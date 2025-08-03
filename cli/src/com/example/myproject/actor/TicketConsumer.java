package com.example.myproject.actor;

import com.example.myproject.model.TicketPool;
import java.util.logging.Logger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public class TicketConsumer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(TicketConsumer.class.getName());

    protected final String consumerId;
    protected final TicketPool ticketPool;
    protected final int maxConsumptionRate;
    protected volatile boolean isRunning = true;
    protected int ticketsConsumed = 0;
    protected final Lock consumerLock;
    private final boolean isPriority;
    protected final Random random = new Random();

    public TicketConsumer(String consumerId, TicketPool ticketPool, int maxConsumptionRate, boolean isPriority) {
        this.consumerId = consumerId;
        this.ticketPool = ticketPool;
        this.maxConsumptionRate = maxConsumptionRate;
        this.isPriority = isPriority;
        this.consumerLock = new ReentrantLock();
    }

    @Override
    public void run() {
        try {
            while (isRunning && !ticketPool.isAllTicketsConsumed()) {
                consumerLock.lock();
                try {
                    boolean ticketPurchased = ticketPool.consumeTicket(this);
                    if (ticketPurchased) {
                        ticketsConsumed++;
                        LOGGER.info(String.format("%s bought Ticket ID: %d", consumerId, ticketsConsumed));
                    }
                } catch (TicketPool.TicketException e) {
                    LOGGER.warning(consumerId + " encountered an error: " + e.getMessage());
                } finally {
                    consumerLock.unlock();
                }

                // Randomize sleep time between 50% and 150% of original consumption rate
                int actualConsumptionRate = random.nextInt(maxConsumptionRate / 2) + (maxConsumptionRate / 2);
                Thread.sleep(actualConsumptionRate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning(consumerId + " interrupted: " + e.getMessage());
        }
    }

    public void stop() {
        isRunning = false;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public int getTicketsConsumed() {
        return ticketsConsumed;
    }

    public boolean isPriority() {
        return isPriority;
    }

    public static class VIPTicketConsumer extends TicketConsumer {
        private static final Logger LOGGER = Logger.getLogger(VIPTicketConsumer.class.getName());
        private final int maxTickets;

        public VIPTicketConsumer(String consumerId, TicketPool ticketPool, int consumptionRate, int maxTickets) {
            super(consumerId, ticketPool, consumptionRate, true);
            this.maxTickets = maxTickets;
        }

        @Override
        public void run() {
            try {
                while (isRunning && !ticketPool.isAllTicketsConsumed() && ticketsConsumed < maxTickets) {
                    consumerLock.lock();
                    try {
                        boolean ticketPurchased = ticketPool.consumeVIPTicket(this);
                        if (ticketPurchased) {
                            ticketsConsumed++;
                            LOGGER.info(String.format("Priority %s bought Ticket ID: %d, Remaining VIP tickets: %d", consumerId, ticketsConsumed, (maxTickets - ticketsConsumed)));
                        }
                    } catch (TicketPool.TicketException e) {
                        LOGGER.warning("VIP " + consumerId + " encountered an error: " + e.getMessage());
                    } finally {
                        consumerLock.unlock();
                    }

                    // Randomize sleep time between 50% and 150% of original consumption rate
                    int actualConsumptionRate = random.nextInt(maxConsumptionRate / 2) + (maxConsumptionRate / 2);
                    Thread.sleep(actualConsumptionRate);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("VIP " + consumerId + " interrupted: " + e.getMessage());
            }
        }

        public int getMaxTickets() {
            return maxTickets;
        }
    }
}