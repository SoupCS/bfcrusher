package ru.justnanix.bfcrusher.bot.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.*;
import net.minecraft.util.ITickable;
import net.minecraft.util.LazyLoadBase;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import ru.justnanix.bfcrusher.BFCrusher;
import ru.justnanix.bfcrusher.proxy.ProxyParser;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BNetworkManager extends SimpleChannelInboundHandler<Packet<?>> {
    public static final AttributeKey<EnumConnectionState> PROTOCOL_ATTRIBUTE_KEY = AttributeKey.valueOf("protocol");

    public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyLoadBase<>() {
        protected NioEventLoopGroup func_179280_b() {
            return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Бот №%d").setDaemon(true).build());
        }
    };

    private final Queue<BNetworkManager.InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues.newConcurrentLinkedQueue();
    private final ReentrantReadWriteLock field_181680_j = new ReentrantReadWriteLock();

    /**
     * The active channel
     */
    private Channel channel;

    /**
     * The INetHandler instance responsible for processing received packets
     */
    private INetHandler packetListener;
    private boolean disconnected;

    public BNetworkManager(EnumPacketDirection packetDirection) {

    }

    /**
     * Create a new NetworkManager from the server host and connect it to the server
     */
    public static BNetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort, ProxyParser.Proxy proxy) {
        BNetworkManager netManager = new BNetworkManager(EnumPacketDirection.CLIENTBOUND);

        new Bootstrap().group(CLIENT_NIO_EVENTLOOP.getValue()).handler(new ChannelInitializer<>() {
            protected void initChannel(Channel ctx) {
                try {
                    ctx.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException ignored) {}

                switch (proxy.proxyType()) {
                    case SOCKS4 -> ctx.pipeline().addLast(new Socks4ProxyHandler(proxy.address()));
                    case SOCKS5 -> ctx.pipeline().addLast(new Socks5ProxyHandler(proxy.address()));
                    case HTTP -> ctx.pipeline().addLast(new HttpProxyHandler(proxy.address()));
                }

                ctx.pipeline().addLast("timeout", new ReadTimeoutHandler(30))
                        .addLast("splitter", new NettyVarint21FrameDecoder())
                        .addLast("decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
                        .addLast("prepender", new NettyVarint21FrameEncoder())
                        .addLast("encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
                        .addLast("packet_handler", netManager);
            }
        }).channel(NioSocketChannel.class)
                .connect(address, serverPort).syncUninterruptibly();

        return netManager;
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.channel = ctx.channel();

        if (BFCrusher.blockConnections.get() && getNetHandler() instanceof BLoginHandler) {
            this.closeChannel();
            return;
        }

        try {
            this.setConnectionState(EnumConnectionState.HANDSHAKING);
        } catch (Throwable ignored) {}
    }

    /**
     * Sets the new connection state and registers which packets this channel may send and receive
     */
    public void setConnectionState(EnumConnectionState newState) {
        this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).set(newState);
        this.channel.config().setAutoRead(true);
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.closeChannel();
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        if (!(throwable instanceof CodecException) && !(throwable instanceof SocketException) && !(throwable instanceof TimeoutException) && !(throwable instanceof ClosedChannelException)) {
            throwable.printStackTrace();
        }

        this.closeChannel();
    }

    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) throws Exception {
        if ((BFCrusher.blockConnections.get() || BFCrusher.blockConnectionsBF.get()) && getNetHandler() instanceof BLoginHandler) {
            this.closeChannel();
            return;
        }

        if (this.channel.isOpen()) {
            try {
                ((Packet<INetHandler>) packet).processPacket(this.packetListener);
            } catch (ThreadQuickExitException ignored) {
            }
        }
    }

    public void sendPacket(Packet<?> packetIn) {
        if (BFCrusher.blockConnections.get() && getNetHandler() instanceof BLoginHandler) {
            this.closeChannel();
            return;
        }

        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packetIn, null);
        } else {
            this.field_181680_j.writeLock().lock();

            try {
                this.outboundPacketsQueue.add(new BNetworkManager.InboundHandlerTuplePacketListener(packetIn));
            } finally {
                this.field_181680_j.writeLock().unlock();
            }
        }
    }

    @SafeVarargs
    public final void func_179288_a(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener, GenericFutureListener<? extends Future<? super Void>>... listeners) {
        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packet, ArrayUtils.add(listeners, 0, listener));
        } else {
            this.field_181680_j.writeLock().lock();

            try {
                this.outboundPacketsQueue.add(new BNetworkManager.InboundHandlerTuplePacketListener(packet, ArrayUtils.add(listeners, 0, listener)));
            } finally {
                this.field_181680_j.writeLock().unlock();
            }
        }
    }

    /**
     * Will commit the packet to the channel. If the current thread 'owns' the channel it will write and flush the
     * packet, otherwise it will add a task for the channel eventloop thread to do that.
     */
    private void dispatchPacket(final Packet<?> inPacket, @Nullable final GenericFutureListener<? extends Future<? super Void>>[] futureListeners) {
        final EnumConnectionState enumconnectionstate = EnumConnectionState.getFromPacket(inPacket);
        final EnumConnectionState enumconnectionstate1 = this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).get();

        if (enumconnectionstate1 != enumconnectionstate) {
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop()) {
            if (enumconnectionstate != enumconnectionstate1) {
                this.setConnectionState(enumconnectionstate);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);

            if (futureListeners != null) {
                channelfuture.addListeners(futureListeners);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(() -> {
                if (enumconnectionstate != enumconnectionstate1) {
                    BNetworkManager.this.setConnectionState(enumconnectionstate);
                }

                ChannelFuture channelfuture1 = BNetworkManager.this.channel.writeAndFlush(inPacket);

                if (futureListeners != null) {
                    channelfuture1.addListeners(futureListeners);
                }

                channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    /**
     * Will iterate through the outboundPacketQueue and dispatch all Packets
     */
    private void flushOutboundQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            this.field_181680_j.readLock().lock();

            try {
                while (!this.outboundPacketsQueue.isEmpty()) {
                    BNetworkManager.InboundHandlerTuplePacketListener networkmanager$inboundhandlertuplepacketlistener = this.outboundPacketsQueue.poll();
                    this.dispatchPacket(networkmanager$inboundhandlertuplepacketlistener.packet, networkmanager$inboundhandlertuplepacketlistener.listener);
                }
            } finally {
                this.field_181680_j.readLock().unlock();
            }
        }
    }

    /**
     * Checks timeouts and processes all packets received
     */
    public void tick() {
        this.flushOutboundQueue();

        if (this.packetListener instanceof ITickable) {
            ((ITickable) this.packetListener).tick();
        }

        if (this.channel != null) {
            this.channel.flush();
        }
    }

    /**
     * Closes the channel, the parameter can be used for an exit message (not certain how it gets sent)
     */
    public void closeChannel() {
        if (this.channel.isOpen()) {
            try {
                try {
                    this.channel.close().sync();
                } catch (Exception e) {
                    this.channel.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Returns true if this NetworkManager has an active channel, false otherwise
     */
    public boolean isChannelOpen() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean hasNoChannel() {
        return this.channel == null;
    }

    /**
     * Gets the current handler for processing packets
     */
    public INetHandler getNetHandler() {
        return this.packetListener;
    }

    /**
     * Sets the NetHandler for this NetworkManager, no checks are made if this handler is suitable for the particular
     * connection state (protocol)
     */
    public void setNetHandler(INetHandler handler) {
        Validate.notNull(handler, "packetListener");
        this.packetListener = handler;
    }

    /**
     * Switches the channel to manual reading modus
     */
    public void disableAutoRead() {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionThreshold(int threshold) {
        if (threshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
                ((NettyCompressionDecoder) this.channel.pipeline().get("decompress")).setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(threshold));
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
                ((NettyCompressionEncoder) this.channel.pipeline().get("compress")).setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (!this.disconnected) {
                this.disconnected = true;
                this.getNetHandler().onDisconnect(null);
            }
        }
    }

    static class InboundHandlerTuplePacketListener {
        private final Packet<?> packet;
        private final GenericFutureListener<? extends Future<? super Void>>[] listener;

        @SafeVarargs
        public InboundHandlerTuplePacketListener(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>... listener) {
            this.packet = packet;
            this.listener = listener;
        }
    }
}
