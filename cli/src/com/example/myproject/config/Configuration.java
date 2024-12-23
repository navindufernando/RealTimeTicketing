package com.example.myproject.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;

public class Configuration implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final String eventName;
    private final int totalTickets;
    private final int ticketReleaseRate;
    private final int customerRetrievalRate;
    private final int maxTicketCapacity;

    public Configuration(String eventName, int totalTickets, int ticketReleaseRate, int customerRetrievalRate, int maxTicketCapacity) {
        validateConfiguration(totalTickets, ticketReleaseRate, customerRetrievalRate, maxTicketCapacity);
        this.eventName = eventName;
        this.totalTickets = totalTickets;
        this.ticketReleaseRate = ticketReleaseRate;
        this.customerRetrievalRate = customerRetrievalRate;
        this.maxTicketCapacity = maxTicketCapacity;
    }

    private void validateConfiguration(int totalTickets, int ticketReleaseRate, int customerRetrievalRate, int maxTicketCapacity) {
        if (totalTickets <= 0) {
            throw new IllegalArgumentException("Total tickets must be positive");
        }
        if (ticketReleaseRate <= 0) {
            throw new IllegalArgumentException("Ticket release rate must be positive");
        }
        if (customerRetrievalRate <= 0) {
            throw new IllegalArgumentException("Customer retrieval rate must be positive");
        }
        if (maxTicketCapacity <= 0 || maxTicketCapacity > totalTickets) {
            throw new IllegalArgumentException("Max ticket capacity must be positive and cannot exceed total tickets");
        }
    }

    // Getters for configuration values
    public String getEventName() { return eventName; }
    public int getTotalTickets() { return totalTickets; }
    public int getTicketReleaseRate() { return ticketReleaseRate; }
    public int getCustomerRetrievalRate() { return customerRetrievalRate; }
    public int getMaxTicketCapacity() { return maxTicketCapacity; }

    // Get file paths based on event name
    private String getJsonFilePath() {
        return STR."\{eventName}.json";
    }

    // Static method to get file paths for a given event name
    public static String getConfigFilePath(String eventName) {
        return STR."\{eventName}.json";
    }

    // JSON Serialization Methods
    public void saveToJsonFile() throws IOException {
        try (Writer writer = new FileWriter(getJsonFilePath())) {
            GSON.toJson(this, writer);
        }
    }

    public static Configuration loadFromJsonFile(String eventName) throws IOException {
        File configFile = new File(getConfigFilePath(eventName));
        if (!configFile.exists()) {
            return null;
        }

        try (Reader reader = new FileReader(configFile)) {
            return GSON.fromJson(reader, Configuration.class);
        }
    }

    @Override
    public String toString() {
        return String.format("Configuration{eventName='%s', totalTickets=%d, releaseRate=%d, retrievalRate=%d, maxCapacity=%d}",
                eventName, totalTickets, ticketReleaseRate, customerRetrievalRate, maxTicketCapacity);
    }
}