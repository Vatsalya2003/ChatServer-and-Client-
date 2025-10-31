//package com.chatapp.consumer.manager;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.chatapp.consumer.model.MessageQueue;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.http.*;
//
//import java.util.*;
//import java.util.concurrent.atomic.AtomicLong;
//
///**
// * RoomManager - Calls Server's broadcast endpoint
// * No longer maintains WebSocket sessions
// * Just forwards messages to server via REST API
// */
//@Component
//public class RoomManager {
//
//    private final ObjectMapper objectMapper;
//    private final RestTemplate restTemplate = new RestTemplate();
//    private final AtomicLong messagesProcessed = new AtomicLong(0);
//
//    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
//    private String serverBroadcastUrl;
//
//    public RoomManager(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//    }
//
//    /**
//     * Broadcast message by calling Server's REST endpoint
//     * Server has the WebSocket sessions and will do the actual broadcast
//     */
//    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
//        try {
//            // Create HTTP request to server
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            HttpEntity<MessageQueue> request = new HttpEntity<>(queueMsg, headers);
//
//            // Call server's broadcast endpoint
//            ResponseEntity<String> response = restTemplate.postForEntity(
//                    serverBroadcastUrl,
//                    request,
//                    String.class
//            );
//
//            if (response.getStatusCode().is2xxSuccessful()) {
//                messagesProcessed.incrementAndGet();
//                System.out.println("✓ Broadcast request sent to server for room " + roomId);
//            } else {
//                System.err.println("✗ Server broadcast failed: " + response.getStatusCode());
//            }
//
//        } catch (Exception e) {
//            System.err.println("✗ Error calling server broadcast for room " + roomId +
//                    ": " + e.getMessage());
//        }
//    }
//
//    /**
//     * Get consumer metrics
//     */
//    public Map<String, Object> getMetrics() {
//        Map<String, Object> metrics = new HashMap<>();
//        metrics.put("messagesProcessed", messagesProcessed.get());
//        metrics.put("serverBroadcastUrl", serverBroadcastUrl);
//        return metrics;
//    }
//}
package com.chatapp.consumer.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatapp.consumer.model.MessageQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class RoomManager Batches messages and calls Server's broadcast endpoint
 */
@Component
public class RoomManager {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicLong messagesProcessed = new AtomicLong(0);

    @Value("${server.broadcast.url:http://localhost:8080/api/broadcast}")
    private String serverBroadcastUrl;

    private final Map<String, List<MessageQueue>> batchBuffers = new ConcurrentHashMap<>();
    private static final int BATCH_SIZE = 10;

    public RoomManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Add message to batch buffer and send when batch is full
     * This dramatically reduces HTTP calls: 10 messages = 1 HTTP call instead of 10
     */
    public void broadcastToRoom(String roomId, MessageQueue queueMsg) {
        List<MessageQueue> batch;
        boolean shouldSend = false;

        synchronized (batchBuffers) {
            batch = batchBuffers.computeIfAbsent(roomId, k -> new ArrayList<>());
            batch.add(queueMsg);

            // If batch is full, prepare to send
            if (batch.size() >= BATCH_SIZE) {
                shouldSend = true;
            }
        }

        // Send outside synchronized block to avoid blocking other threads
        if (shouldSend) {
            flushRoom(roomId);
        }
    }

    /**
     * Flush messages for a specific room
     */
    private void flushRoom(String roomId) {
        List<MessageQueue> toSend;

        synchronized (batchBuffers) {
            List<MessageQueue> buffer = batchBuffers.get(roomId);
            if (buffer == null || buffer.isEmpty()) {
                return;
            }
            toSend = new ArrayList<>(buffer);
            buffer.clear();
        }

        if (!toSend.isEmpty()) {
            sendBatch(roomId, toSend);
        }
    }

    /**
     * Send batch of messages to server via REST API
     */
    private void sendBatch(String roomId, List<MessageQueue> messages) {
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
//                System.out.println("✓ Batch broadcast: " + messages.size() +
//                        " msgs → room " + roomId);
            } else {
//                System.err.println("✗ Batch broadcast failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
//            System.err.println("✗ Error sending batch to room " + roomId +
//                    ": " + e.getMessage());
        }
    }

    /**
     * Flush all remaining messages in all buffers
     * Called periodically by scheduler and on shutdown
     */
    public void flushAll() {
        Set<String> roomIds;

        synchronized (batchBuffers) {
            roomIds = new HashSet<>(batchBuffers.keySet());
        }

        for (String roomId : roomIds) {
            flushRoom(roomId);
        }
    }

    /**
     * Get consumer metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messagesProcessed", messagesProcessed.get());
        metrics.put("serverBroadcastUrl", serverBroadcastUrl);
        metrics.put("batchSize", BATCH_SIZE);

        // Count pending messages in buffers
        int pending = 0;
        synchronized (batchBuffers) {
            for (List<MessageQueue> buffer : batchBuffers.values()) {
                pending += buffer.size();
            }
        }
        metrics.put("pendingInBuffers", pending);

        return metrics;
    }
}