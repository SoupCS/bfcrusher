package ru.justnanix.bfcrusher.bot;

import io.netty.util.internal.ConcurrentSet;
import ru.justnanix.bfcrusher.bot.entity.BPlayer;
import ru.justnanix.bfcrusher.bot.entity.BPlayerController;
import ru.justnanix.bfcrusher.bot.network.BNetworkManager;
import ru.justnanix.bfcrusher.bot.network.BPlayHandler;
import ru.justnanix.bfcrusher.bot.world.BWorldClient;

import java.util.Set;

public class Bot {
    public static Set<Bot> bots = new ConcurrentSet<>();

    private final BNetworkManager netManager;
    private final BPlayHandler connection;

    private final BPlayerController controller;
    private final BPlayer bot;

    private final BWorldClient world;

    public int joinCounter = 0;

    public Bot(BNetworkManager netManager, BPlayHandler connection, BPlayerController controller, BPlayer bot, BWorldClient world) {
        this.netManager = netManager;
        this.connection = connection;
        this.controller = controller;
        this.bot = bot;
        this.world = world;
    }

    public BPlayerController getController() {
        return controller;
    }

    public BNetworkManager getNetManager() {
        return netManager;
    }

    public BPlayHandler getConnection() {
        return connection;
    }

    public BWorldClient getWorld() {
        return world;
    }

    public BPlayer getBot() {
        return bot;
    }
}
