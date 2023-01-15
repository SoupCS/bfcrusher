package ru.justnanix.bfcrusher.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.CPacketLoginStart;
import ru.justnanix.bfcrusher.BFCrusher;
import ru.justnanix.bfcrusher.bot.network.BLoginHandler;
import ru.justnanix.bfcrusher.bot.network.BNetworkManager;
import ru.justnanix.bfcrusher.proxy.ProxyParser;

import java.net.InetAddress;
import java.util.UUID;

public class BotHandler extends Thread {
    private final String host;
    private final int port;

    public boolean joined = false;

    public BotHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), BFCrusher.randomNick());
        ProxyParser.Proxy proxy = BFCrusher.proxyParser.nextProxy();

        try {
            BNetworkManager netManagerLogin = BNetworkManager.createNetworkManagerAndConnect(InetAddress.getByName(host), port, proxy);

            netManagerLogin.setNetHandler(new BLoginHandler(netManagerLogin, this));
            netManagerLogin.sendPacket(new C00Handshake(host, port, EnumConnectionState.LOGIN));
            netManagerLogin.sendPacket(new CPacketLoginStart(gameProfile));
        } catch (Exception ignored) {}
    }
}
