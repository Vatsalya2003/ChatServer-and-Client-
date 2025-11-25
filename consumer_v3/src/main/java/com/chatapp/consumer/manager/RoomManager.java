//package com.chatapp.consumer.manager;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.chatapp.consumer.model.MessageQueue;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.http.*;
//import org.springframework.http.client.SimpleClientHttpRequestFactory;
//
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicLong;
//
///**
// * SIMPLIFIED OPTIMIZED RoomManager - Async without Apache HttpClient
// */
//@Component
//public class RoomManager {
//
//    private final ObjectMapper objectMapper;
//    private final RestTemplate restTemplate;
//    private final AtomicLong messagesProcessed = new AtomicLong(0);
//
//    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
//    private String serverBroadcastUrl;
//
//    private final Map<String, List<MessageQueue>> batchBuffers = new ConcurrentHashMap<>();
//    private static final int BATCH_SIZE = 200;
//
//    private ExecutorService asyncExecutor;
//
//    public RoomManager(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//
//        // Simple connection factory (no Apache HttpClient needed)
//        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//        factory.setConnectTimeout(3000);
//        factory.setReadTimeout(3000);
//
//        this.restTemplate = new RestTemplate(factory);
//    }
//
//    @PostConstruct
//    public void init() {
//        this.asyncExecutor = Executors.newFixedThreadPool(50);
//        System.out.println("RoomManager initialized with async broadcasting (simplified)");
//        System.out.println("Broadcast batch size: " + BATCH_SIZE);
//    }
//
//    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
//        synchronized (batchBuffers) {
//            List<MessageQueue> batch = batchBuffers.computeIfAbsent(roomId, k -> new ArrayList<>());
//            batch.add(queueMsg);
//
//            if (batch.size() >= BATCH_SIZE) {
//                flushRoomAsync(roomId);
//            }
//        }
//    }
//
//    public void broadcastToRoomAsync(String roomId, MessageQueue queueMsg) {
//        synchronized (batchBuffers) {
//            List<MessageQueue> batch = batchBuffers.computeIfAbsent(roomId, k -> new ArrayList<>());
//            batch.add(queueMsg);
//
//            if (batch.size() >= BATCH_SIZE) {
//                flushRoomAsync(roomId);
//            }
//        }
//    }
//
//    private void flushRoomAsync(String roomId) {
//        List<MessageQueue> toSend;
//
//        synchronized (batchBuffers) {
//            List<MessageQueue> buffer = batchBuffers.get(roomId);
//            if (buffer == null || buffer.isEmpty()) {
//                return;
//            }
//            toSend = new ArrayList<>(buffer);
//            buffer.clear();
//        }
//
//        if (!toSend.isEmpty()) {
//            final List<MessageQueue> messagesToSend = toSend;
//            asyncExecutor.submit(() -> sendBatch(roomId, messagesToSend));
//        }
//    }
//
//    private void sendBatch(String roomId, List<MessageQueue> messages) {
//        try {
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            HttpEntity<List<MessageQueue>> request = new HttpEntity<>(messages, headers);
//
//            ResponseEntity<String> response = restTemplate.postForEntity(
//                    serverBroadcastUrl + "/batch",
//                    request,
//                    String.class
//            );
//
//            if (response.getStatusCode().is2xxSuccessful()) {
//                messagesProcessed.addAndGet(messages.size());
//            }
//
//        } catch (Exception e) {
//            // Silent failure - don't spam logs
//        }
//    }
//
//    public void flushAll() {
//        Set<String> roomIds;
//        synchronized (batchBuffers) {
//            roomIds = new HashSet<>(batchBuffers.keySet());
//        }
//
//        for (String roomId : roomIds) {
//            flushRoomAsync(roomId);
//        }
//    }
//
//    public Map<String, Object> getMetrics() {
//        Map<String, Object> metrics = new HashMap<>();
//        metrics.put("messagesProcessed", messagesProcessed.get());
//        metrics.put("batchSize", BATCH_SIZE);
//
//        int pending = 0;
//        synchronized (batchBuffers) {
//            for (List<MessageQueue> buffer : batchBuffers.values()) {
//                pending += buffer.size();
//            }
//        }
//        metrics.put("pendingInBuffers", pending);
//
//        return metrics;
//    }
//
//    @PreDestroy
//    public void shutdown() {
//        if (asyncExecutor != null) {
//            asyncExecutor.shutdown();
//        }
//    }
//}
//
//////package com.chatapp.consumer.manager;
//////
//////import com.fasterxml.jackson.databind.ObjectMapper;
//////import com.chatapp.consumer.model.MessageQueue;
//////import org.springframework.beans.factory.annotation.Value;
//////import org.springframework.stereotype.Component;
//////import org.springframework.web.client.RestTemplate;
//////import org.springframework.http.*;
//////
//////import java.util.*;
//////import java.util.concurrent.atomic.AtomicLong;
//////
///////**
////// * RoomManager - Calls Server's broadcast endpoint
////// * No longer maintains WebSocket sessions
////// * Just forwards messages to server via REST API
////// */
//////@Component
//////public class RoomManager {
//////
//////    private final ObjectMapper objectMapper;
//////    private final RestTemplate restTemplate = new RestTemplate();
//////    private final AtomicLong messagesProcessed = new AtomicLong(0);
//////
//////    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
//////    private String serverBroadcastUrl;
//////
//////    public RoomManager(ObjectMapper objectMapper) {
//////        this.objectMapper = objectMapper;
//////    }
//////
//////    /**
//////     * Broadcast message by calling Server's REST endpoint
//////     * Server has the WebSocket sessions and will do the actual broadcast
//////     */
//////    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
//////        try {
//////            // Create HTTP request to server
//////            HttpHeaders headers = new HttpHeaders();
//////            headers.setContentType(MediaType.APPLICATION_JSON);
//////
//////            HttpEntity<MessageQueue> request = new HttpEntity<>(queueMsg, headers);
//////
//////            // Call server's broadcast endpoint
//////            ResponseEntity<String> response = restTemplate.postForEntity(
//////                    serverBroadcastUrl,
//////                    request,
//////                    String.class
//////            );
//////
//////            if (response.getStatusCode().is2xxSuccessful()) {
//////                messagesProcessed.incrementAndGet();
//////                System.out.println("✓ Broadcast request sent to server for room " + roomId);
//////            } else {
//////                System.err.println("✗ Server broadcast failed: " + response.getStatusCode());
//////            }
//////
//////        } catch (Exception e) {
//////            System.err.println("✗ Error calling server broadcast for room " + roomId +
//////                    ": " + e.getMessage());
//////        }
//////    }
//////
//////    /**
//////     * Get consumer metrics
//////     */
//////    public Map<String, Object> getMetrics() {
//////        Map<String, Object> metrics = new HashMap<>();
//////        metrics.put("messagesProcessed", messagesProcessed.get());
//////        metrics.put("serverBroadcastUrl", serverBroadcastUrl);
//////        return metrics;
//////    }
//////}
////package com.chatapp.consumer.manager;
////
////import com.fasterxml.jackson.databind.ObjectMapper;
////import com.chatapp.consumer.model.MessageQueue;
////import org.springframework.beans.factory.annotation.Value;
////import org.springframework.stereotype.Component;
////import org.springframework.web.client.RestTemplate;
////import org.springframework.http.*;
////import org.springframework.http.client.SimpleClientHttpRequestFactory;
////import java.util.*;
////import java.util.concurrent.ConcurrentHashMap;
////import java.util.concurrent.atomic.AtomicLong;
////
/////**
//// * Class RoomManager Batches messages and calls Server's broadcast endpoint
//// */
////@Component
////public class RoomManager {
////
////    private final ObjectMapper objectMapper;
//////    private final RestTemplate restTemplate = new RestTemplate();
////    private final RestTemplate restTemplate;
////    private final AtomicLong messagesProcessed = new AtomicLong(0);
////
////    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
////    private String serverBroadcastUrl;
////
////    private final Map<String, List<MessageQueue>> batchBuffers = new ConcurrentHashMap<>();
////    private static final int BATCH_SIZE = 100;
////
////    public RoomManager(ObjectMapper objectMapper) {
////        this.objectMapper = objectMapper;
////
////        // Configure RestTemplate with connection pooling
////        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
////        factory.setConnectTimeout(5000);
////        factory.setReadTimeout(5000);
////
////        this.restTemplate = new RestTemplate(factory);
////    }
////
////    /**
////     * Add message to batch buffer and send when batch is full
////     * This dramatically reduces HTTP calls: 10 messages = 1 HTTP call instead of 10
////     */
////    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
////        List<MessageQueue> batch;
////        boolean shouldSend = false;
////
////        synchronized (batchBuffers) {
////            batch = batchBuffers.computeIfAbsent(roomId, k -> new ArrayList<>());
////            batch.add(queueMsg);
////
////            // If batch is full, prepare to send
////            if (batch.size() >= BATCH_SIZE) {
////                shouldSend = true;
////            }
////        }
////
////        // Send outside synchronized block to avoid blocking other threads
////        if (shouldSend) {
////            flushRoom(roomId);
////        }
////    }
////
////    /**
////     * Flush messages for a specific room
////     */
////    private void flushRoom(String roomId) {
////        List<MessageQueue> toSend;
////
////        synchronized (batchBuffers) {
////            List<MessageQueue> buffer = batchBuffers.get(roomId);
////            if (buffer == null || buffer.isEmpty()) {
////                return;
////            }
////            toSend = new ArrayList<>(buffer);
////            buffer.clear();
////        }
////
////        if (!toSend.isEmpty()) {
////            sendBatch(roomId, toSend);
////        }
////    }
////
////    /**
////     * Send batch of messages to server via REST API
////     */
////    private void sendBatch(String roomId, List<MessageQueue> messages) {
////        try {
////            HttpHeaders headers = new HttpHeaders();
////            headers.setContentType(MediaType.APPLICATION_JSON);
////
////            HttpEntity<List<MessageQueue>> request = new HttpEntity<>(messages, headers);
////
////            ResponseEntity<String> response = restTemplate.postForEntity(
////                    serverBroadcastUrl + "/batch",
////                    request,
////                    String.class
////            );
////
////            if (response.getStatusCode().is2xxSuccessful()) {
////                messagesProcessed.addAndGet(messages.size());
//////                System.out.println("✓ Batch broadcast: " + messages.size() +
//////                        " msgs → room " + roomId);
////            } else {
//////                System.err.println("✗ Batch broadcast failed: " + response.getStatusCode());
////            }
////
////        } catch (Exception e) {
//////            System.err.println("✗ Error sending batch to room " + roomId +
//////                    ": " + e.getMessage());
////        }
////    }
////
////    /**
////     * Flush all remaining messages in all buffers
////     * Called periodically by scheduler and on shutdown
////     */
////    public void flushAll() {
////        Set<String> roomIds;
////
////        synchronized (batchBuffers) {
////            roomIds = new HashSet<>(batchBuffers.keySet());
////        }
////
////        for (String roomId : roomIds) {
////            flushRoom(roomId);
////        }
////    }
////
////    /**
////     * Get consumer metrics
////     */
////    public Map<String, Object> getMetrics() {
////        Map<String, Object> metrics = new HashMap<>();
////        metrics.put("messagesProcessed", messagesProcessed.get());
////        metrics.put("serverBroadcastUrl", serverBroadcastUrl);
////        metrics.put("batchSize", BATCH_SIZE);
////
////        // Count pending messages in buffers
////        int pending = 0;
////        synchronized (batchBuffers) {
////            for (List<MessageQueue> buffer : batchBuffers.values()) {
////                pending += buffer.size();
////            }
////        }
////        metrics.put("pendingInBuffers", pending);
////
////        return metrics;
////    }
////}

package com.chatapp.consumer.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatapp.consumer.model.MessageQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OPTIMIZED RoomManager - Async HTTP without external dependencies
 */
@Component
@EnableAsync
public class RoomManager {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AtomicLong messagesProcessed = new AtomicLong(0);

    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
    private String serverBroadcastUrl;

    // Lock-free queues per room
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MessageQueue>> batchBuffers = new ConcurrentHashMap<>();
    private static final int BATCH_SIZE = 200;

    private ExecutorService asyncExecutor;
    private ScheduledExecutorService periodicFlusher;

    public RoomManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Configure with better timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);

        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        // Large thread pool for async HTTP calls
        this.asyncExecutor = Executors.newFixedThreadPool(100);

        // Periodic flusher every 5 seconds
        this.periodicFlusher = Executors.newSingleThreadScheduledExecutor();
        periodicFlusher.scheduleAtFixedRate(
                this::flushAll,
                5, 5, TimeUnit.SECONDS
        );

        System.out.println("========================================");
        System.out.println("RoomManager initialized - OPTIMIZED");
        System.out.println("Broadcast batch size: " + BATCH_SIZE);
        System.out.println("Async executor threads: 100");
        System.out.println("========================================");
    }

    /**
     * ASYNC broadcast - returns immediately
     */
    public void broadcastToRoomAsync(String roomId, MessageQueue queueMsg) {
        // Use ConcurrentLinkedQueue - lock-free!
        ConcurrentLinkedQueue<MessageQueue> buffer = batchBuffers.computeIfAbsent(
                roomId, k -> new ConcurrentLinkedQueue<>()
        );

        buffer.offer(queueMsg);

        // If batch is full, flush in background
        if (buffer.size() >= BATCH_SIZE) {
            flushRoomAsync(roomId);
        }
    }

    /**
     * Legacy method - redirects to async
     */
    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
        broadcastToRoomAsync(roomId, queueMsg);
    }

    /**
     * Flush specific room asynchronously
     */
    private void flushRoomAsync(String roomId) {
        ConcurrentLinkedQueue<MessageQueue> buffer = batchBuffers.get(roomId);
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // Drain up to BATCH_SIZE messages
        List<MessageQueue> toSend = new ArrayList<>();
        MessageQueue msg;
        while ((msg = buffer.poll()) != null && toSend.size() < BATCH_SIZE) {
            toSend.add(msg);
        }

        if (!toSend.isEmpty()) {
            final List<MessageQueue> messagesToSend = toSend;
            // Submit to async executor - returns immediately!
            asyncExecutor.submit(() -> sendBatchSync(roomId, messagesToSend));
        }
    }

    /**
     * Send batch via HTTP - runs in background thread
     */
    private void sendBatchSync(String roomId, List<MessageQueue> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<List<MessageQueue>> request = new HttpEntity<>(messages, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    serverBroadcastUrl + "/batch",
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                messagesProcessed.addAndGet(messages.size());
            }

        } catch (Exception e) {
            // Silent failure - don't spam logs
        }
    }

    /**
     * Flush all rooms
     */
    public void flushAll() {
        for (String roomId : batchBuffers.keySet()) {
            flushRoomAsync(roomId);
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messagesProcessed", messagesProcessed.get());
        metrics.put("batchSize", BATCH_SIZE);

        int pending = batchBuffers.values().stream()
                .mapToInt(ConcurrentLinkedQueue::size)
                .sum();
        metrics.put("pendingInBuffers", pending);

        return metrics;
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Shutting down RoomManager...");

        if (periodicFlusher != null) {
            periodicFlusher.shutdown();
            try {
                periodicFlusher.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                periodicFlusher.shutdownNow();
            }
        }

        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                asyncExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
            }
        }

        // Final flush
        flushAll();

        System.out.println("RoomManager shutdown complete");
    }
}