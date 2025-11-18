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

    private DataSource dataSource;
    private BlockingQueue<MessageQueue> writeQueue;
    private ExecutorService writerThreadPool;

    @Value("${database.writer.batch.size:1000}")
    private int batchSize;

    @Value("${database.writer.threads:10}")
    private int numWriters;

    private boolean isRunning = false;
    private AtomicLong totalWritten = new AtomicLong(0);
    private AtomicLong failedWrites = new AtomicLong(0);

    public DatabaseWriter(DataSource ds) {
        this.dataSource = ds;
        this.writeQueue = new LinkedBlockingQueue<>();
    }

    @PostConstruct
    public void init() {
        System.out.println("Starting database writer...");
        System.out.println("Batch size: " + batchSize);
        System.out.println("Writer threads: " + numWriters);

        isRunning = true;
        writerThreadPool = Executors.newFixedThreadPool(numWriters);

        // start all writer threads
        for (int i = 0; i < numWriters; i++) {
            int threadId = i + 1;
            writerThreadPool.submit(() -> runWriter(threadId));
        }
    }

    public void addMessage(MessageQueue msg) {
        try {
            writeQueue.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void runWriter(int id) {
        List<MessageQueue> batch = new ArrayList<>();

        while (isRunning) {
            try {
                // collect messages for batch
                MessageQueue msg = writeQueue.poll(1, TimeUnit.SECONDS);

                if (msg != null) {
                    batch.add(msg);

                    // get more messages if available
                    writeQueue.drainTo(batch, batchSize - batch.size());
                }

                // write if we have enough messages
                if (batch.size() >= batchSize || (!batch.isEmpty() && msg == null)) {
                    writeToDB(batch);
                    batch.clear();
                }

            } catch (Exception e) {
                System.err.println("Writer " + id + " error: " + e.getMessage());
            }
        }
    }

    private void writeToDB(List<MessageQueue> msgs) {
        if (msgs.isEmpty()) return;

        // build INSERT query with multiple rows
        String query = "INSERT INTO messages (message_id, room_id, user_id, username, " +
                "message, timestamp, message_type, server_id, client_ip) VALUES ";

        // add placeholders for each message
        for (int i = 0; i < msgs.size(); i++) {
            if (i > 0) query += ", ";
            query += "(?,?,?,?,?,?,?,?,?)";
        }

        // handle duplicates
        query += " ON DUPLICATE KEY UPDATE message_id=message_id";

        try {
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(query);

            conn.setAutoCommit(false);

            // fill in values for each message
            int paramIdx = 1;
            for (MessageQueue m : msgs) {
                stmt.setString(paramIdx++, m.getMessageId());
                stmt.setInt(paramIdx++, Integer.parseInt(m.getRoomId()));
                stmt.setInt(paramIdx++, Integer.parseInt(m.getUserId()));
                stmt.setString(paramIdx++, m.getUsername());
                stmt.setString(paramIdx++, m.getMessage());

                // convert timestamp
                Timestamp ts = convertTimestamp(m.getTimestamp());
                stmt.setTimestamp(paramIdx++, ts);

                stmt.setString(paramIdx++, m.getMessageType());
                stmt.setString(paramIdx++, m.getServerId());
                stmt.setString(paramIdx++, m.getClientIp());
            }

            stmt.executeUpdate();
            conn.commit();

            totalWritten.addAndGet(msgs.size());

            stmt.close();
            conn.close();

        } catch (SQLException e) {
            System.err.println("DB write failed: " + e.getMessage());
            failedWrites.addAndGet(msgs.size());
        }
    }

    private Timestamp convertTimestamp(String ts) {
        try {
            Instant inst = Instant.parse(ts);
            return Timestamp.from(inst);
        } catch (Exception e) {
            // if parsing fails, use current time
            return new Timestamp(System.currentTimeMillis());
        }
    }

    @PreDestroy
    public void stop() {
        System.out.println("Stopping database writer...");
        isRunning = false;

        if (writerThreadPool != null) {
            writerThreadPool.shutdown();
        }

        System.out.println("Total messages written: " + totalWritten.get());
        System.out.println("Failed writes: " + failedWrites.get());
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("written", totalWritten.get());
        stats.put("failed", failedWrites.get());
        stats.put("queueSize", writeQueue.size());
        return stats;
    }
}