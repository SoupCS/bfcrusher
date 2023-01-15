package ru.justnanix.bfcrusher.bot.network;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.login.INetHandlerLoginClient;
import net.minecraft.network.login.server.SPacketDisconnect;
import net.minecraft.network.login.server.SPacketEnableCompression;
import net.minecraft.network.login.server.SPacketEncryptionRequest;
import net.minecraft.network.login.server.SPacketLoginSuccess;
import net.minecraft.util.text.ITextComponent;
import ru.justnanix.bfcrusher.BFCrusher;
import ru.justnanix.bfcrusher.bot.BotHandler;

public record BLoginHandler(BNetworkManager networkManager, BotHandler handler) implements INetHandlerLoginClient {
    public void handleEncryptionRequest(SPacketEncryptionRequest packetIn) {

    }

    public void handleLoginSuccess(SPacketLoginSuccess packetIn) {
        if (BFCrusher.blockConnections.get() || BFCrusher.blockConnectionsBF.get()) {
            Thread.currentThread().interrupt();
            Thread.currentThread().stop();

            return;
        }

        this.networkManager.setConnectionState(EnumConnectionState.PLAY);
        this.networkManager.setNetHandler(new BPlayHandler(this.networkManager, packetIn.getProfile()));

        handler.joined = true;
        BFCrusher.botsConnected.getAndIncrement();

        System.out.printf("|| (%s) Подключился.\n", packetIn.getProfile().getName());
    }

    public void onDisconnect(ITextComponent reason) {

    }

    public void handleDisconnect(SPacketDisconnect packetIn) {
        this.networkManager.closeChannel();
    }

    public void handleEnableCompression(SPacketEnableCompression packetIn) {
        this.networkManager.setCompressionThreshold(packetIn.getCompressionThreshold());
    }
}
