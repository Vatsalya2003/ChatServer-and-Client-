package com.chatapp.consumer.service;

import com.chatapp.consumer.model.MessageQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DatabaseWriter {

    private final DataSource dataSource;
    private final BlockingQueue<MessageQueue> writeQueue;
    private ExecutorService writerThreadPool;

    @Value("${database.writer.batch.size:500}")
    private int batchSize;

    @Value("${database.writer.threads:8}")
    private int numWriters;

    @Value("${database.writer.flush.interval.ms:500}")
    private int flushIntervalMs;

    private volatile boolean isRunning = false;

    private final AtomicLong totalWritten = new AtomicLong(0);
    private final AtomicLong failedWrites = new AtomicLong(0);
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong totalBatchLatency = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private long startTime;

    private static final int CIRCUIT_THRESHOLD = 5;
    private volatile boolean circuitOpen = false;

    public DatabaseWriter(DataSource ds) {
        this.dataSource = ds;
        this.writeQueue = new LinkedBlockingQueue<>(100000);
    }

    @PostConstruct
    public void init() {
        System.out.println("\n========================================");
        System.out.println("Starting OPTIMIZED Database Writer...");
        System.out.println("Batch size: " + batchSize);
        System.out.println("Writer threads: " + numWriters);
        System.out.println("Flush interval: " + flushIntervalMs + "ms");
        System.out.println("========================================\n");

        startTime = System.currentTimeMillis();
        isRunning = true;
        writerThreadPool = Executors.newFixedThreadPool(numWriters);

        for (int i = 0; i < numWriters; i++) {
            int threadId = i + 1;
            writerThreadPool.submit(() -> runWriter(threadId));
        }
    }

    public void addMessage(MessageQueue msg) {
        if (!isRunning || circuitOpen) {
            return;
        }

        if (!writeQueue.offer(msg)) {
            failedWrites.incrementAndGet();
        }
    }

    private void runWriter(int id) {
        List<MessageQueue> batch = new ArrayList<>(batchSize);
        long lastFlushTime = System.currentTimeMillis();

        while (isRunning) {
            try {
                MessageQueue msg = writeQueue.poll(100, TimeUnit.MILLISECONDS);

                if (msg != null) {
                    batch.add(msg);
                    writeQueue.drainTo(batch, batchSize - batch.size());
                }

                long now = System.currentTimeMillis();
                boolean shouldFlush = batch.size() >= batchSize ||
                        (!batch.isEmpty() && (now - lastFlushTime) >= flushIntervalMs);

                if (shouldFlush) {
                    writeToDB(batch, id);
                    batch.clear();
                    lastFlushTime = now;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Writer thread " + id + " error: " + e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            writeToDB(batch, id);
        }
    }

    private void writeToDB(List<MessageQueue> msgs, int threadId) {
        if (msgs.isEmpty()) return;

        if (circuitOpen) {
            failedWrites.addAndGet(msgs.size());
            return;
        }

        long batchStart = System.currentTimeMillis();

        StringBuilder queryBuilder = new StringBuilder(
                "INSERT INTO messages (message_id, room_id, user_id, username, " +
                        "message, timestamp, message_type, server_id, client_ip) VALUES "
        );

        for (int i = 0; i < msgs.size(); i++) {
            if (i > 0) queryBuilder.append(", ");
            queryBuilder.append("(?,?,?,?,?,?,?,?,?)");
        }

        queryBuilder.append(" ON DUPLICATE KEY UPDATE message_id=message_id");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {

            conn.setAutoCommit(false);

            int paramIdx = 1;
            for (MessageQueue m : msgs) {
                stmt.setString(paramIdx++, m.getMessageId());
                stmt.setInt(paramIdx++, Integer.parseInt(m.getRoomId()));
                stmt.setInt(paramIdx++, Integer.parseInt(m.getUserId()));
                stmt.setString(paramIdx++, m.getUsername());
                stmt.setString(paramIdx++, m.getMessage());
                stmt.setTimestamp(paramIdx++, convertTimestamp(m.getTimestamp()));
                stmt.setString(paramIdx++, m.getMessageType());
                stmt.setString(paramIdx++, m.getServerId());
                stmt.setString(paramIdx++, m.getClientIp());
            }

            stmt.executeUpdate();
            conn.commit();

            long batchLatency = System.currentTimeMillis() - batchStart;
            totalWritten.addAndGet(msgs.size());
            batchCount.incrementAndGet();
            totalBatchLatency.addAndGet(batchLatency);

            consecutiveFailures.set(0);
            if (circuitOpen) {
                circuitOpen = false;
                System.out.println("✅ Circuit breaker CLOSED - database recovered");
            }

            if (batchCount.get() % 50 == 0) {
                long runtime = (System.currentTimeMillis() - startTime) / 1000;
                double throughput = runtime > 0 ? totalWritten.get() / (double) runtime : 0;
                System.out.println(String.format(
                        "📊 Writer-%d | Batches: %d | Written: %,d | Throughput: %.1f msg/sec | Queue: %d",
                        threadId, batchCount.get(), totalWritten.get(), throughput, writeQueue.size()
                ));
            }

        } catch (SQLException e) {
            System.err.println("DB write failed: " + e.getMessage());
            failedWrites.addAndGet(msgs.size());

            if (consecutiveFailures.incrementAndGet() >= CIRCUIT_THRESHOLD) {
                circuitOpen = true;
                System.err.println("⚠️ Circuit breaker OPEN!");
            }
        }
    }

    private Timestamp convertTimestamp(String ts) {
        try {
            Instant inst = Instant.parse(ts);
            return Timestamp.from(inst);
        } catch (Exception e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    @PreDestroy
    public void stop() {
        System.out.println("\n========================================");
        System.out.println("Stopping database writer...");
        isRunning = false;

        if (writerThreadPool != null) {
            writerThreadPool.shutdown();
            try {
                if (!writerThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    writerThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                writerThreadPool.shutdownNow();
            }
        }

        long runtime = (System.currentTimeMillis() - startTime) / 1000;
        double throughput = runtime > 0 ? totalWritten.get() / (double) runtime : 0;
        double avgBatchLatency = batchCount.get() > 0 ?
                totalBatchLatency.get() / (double) batchCount.get() : 0;

        System.out.println("\n📊 FINAL STATISTICS:");
        System.out.println("  Total messages written: " + String.format("%,d", totalWritten.get()));
        System.out.println("  Failed writes: " + failedWrites.get());
        System.out.println("  Total batches: " + batchCount.get());
        System.out.println("  Runtime: " + runtime + " seconds");
        System.out.println("  Throughput: " + String.format("%.2f msg/sec", throughput));
        System.out.println("  Avg batch latency: " + String.format("%.2f ms", avgBatchLatency));
        System.out.println("========================================\n");
    }

    public Map<String, Object> getStats() {
        long runtime = (System.currentTimeMillis() - startTime) / 1000;
        double throughput = runtime > 0 ? totalWritten.get() / (double) runtime : 0;
        double avgBatchLatency = batchCount.get() > 0 ?
                totalBatchLatency.get() / (double) batchCount.get() : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("written", totalWritten.get());
        stats.put("failed", failedWrites.get());
        stats.put("queueSize", writeQueue.size());
        stats.put("batchCount", batchCount.get());
        stats.put("runtimeSeconds", runtime);
        stats.put("throughput", String.format("%.2f msg/sec", throughput));
        stats.put("avgBatchLatency", String.format("%.2f ms", avgBatchLatency));
        stats.put("circuitOpen", circuitOpen);

        return stats;
    }
}

//package com.chatapp.consumer.service;
//
//import com.chatapp.consumer.model.MessageQueue;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import javax.sql.DataSource;
//import java.sql.*;
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicLong;
//
//@Service
//public class DatabaseWriter {
//
//    private DataSource dataSource;
//    private BlockingQueue<MessageQueue> writeQueue;
//    private ExecutorService writerThreadPool;
//
//    @Value("${database.writer.batch.size:1000}")
//    private int batchSize;
//
//    @Value("${database.writer.threads:10}")
//    private int numWriters;
//
//    private boolean isRunning = false;
//    private AtomicLong totalWritten = new AtomicLong(0);
//    private AtomicLong failedWrites = new AtomicLong(0);
//    private AtomicLong batchCount = new AtomicLong(0);
//    private AtomicLong totalBatchLatency = new AtomicLong(0);
//    private long startTime;
//
//    public DatabaseWriter(DataSource ds) {
//        this.dataSource = ds;
//        this.writeQueue = new LinkedBlockingQueue<>();
//    }
//
//    @PostConstruct
//    public void init() {
//        System.out.println("Starting database writer...");
//        System.out.println("Batch size: " + batchSize);
//        System.out.println("Writer threads: " + numWriters);
//
//        startTime = System.currentTimeMillis();
//
//        isRunning = true;
//        writerThreadPool = Executors.newFixedThreadPool(numWriters);
//
//        // start all writer threads
//        for (int i = 0; i < numWriters; i++) {
//            int threadId = i + 1;
//            writerThreadPool.submit(() -> runWriter(threadId));
//        }
//    }
//
//    public void addMessage(MessageQueue msg) {
//        try {
//            writeQueue.put(msg);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void runWriter(int id) {
//        List<MessageQueue> batch = new ArrayList<>();
//
//        while (isRunning) {
//            try {
//                // collect messages for batch
//                MessageQueue msg = writeQueue.poll(1, TimeUnit.SECONDS);
//
//                if (msg != null) {
//                    batch.add(msg);
//
//                    // get more messages if available
//                    writeQueue.drainTo(batch, batchSize - batch.size());
//                }
//
//                // write if we have enough messages
//                if (batch.size() >= batchSize || (!batch.isEmpty() && msg == null)) {
//                    writeToDB(batch);
//                    batch.clear();
//                }
//
//            } catch (Exception e) {
//                System.err.println("Writer " + id + " error: " + e.getMessage());
//            }
//        }
//    }
//
//    private void writeToDB(List<MessageQueue> msgs) {
//        if (msgs.isEmpty()) return;
//
//        long batchStart = System.currentTimeMillis();
//        // build INSERT query with multiple rows
//        String query = "INSERT INTO messages (message_id, room_id, user_id, username, " +
//                "message, timestamp, message_type, server_id, client_ip) VALUES ";
//
//        // add placeholders for each message
//        for (int i = 0; i < msgs.size(); i++) {
//            if (i > 0) query += ", ";
//            query += "(?,?,?,?,?,?,?,?,?)";
//        }
//
//        // handle duplicates
//        query += " ON DUPLICATE KEY UPDATE message_id=message_id";
//
//        try (Connection conn = dataSource.getConnection();
//             PreparedStatement stmt = conn.prepareStatement(query)) {
//
//            conn.setAutoCommit(false);
//
//            // fill in values for each message
//            int paramIdx = 1;
//            for (MessageQueue m : msgs) {
//                stmt.setString(paramIdx++, m.getMessageId());
//                stmt.setInt(paramIdx++, Integer.parseInt(m.getRoomId()));
//                stmt.setInt(paramIdx++, Integer.parseInt(m.getUserId()));
//                stmt.setString(paramIdx++, m.getUsername());
//                stmt.setString(paramIdx++, m.getMessage());
//                stmt.setTimestamp(paramIdx++, convertTimestamp(m.getTimestamp()));
//                stmt.setString(paramIdx++, m.getMessageType());
//                stmt.setString(paramIdx++, m.getServerId());
//                stmt.setString(paramIdx++, m.getClientIp());
//            }
//
//            stmt.executeUpdate();
//            conn.commit();
//
//            long batchLatency = System.currentTimeMillis() - batchStart;
//            totalWritten.addAndGet(msgs.size());
//            batchCount.incrementAndGet();
//            totalBatchLatency.addAndGet(batchLatency);
//
//        } catch (SQLException e) {
//            System.err.println("DB write failed: " + e.getMessage());
//            failedWrites.addAndGet(msgs.size());
//        }
////        try {
////            Connection conn = dataSource.getConnection();
////            PreparedStatement stmt = conn.prepareStatement(query);
////
////            conn.setAutoCommit(false);
////
////            // fill in values for each message
////            int paramIdx = 1;
////            for (MessageQueue m : msgs) {
////                stmt.setString(paramIdx++, m.getMessageId());
////                stmt.setInt(paramIdx++, Integer.parseInt(m.getRoomId()));
////                stmt.setInt(paramIdx++, Integer.parseInt(m.getUserId()));
////                stmt.setString(paramIdx++, m.getUsername());
////                stmt.setString(paramIdx++, m.getMessage());
////
////                // convert timestamp
////                Timestamp ts = convertTimestamp(m.getTimestamp());
////                stmt.setTimestamp(paramIdx++, ts);
////
////                stmt.setString(paramIdx++, m.getMessageType());
////                stmt.setString(paramIdx++, m.getServerId());
////                stmt.setString(paramIdx++, m.getClientIp());
////            }
////
////            stmt.executeUpdate();
////            conn.commit();
////
////            long batchLatency = System.currentTimeMillis() - batchStart;
////            totalWritten.addAndGet(msgs.size());
////            batchCount.incrementAndGet();
////            totalBatchLatency.addAndGet(batchLatency);
////
////            stmt.close();
////            conn.close();
////
////        } catch (SQLException e) {
////            System.err.println("DB write failed: " + e.getMessage());
////            failedWrites.addAndGet(msgs.size());
////        }
//    }
//
//    private Timestamp convertTimestamp(String ts) {
//        try {
//            Instant inst = Instant.parse(ts);
//            return Timestamp.from(inst);
//        } catch (Exception e) {
//            // if parsing fails, use current time
//            return new Timestamp(System.currentTimeMillis());
//        }
//    }
//
//    @PreDestroy
//    public void stop() {
//        System.out.println("Stopping database writer...");
//        isRunning = false;
//
//        if (writerThreadPool != null) {
//            writerThreadPool.shutdown();
//        }
//
//        System.out.println("Total messages written: " + totalWritten.get());
//        System.out.println("Failed writes: " + failedWrites.get());
//    }
//
//    public Map<String, Object> getStats() {
//        Map<String, Object> stats = new HashMap<>();
//
//        long runtime = (System.currentTimeMillis() - startTime) / 1000;
//        double throughput = runtime > 0 ? totalWritten.get() / (double) runtime : 0;
//        double avgBatchLatency = batchCount.get() > 0 ?
//                totalBatchLatency.get() / (double) batchCount.get() : 0;
//
//        stats.put("written", totalWritten.get());
//        stats.put("failed", failedWrites.get());
//        stats.put("queueSize", writeQueue.size());
//        stats.put("batchCount", batchCount.get());
//        stats.put("runtimeSeconds", runtime);
//        stats.put("throughput", String.format("%.2f msg/sec", throughput));
//        stats.put("avgBatchLatency", String.format("%.2f ms", avgBatchLatency));
//
//        return stats;
//    }
//}