package ru.justnanix.bfcrusher.nicks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NicksParser {
    private final List<String> nicks = new CopyOnWriteArrayList<>();
    private int number = -1;

    public void init() {
        System.out.println("|| (NicksParser) Парсю ники...");

        File nicksFile = new File("Nicks\\nicks.txt");

        if (nicksFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(nicksFile))) {
                while (reader.ready()) {
                    try {
                        String line = reader.readLine();
                        this.nicks.add(line);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        } else {
            System.out.println("|| (NicksParser) Файл с никами не найден!");
            System.exit(0);
        }

        System.out.printf("|| (NicksParser) Загружено %s ников.\n\n", nicks.size());
    }

    public String nextNick() {
        ++number;

        if (number >= nicks.size())
            number = 0;

        return nicks.get(number);
    }
}
