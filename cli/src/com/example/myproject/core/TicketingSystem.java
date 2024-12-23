package com.example.myproject.core;

import com.example.myproject.actor.TicketProducer;
import com.example.myproject.actor.TicketConsumer;
import com.example.myproject.model.TicketPool;
import com.example.myproject.config.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.Scanner;
import java.util.Properties;
import java.io.*;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TicketingSystem {
    private static final Logger LOGGER = Logger.getLogger(TicketingSystem.class.getName());
    private static ExecutorService executorService;
    private static ScheduledExecutorService monitorService;
    private static final String CONFIG_FILE = "last_config.properties";
    private static String eventName;
    private static final Random random = new Random();
    private static FileHandler currentFileHandler;

    static {
        setupInitialLogger();
    }

    // Initialize executor services
    private static void initializeExecutors() {
        executorService = new ThreadPoolExecutor(20, 50, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        monitorService = Executors.newSingleThreadScheduledExecutor();
    }

    // Initial logger setup before event name is known
    private static void setupInitialLogger() {
        try {
            Logger rootLogger = Logger.getLogger("");

            // Remove existing handlers
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            // Set initial logging level to WARNING to reduce startup messages
            rootLogger.setLevel(Level.WARNING);

            // Create a custom formatter that only shows the message without timestamp
            Formatter minimalFormatter = new Formatter() {
                @Override
                public String format(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        return record.getMessage() + "\n";
                    }
                    return "";
                }
            };

            // Add console handler with minimal formatting
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(minimalFormatter);
            consoleHandler.setLevel(Level.WARNING);
            rootLogger.addHandler(consoleHandler);

        } catch (Exception e) {
            System.err.println("Failed to setup initial logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //setupLogger method to ensure consistent logging
    private static void setupLogger() {
        try {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            if (eventName == null || eventName.isEmpty()) {
                eventName = "default_event";
            }

            String logFileName = eventName + "_ticketing.log";

            if (currentFileHandler != null) {
                currentFileHandler.close();
            }

            // Create new FileHandler with append set to true
            currentFileHandler = new FileHandler(logFileName, true) {
                @Override
                public synchronized void publish(LogRecord record) {
                    // Force immediate write for each record
                    super.publish(record);
                    flush();
                }
            };

            // Set logging level to INFO for file handler
            currentFileHandler.setLevel(Level.INFO);

            // Create detailed formatter for file logging
            Formatter detailedFormatter = new Formatter() {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");

                @Override
                public String format(LogRecord record) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%s %s %s%n",
                            dateFormat.format(new Date(record.getMillis())).toLowerCase(),
                            record.getSourceClassName(),
                            record.getSourceMethodName()));
                    sb.append(String.format("INFO: %s%n", record.getMessage()));
                    return sb.toString();
                }
            };

            currentFileHandler.setFormatter(detailedFormatter);
            rootLogger.addHandler(currentFileHandler);

            // Set root logger to INFO level to capture all relevant messages
            rootLogger.setLevel(Level.INFO);

            // Add console handler for warnings and errors
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        return STR."\{record.getMessage()}\n";
                    }
                    return "";
                }
            });
            consoleHandler.setLevel(Level.WARNING);
            rootLogger.addHandler(consoleHandler);

            // Log the start of a new logging session
            LOGGER.info(STR."Logging initiated for event: \{eventName}");

        } catch (Exception e) {
            System.err.println("Failed to setup logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Configuration loadLastConfiguration() {
        Configuration config;
        try {
            // Attempt to load from last saved event name
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
                eventName = props.getProperty("eventName", "");
            } catch (IOException e) {
                System.out.println("No previous configuration properties found.");
                return null;
            }

            if (!eventName.isEmpty()) {
                config = Configuration.loadFromJsonFile(eventName);
                if (config != null) {
                    setupLogger();
                    return config;
                }
            }
        } catch (IOException e) {
            System.out.println("No previous configuration found. Please start a new event.");
        }
        return null;
    }

    private static void saveConfiguration(Configuration config) {
        try {
            config.saveToJsonFile();
        } catch (IOException e) {
            System.err.println("Failed to save JSON configuration: " + e.getMessage());
        }

        Properties props = new Properties();
        props.setProperty("eventName", eventName);
        props.setProperty("totalTickets", String.valueOf(config.getTotalTickets()));
        props.setProperty("ticketReleaseRate", String.valueOf(config.getTicketReleaseRate()));
        props.setProperty("customerRetrievalRate", String.valueOf(config.getCustomerRetrievalRate()));
        props.setProperty("maxTicketCapacity", String.valueOf(config.getMaxTicketCapacity()));

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Ticketing System Configuration");
        } catch (IOException e) {
            System.err.println("Failed to save properties configuration: " + e.getMessage());
        }
    }

    private static Configuration getUserConfiguration(Scanner scanner) {
        System.out.print("Enter event name: ");
        eventName = scanner.nextLine();

        // logger with new event name
        setupLogger();

        int totalTickets = getPositiveInteger(scanner, "Enter total number of tickets: ");
        int ticketReleaseRate = getPositiveInteger(scanner, "Enter ticket release rate (ms): ");
        int customerRetrievalRate = getPositiveInteger(scanner, "Enter customer retrieval rate (ms): ");
        int maxTicketCapacity;

        do {
            maxTicketCapacity = getPositiveInteger(scanner,
                    "Enter maximum ticket capacity (cannot exceed total tickets): ");
            if (maxTicketCapacity > totalTickets) {
                System.out.println("Error: Maximum capacity cannot exceed total tickets (" + totalTickets + ")");
            }
        } while (maxTicketCapacity > totalTickets);

        Configuration config = new Configuration(eventName, totalTickets, ticketReleaseRate,
                customerRetrievalRate, maxTicketCapacity);
        saveConfiguration(config);
        return config;
    }

    private static void resetLogConfiguration() {
        try {
            if (currentFileHandler != null) {
                currentFileHandler.close();
            }

            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            setupInitialLogger();

        } catch (Exception e) {
            System.err.println("Error resetting log configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void resetConfigurations() {
        try {
            // Clear all log files
            File[] logFiles = new File(".").listFiles((_, name) ->
                    name.endsWith("_ticketing.log"));
            if (logFiles != null) {
                for (File logFile : logFiles) {
                    if (logFile.delete()) {
                        System.out.println("Deleted log file: " + logFile.getName());
                    }
                }
            }

            // Clear all JSON configuration files
            File[] jsonFiles = new File(".").listFiles((_, name) ->
                    name.endsWith(".json") && !name.equals("last_config.properties"));
            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    if (jsonFile.delete()) {
                        System.out.println("Deleted JSON config file: " + jsonFile.getName());
                    }
                }
            }

            // Delete properties configuration file
            File propsConfig = new File(CONFIG_FILE);
            if (propsConfig.exists() && propsConfig.delete()) {
                System.out.println("Deleted properties configuration file: " + CONFIG_FILE);
            }

            // Reset log configuration
            resetLogConfiguration();

            System.out.println("\nAll configurations, logs, and related files have been reset successfully.");
        } catch (Exception e) {
            System.err.println("Error resetting configurations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getPositiveInteger(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                int value = Integer.parseInt(scanner.nextLine());
                if (value > 0) {
                    return value;
                }
                System.out.println("Error: Please enter a positive number.");
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid number.");
            }
        }
    }

    private static List<TicketProducer> initializeProducers(TicketPool ticketPool, Configuration config) {
        int producerCount = random.nextInt(5) + 3; // 3 to 7 producers
        List<TicketProducer> producers = new ArrayList<>();
        for (int i = 0; i < producerCount; i++) {
            producers.add(new TicketProducer("Producer-" + (i + 1), ticketPool, config.getTicketReleaseRate()));
        }
        return producers;
    }

    private static List<TicketConsumer> initializeConsumers(TicketPool ticketPool, Configuration config) {
        int consumerCount = random.nextInt(10) + 5; // 5 to 14 consumers
        List<TicketConsumer> consumers = new ArrayList<>();

        // Initialize VIP consumers
        int vipConsumerCount = consumerCount / 4; // 25% VIP consumers
        for (int i = 0; i < vipConsumerCount; i++) {
            consumers.add(new TicketConsumer.VIPTicketConsumer(
                    "Priority-Consumer-" + (i + 1),
                    ticketPool,
                    config.getCustomerRetrievalRate(),
                    config.getTotalTickets() / 8
            ));
        }

        // Initialize regular consumers
        for (int i = vipConsumerCount; i < consumerCount; i++) {
            consumers.add(new TicketConsumer(
                    "Consumer-" + (i + 1),
                    ticketPool,
                    config.getCustomerRetrievalRate(),
                    false
            ));
        }
        return consumers;
    }

    private static ScheduledFuture<?> startMonitoring(TicketPool ticketPool) {
        LOGGER.info("Starting ticket pool monitoring");
        return monitorService.scheduleAtFixedRate(() -> {
            try {
                LOGGER.info(String.format("Pool Status - Available: %d, Produced: %d, Consumed: %d",
                        ticketPool.getAvailableTickets(),
                        ticketPool.getTicketsProduced(),
                        ticketPool.getTicketsConsumed()));
            } catch (Exception e) {
                LOGGER.severe("Error in monitoring task: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static void startActors(List<TicketProducer> producers, List<TicketConsumer> consumers) {
        for (TicketProducer producer : producers) {
            executorService.submit(producer);
        }
        for (TicketConsumer consumer : consumers) {
            executorService.submit(consumer);
        }
    }

    private static void waitForCompletion(TicketPool ticketPool, ScheduledFuture<?> monitorTask) {
        try {
            while (!ticketPool.isAllTicketsConsumed()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("System interrupted while waiting for completion");
        }
    }

    private static void cleanup(List<TicketProducer> producers, List<TicketConsumer> consumers,
                                ScheduledFuture<?> monitorTask) {
        // Stop all producers and consumers
        for (TicketProducer producer : producers) {
            producer.stop();
        }
        for (TicketConsumer consumer : consumers) {
            consumer.stop();
        }

        // Cancel the monitoring task
        if (monitorTask != null) {
            monitorTask.cancel(true);
        }

        // Shutdown executor services
        shutdownExecutors();
    }

    private static void shutdownExecutors() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (monitorService != null) {
            monitorService.shutdown();
            try {
                if (!monitorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    monitorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void startSystem(Configuration config) {
        LOGGER.info("Starting ticketing system for event: " + config.getEventName());

        // Initialize new executor services for each start
        initializeExecutors();
        LOGGER.info("Executor services initialized");

        TicketPool ticketPool = new TicketPool(eventName, config.getMaxTicketCapacity(), config.getTotalTickets());
        LOGGER.info("Ticket pool created with capacity: " + config.getMaxTicketCapacity());

        // Initialize producers and consumers
        List<TicketProducer> producers = initializeProducers(ticketPool, config);
        List<TicketConsumer> consumers = initializeConsumers(ticketPool, config);
        LOGGER.info("Initialized " + producers.size() + " producers and " + consumers.size() + " consumers");

        // Start monitoring
        ScheduledFuture<?> monitorTask = startMonitoring(ticketPool);

        try {
            // Start producers and consumers
            startActors(producers, consumers);
            LOGGER.info("Started all producers and consumers");

            // Wait for completion
            waitForCompletion(ticketPool, monitorTask);

            // Print final statistics
            printFinalStatistics(ticketPool, producers, consumers);
        } catch (Exception e) {
            LOGGER.severe("Error during system execution: " + e.getMessage());
        } finally {
            // Cleanup
            cleanup(producers, consumers, monitorTask);
            LOGGER.info("System cleanup completed");
        }
    }

    // Modify the printFinalStatistics method:
    private static void printFinalStatistics(TicketPool ticketPool,
                                             List<TicketProducer> producers,
                                             List<TicketConsumer> consumers) {
        System.out.println("\nFinal Statistics:");
        System.out.println("Event: " + eventName);
        System.out.println("Total tickets: " + ticketPool.getTotalTickets());
        System.out.println("Tickets produced: " + ticketPool.getTicketsProduced());
        System.out.println("Tickets consumed: " + ticketPool.getTicketsConsumed());
        System.out.println("VIP tickets consumed: " + ticketPool.getVIPTicketsConsumed());

        System.out.println("\nProducer Statistics:");
        for (TicketProducer producer : producers) {
            System.out.println(producer.getProducerId() +
                    " produced: " + producer.getTicketsProduced() + " tickets");
        }

        System.out.println("\nConsumer Statistics:");
        for (TicketConsumer consumer : consumers) {
            if (consumer instanceof TicketConsumer.VIPTicketConsumer) {
                TicketConsumer.VIPTicketConsumer vipConsumer = (TicketConsumer.VIPTicketConsumer) consumer;
                System.out.println("Priority " + vipConsumer.getConsumerId() +
                        " consumed: " + vipConsumer.getTicketsConsumed() + " tickets" +
                        " (VIP Ticket Limit: " + vipConsumer.getMaxTickets() + ")");
            } else {
                String consumerType = consumer.isPriority() ? "Priority " : "";
                System.out.println(consumerType + consumer.getConsumerId() +
                        " consumed: " + consumer.getTicketsConsumed() + " tickets");
            }
        }

        System.out.println("\nEnter 'start' to return to menu or 'stop' to exit system:");
    }

    // Update the main method menu to include reset option:
    public static void main(String[] args) {
        System.out.println("\nWelcome to the Real-Time Event Ticketing System");
        Scanner scanner = new Scanner(System.in);
        Configuration config;
        boolean running = true;

        while (running) {
            System.out.println("\n************************************************");
            System.out.println("*                MENU OPTIONS                  *");
            System.out.println("************************************************");
            System.out.println("\n\t1. Start New Event");
            System.out.println("\t2. Load Previous Configuration");
            System.out.println("\t3. Reset All Configurations");
            System.out.println("\t4. Exit");
            System.out.println("\n************************************************");
            System.out.print("\nSelect an option: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                switch (choice) {
                    case 1:
                        config = getUserConfiguration(scanner);
                        break;
                    case 2:
                        config = loadLastConfiguration();
                        if (config == null) {
                            System.out.println("No previous configuration found. Please start a new event.");
                            continue;
                        }
                        System.out.println("Loaded configuration for event: " + eventName);
                        break;
                    case 3:
                        System.out.println("Are you sure you want to reset all configurations? (y/n)");
                        String confirm = scanner.nextLine().trim().toLowerCase();
                        if (confirm.equals("y")) {
                            resetConfigurations();
                        }
                        continue;
                    case 4:
                        System.out.println("Exiting system...");
                        scanner.close();
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.");
                        continue;
                }

                if (config != null) {
                    System.out.println("\nConfiguration set for event: " + eventName);
                    System.out.println("Type 'start' to begin or 'back' to return to menu:");
                    String input = scanner.nextLine().trim().toLowerCase();
                    if (input.equals("start")) {
                        startSystem(config);

                        String nextAction = scanner.nextLine().trim().toLowerCase();
                        if (nextAction.equals("stop")) {
                            System.out.println("Closing system...");
                            running = false;
                            scanner.close();
                            return;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            } catch (Exception e) {
                LOGGER.severe("Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}