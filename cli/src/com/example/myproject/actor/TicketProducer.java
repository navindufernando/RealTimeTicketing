package com.example.myproject.actor;

import com.example.myproject.model.TicketPool;
import java.util.logging.Logger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public class TicketProducer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(TicketProducer.class.getName());
    private final String producerId;
    private final TicketPool ticketPool;
    private final int maxReleaseRate;
    private final Random random = new Random();
    private volatile boolean isRunning = true;
    private int ticketsProduced = 0;
    private final Lock producerLock = new ReentrantLock();

    public TicketProducer(String producerId, TicketPool ticketPool, int maxReleaseRate) {
        this.producerId = producerId;
        this.ticketPool = ticketPool;
        this.maxReleaseRate = maxReleaseRate;
    }

    @Override
    public void run() {
        try {
            while (isRunning && !ticketPool.isAllTicketsProduced()) {
                producerLock.lock();
                try {
                    if (ticketPool.produceTicket(this)) {
                        ticketsProduced++;
                        LOGGER.info(STR."\{producerId} produced a ticket. Total produced: \{ticketsProduced}");
                    }
                } catch (TicketPool.TicketException e) {
                    LOGGER.warning(STR."\{producerId} encountered an error: \{e.getMessage()}");
                } finally {
                    producerLock.unlock();
                }

                // Randomize sleep time between 50% and 150% of original release rate
                int actualReleaseRate = random.nextInt(maxReleaseRate / 2) + (maxReleaseRate / 2);
                Thread.sleep(actualReleaseRate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning(STR."\{producerId} interrupted: \{e.getMessage()}");
        }
    }
    public String getProducerId() {

        return producerId;
    }

    public int getTicketsProduced() {
        return ticketsProduced;
    }

    public void stop() {
        isRunning = false;
    }
}