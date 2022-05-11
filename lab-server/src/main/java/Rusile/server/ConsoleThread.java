package Rusile.server;


import Rusile.common.people.Person;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Scanner;

public class ConsoleThread extends Thread {
    private static final Scanner scanner = ServerConfig.scanner;
    private volatile boolean running = true;

    @Override
    public void run() {
        ServerConfig.logger.info("Console thread is running");
        try {
        while (running) {
            String line = scanner.nextLine();
            if ("save".equalsIgnoreCase(line)) {
                ServerConfig.fileManager.writeCollection((ArrayDeque<Person>) ServerConfig.collectionManager.getCollection());
                ServerConfig.collectionManager.setLastSaveTime(LocalDateTime.now());
                ServerConfig.logger.info("Collection saved");
            }
            if ("exit".equalsIgnoreCase(line)) {
                ServerConfig.logger.info("Server closed");
                System.exit(0);
            }
        }
      } catch (NoSuchElementException e) {
            ServerConfig.logger.error("Invalid input!");
            System.exit(0);
        }
    }

    public void shutdown() {
        this.running = false;
    }
}
