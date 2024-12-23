package com.example.myproject.model;

import com.example.myproject.actor.TicketConsumer;
import com.example.myproject.actor.TicketProducer;

import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class TicketPool {
    // Nested Ticket Exception class
    public static class TicketException extends RuntimeException {
        public TicketException(String message) {
            super(message);
        }

        public TicketException(String message, Throwable cause) {
            super(message, cause);
        }

        public TicketException(Throwable cause) {
            super(cause);
        }
    }

    // Nested Ticket class
    public static class Ticket {
        private final String ticketId;
        private final String eventName;

        public Ticket(String ticketId, String eventName) {
            if (ticketId == null || ticketId.trim().isEmpty()) {
                throw new IllegalArgumentException("Ticket ID cannot be null or empty");
            }
            if (eventName == null || eventName.trim().isEmpty()) {
                throw new IllegalArgumentException("Event name cannot be null or empty");
            }
            this.ticketId = ticketId;
            this.eventName = eventName;
        }

        public String getTicketId() {
            return ticketId;
        }

        public String getEventName() {
            return eventName;
        }

        @Override
        public String toString() {
            return String.format("Ticket{id='%s', event='%s'}", ticketId, eventName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ticket ticket = (Ticket) o;
            return ticketId.equals(ticket.ticketId) && eventName.equals(ticket.eventName);
        }

        @Override
        public int hashCode() {
            int result = ticketId.hashCode();
            result = 31 * result + eventName.hashCode();
            return result;
        }
    }

    // Existing TicketPool code remains the same
    private static final Logger LOGGER = Logger.getLogger(TicketPool.class.getName());
    private final String eventName;
    private final int maxCapacity;
    private final int totalTickets;
    private final int maxVIPTickets;
    private final BlockingQueue<Ticket> ticketQueue;
    private final AtomicInteger ticketsProduced = new AtomicInteger(0);
    private final AtomicInteger ticketsConsumed = new AtomicInteger(0);
    private final AtomicInteger vipTicketsConsumed = new AtomicInteger(0);
    private final PriorityBlockingQueue<TicketConsumer> consumerQueue;
    private final ReentrantLock poolLock = new ReentrantLock();
    private final Condition notFull = poolLock.newCondition();
    private final Condition notEmpty = poolLock.newCondition();

    static {
        setupLogger();
    }

    private static void setupLogger() {
        try {
            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            Formatter customFormatter = new Formatter() {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");

                @Override
                public String format(LogRecord record) {
                    return String.format("\u001B[31m%s %s %s\nINFO: %s\n",
                            dateFormat.format(new Date(record.getMillis())),
                            record.getSourceClassName().substring(record.getSourceClassName().lastIndexOf('.') + 1),
                            record.getSourceMethodName(),
                            record.getMessage());
                }
            };

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(customFormatter);
            LOGGER.addHandler(consoleHandler);
            LOGGER.setUseParentHandlers(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TicketPool(String eventName, int maxCapacity, int totalTickets) {
        validateParameters(maxCapacity, totalTickets);
        this.eventName = eventName;
        this.maxCapacity = maxCapacity;
        this.totalTickets = totalTickets;
        this.maxVIPTickets = totalTickets / 4;
        this.ticketQueue = new ArrayBlockingQueue<>(maxCapacity);
        this.consumerQueue = new PriorityBlockingQueue<>(11, createConsumerComparator());
    }

    private Comparator<TicketConsumer> createConsumerComparator() {
        return Comparator.comparing(TicketConsumer::isPriority).reversed()
                .thenComparing(TicketConsumer::getConsumerId);
    }

    public boolean produceTicket(TicketProducer producer) {
        poolLock.lock();
        try {
            if (isAllTicketsProduced()) {
                return false;
            }

            while (ticketQueue.size() >= maxCapacity) {
                if (!notFull.await(100, TimeUnit.MILLISECONDS)) {
                    throw new TicketException("Timeout waiting for space in ticket pool");
                }
            }

            Ticket ticket = createTicket(producer);
            if (ticketQueue.offer(ticket)) {
                ticketsProduced.incrementAndGet();
                LOGGER.logp(Level.INFO, "TicketPool", "addTicket",
                        String.format("Vendor %s added Ticket ID: %d pool size: %d",
                                producer.getProducerId().replace("Producer-", ""),
                                ticketsProduced.get(),
                                ticketQueue.size()));
                notEmpty.signalAll();
                return true;
            }
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TicketException("Producer interrupted while waiting", e);
        } finally {
            poolLock.unlock();
        }
    }

    public boolean consumeTicket(TicketConsumer consumer) {
        poolLock.lock();
        try {
            if (!consumerQueue.contains(consumer)) {
                consumerQueue.offer(consumer);
            }

            while (ticketQueue.isEmpty() || consumerQueue.peek() != consumer) {
                if (isAllTicketsConsumed()) {
                    consumerQueue.remove(consumer);
                    return false;
                }
                if (!notEmpty.await(100, TimeUnit.MILLISECONDS)) {
                    throw new TicketException("Timeout waiting for tickets");
                }
            }

            Ticket ticket = ticketQueue.poll();
            if (ticket != null) {
                consumerQueue.poll();
                ticketsConsumed.incrementAndGet();
                LOGGER.logp(Level.INFO, "TicketPool", "buyTicket",
                        String.format("Customer %s bought Ticket ID: %d pool size: %d",
                                consumer.getConsumerId().replace("Consumer-", ""),
                                Integer.parseInt(ticket.getTicketId().substring(ticket.getTicketId().lastIndexOf('-') + 1)),
                                ticketQueue.size()));
                notFull.signalAll();
                return true;
            }

            consumerQueue.remove(consumer);
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TicketException("Consumer interrupted while waiting", e);
        } finally {
            poolLock.unlock();
        }
    }

    public boolean consumeVIPTicket(TicketConsumer consumer) {
        poolLock.lock();
        try {
            if (vipTicketsConsumed.get() >= maxVIPTickets) {
                return false;
            }

            boolean consumed = consumeTicket(consumer);
            if (consumed) {
                vipTicketsConsumed.incrementAndGet();
            }
            return consumed;
        } finally {
            poolLock.unlock();
        }
    }

    private Ticket createTicket(TicketProducer producer) {
        String ticketId = String.format("%s-%s-%d", eventName.replaceAll("\\s+", ""),
                producer.getProducerId(),
                ticketsProduced.get() + 1);
        return new Ticket(ticketId, eventName);
    }

    public boolean isAllTicketsProduced() {
        return ticketsProduced.get() >= totalTickets;
    }

    public boolean isAllTicketsConsumed() {
        return isAllTicketsProduced() && ticketQueue.isEmpty();
    }

    public int getAvailableTickets() {
        return ticketQueue.size();
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public int getTicketsProduced() {
        return ticketsProduced.get();
    }

    public int getTicketsConsumed() {
        return ticketsConsumed.get();
    }

    public int getVIPTicketsConsumed() {
        return vipTicketsConsumed.get();
    }

    private void validateParameters(int maxCapacity, int totalTickets) {
        if (maxCapacity <= 0 || totalTickets <= 0) {
            throw new IllegalArgumentException("Maximum capacity and total tickets must be positive");
        }
        if (maxCapacity > totalTickets) {
            throw new IllegalArgumentException("Maximum capacity cannot exceed total tickets");
        }
    }
}
