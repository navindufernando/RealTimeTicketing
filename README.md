# 🎟 Real-Time Event Ticketing System

A robust, multi-threaded **Java** application simulating a **real-time event ticketing system** using the **producer-consumer pattern**. This project showcases advanced **concurrent programming**, **thread safety**, **configuration persistence**. It includes **VIP prioritization**, detailed logging, and runtime statistics.
---

## 🚀 Features

- 🔄 **Producer-Consumer Model**: Multiple producers generate tickets, and consumers (regular and VIP) purchase them concurrently.
- 🎟 **Ticket Pool Management**: Thread-safe ticket pool with configurable capacity and total tickets. Ensures proper production and consumption without overflow.
- 🔒 **Thread Safety**: Utilizes `ReentrantLock` and `Condition` for synchronized access to the ticket pool, avoiding race conditions.
- 🏅 **Priority Consumers**: VIP consumers get prioritized access with customizable limits (typically 25% of total tickets).
- 📝 **Configuration Persistence**: Stores and loads event settings via `.json` files using GSON and `last_config.properties`.
- 📊 **Real-Time Monitoring**: Logs ticket events and pool status with timestamps to both console and log files.
- 📈 **Statistics Reporting**: Displays comprehensive stats on producers, consumers, and VIP activity at runtime.
- 🛠 **Reset Functionality**: Deletes previous logs and configs for a fresh event setup.
- ⚙️ **Flexible Configuration**: Supports custom event name, ticket count, release/retrieval rates, and pool capacity.

---

## 🧰 Tech Stack

### 🔧 Core Technologies
- **Java**: Primary language (JDK 24+ recommended).
- **Java Concurrency Utilities**: 
  - `ReentrantLock`, `Condition`, `BlockingQueue`, `PriorityBlockingQueue`, `ExecutorService`, `ScheduledExecutorService`.
- **GSON**: Handles JSON serialization/deserialization for configuration persistence.
- **Java Logging API**: Custom logging to both console and file with formatters.

---

## 📦 Usage

### 🔍 Launch the Application

Run the application to access the CLI menu.

           MENU OPTIONS                  *
1. Start New Event
2. Load Previous Configuration
3. Reset All Configurations
4. Exit

---

## 📁 Logs & Configs

- **Log Files**: Saved as `<eventName>_ticketing.log` (e.g., `aluthkalawak_ticketing.log`) with detailed timestamps.
- **Config Files**:
  - `<eventName>.json` (e.g., `aluthkalawak.json`)
  - `last_config.properties` (stores last-used setup)

---

## 🔐 Key Components

- **TicketProducer**: Scheduled producers generate tickets.
- **TicketConsumer**: Regular and VIP ticket consumers.
- **TicketPool**: Manages ticket availability with synchronization.
- **Configuration**: Stores event settings.
- **TicketingSystem**: CLI controller, thread manager, logger, and orchestrator.

---

## 🌟 Technical Highlights

- **Thread Safety**: `ReentrantLock` and `Condition` manage pool access.
- **VIP Priority**: `PriorityBlockingQueue` ensures VIPs get first access.
- **Randomized Timing**: 50%–150% jitter simulates real-world delays.
- **Custom Logging**: Info-level logs in file, warnings in console.

---

## 🔧 Development Notes

- **No VIP Tickets Consumed?**  
  With only 4 tickets, VIPs may not get triggered. Use 10+ tickets to test priority consumption logic.
