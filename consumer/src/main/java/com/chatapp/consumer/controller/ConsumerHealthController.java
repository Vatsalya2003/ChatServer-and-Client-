package com.chatapp.consumer.controller;


import com.chatapp.consumer.service.SQSConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Class ConsumerHealthController for health checks
 */
@RestController
public class ConsumerHealthController {

    @Autowired
    private SQSConsumer sqsConsumer;

    /**
     * Health check endpoint
     * Returns simple UP status
     * Used by load balancers and monitoring tools
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Chat Consumer");
        response.put("timestamp", java.time.Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Detailed metrics endpoint
     * Returns consumer statistics for monitoring
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(sqsConsumer.getMetrics());
    }

    /**
     * Simple status message
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("Chat Consumer Application - Running");
    }
}
