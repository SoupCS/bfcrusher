package ru.justnanix.bfcrusher.proxy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ProxyParser {
    private List<Proxy> proxies = new CopyOnWriteArrayList<>();
    private int number = -1;

    public void init() {
        System.out.println("|| (ProxyParser) Парсю прокси...");

        File socks4 = new File("Proxy\\socks4.txt");
        File socks5 = new File("Proxy\\socks5.txt");

        if (socks4.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(socks4))) {
                while (reader.ready()) {
                    try {
                        String line = reader.readLine();
                        proxies.add(new Proxy(ProxyType.SOCKS4, new InetSocketAddress(line.split(":")[0], Integer.parseInt(line.split(":")[1]))));
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        if (socks5.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(socks5))) {
                while (reader.ready()) {
                    try {
                        String line = reader.readLine();
                        proxies.add(new Proxy(ProxyType.SOCKS5, new InetSocketAddress(line.split(":")[0], Integer.parseInt(line.split(":")[1]))));
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        if (socks4.exists() || socks5.exists()) {
            System.out.printf("|| (ProxyParser) Загружено %s прокси с файлов.\n\n", proxies.size());
            return;
        }

        try {
            try {
                Document proxyList = Jsoup.connect("https://api.proxyscrape.com/?request=displayproxies&proxytype=socks4").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS4, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                Document proxyList = Jsoup.connect("https://api.proxyscrape.com/?request=displayproxies&proxytype=socks5").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS5, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                Document proxyList = Jsoup.connect("https://www.proxy-list.download/api/v1/get?type=socks4").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS4, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                Document proxyList = Jsoup.connect("https://www.proxy-list.download/api/v1/get?type=socks5").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS5, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                Document proxyList = Jsoup.connect("https://openproxylist.xyz/socks4.txt").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS4, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                Document proxyList = Jsoup.connect("https://openproxylist.xyz/socks5.txt").get();
                proxies.addAll(Arrays.stream(proxyList.text().split(" ")).distinct().map((proxy) -> new Proxy(ProxyType.SOCKS5, new InetSocketAddress(proxy.split(":")[0], Integer.parseInt(proxy.split(":")[1])))).collect(Collectors.toList()));
            } catch (Throwable ignored) {}

            try {
                for (int k = 64; k < 64 * 25; k += 64) {
                    Document proxyList3 = Jsoup.connect("https://hidemy.name/ru/proxy-list/?type=4&start=" + k + "#list").get();

                    for (int i = 1; i < proxyList3.getElementsByTag("tr").size(); i++) {
                        try {
                            Elements elements = proxyList3.getElementsByTag("tr").get(i).getElementsByTag("td");

                            String host = elements.get(0).text();
                            int port = Integer.parseInt(elements.get(1).text());

                            proxies.add(new Proxy(ProxyType.SOCKS4, new InetSocketAddress(host, port)));
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            try {
                for (int k = 64; k < 64 * 25; k += 64) {
                    Document proxyList3 = Jsoup.connect("https://hidemy.name/ru/proxy-list/?type=5&start=" + k + "#list").get();

                    for (int i = 1; i < proxyList3.getElementsByTag("tr").size(); i++) {
                        try {
                            Elements elements = proxyList3.getElementsByTag("tr").get(i).getElementsByTag("td");

                            String host = elements.get(0).text();
                            int port = Integer.parseInt(elements.get(1).text());

                            proxies.add(new Proxy(ProxyType.SOCKS5, new InetSocketAddress(host, port)));
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            proxies = new CopyOnWriteArrayList<>(new HashSet<>(proxies));
            Collections.shuffle(proxies, new Random(System.nanoTime()));

            System.out.printf("|| (ProxyParser) Загружено и записано в файлы %s прокси.\n\n", proxies.size());

            new File("Proxy").mkdirs();

            socks4.createNewFile();
            socks5.createNewFile();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(socks4))) {
                for (Proxy proxy : proxies) {
                    if (proxy.proxyType != ProxyType.SOCKS4)
                        continue;

                    writer.write(proxy.address.getAddress().getHostAddress() + ":" + proxy.address.getPort() + "\n");
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(socks5))) {
                for (Proxy proxy : proxies) {
                    if (proxy.proxyType != ProxyType.SOCKS5)
                        continue;

                    writer.write(proxy.address.getAddress().getHostAddress() + ":" + proxy.address.getPort() + "\n");
                }
            }
        } catch (Exception ignored) {}
    }

    public Proxy nextProxy() {
        ++number;

        if (number >= proxies.size())
            number = 0;

        return proxies.get(number);
    }

    public record Proxy(ProxyType proxyType, InetSocketAddress address) {

    }

    public enum ProxyType {
        SOCKS4,
        SOCKS5,
        HTTP
    }
}
