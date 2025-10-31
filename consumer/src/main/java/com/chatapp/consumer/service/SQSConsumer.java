//package com.chatapp.consumer.service;
package com.chatapp.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatapp.consumer.manager.RoomManager;
import com.chatapp.consumer.model.MessageQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SQSConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RoomManager roomManager;
    private ExecutorService executorService;
    private ScheduledExecutorService flushScheduler;

    @Value("${aws.account.id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    @Value("${consumer.threads:20}")
    private int consumerThreads;

    @Value("${consumer.polling.wait.seconds:20}")
    private int pollingWaitSeconds;

    @Value("${consumer.batch.size:10}")
    private int batchSize;

    private volatile boolean running = true;
    private final String[] queueUrls = new String[20];

    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong batchesFetched = new AtomicLong(0);
    private final long startTime = System.currentTimeMillis();

    public SQSConsumer(SqsClient sqsClient, ObjectMapper objectMapper, RoomManager roomManager) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.roomManager = roomManager;
    }

    @PostConstruct
    public void initialize() {
        System.out.println("\n" + "-------------------------------");
        System.out.println("Initializing SQS Consumer...");

        this.executorService = Executors.newFixedThreadPool(consumerThreads);

        for (int i = 0; i < 20; i++) {
            queueUrls[i] = String.format(
                    "https://sqs.%s.amazonaws.com/%s/chat-room-%d.fifo",
                    region, accountId, (i + 1)
            );
        }

        // Calculate consumers per room
        int consumersPerRoom = Math.max(1, consumerThreads / 20);

        System.out.println(" Queue URLs initialized for 20 rooms");
        System.out.println(" Total consumer threads: " + consumerThreads);
        System.out.println(" Consumers per room: " + consumersPerRoom);
        System.out.println(" Long polling: " + pollingWaitSeconds + " seconds");
        System.out.println(" Batch size: " + batchSize + " messages");
        System.out.println("\n" + "-------------------------------");

        startConsuming(consumersPerRoom);
        startFlushScheduler();
    }

    private void startFlushScheduler() {
        flushScheduler = Executors.newSingleThreadScheduledExecutor();
        flushScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        roomManager.flushAll();
                    } catch (Exception e) {
                        System.err.println("!!! Error during flush: " + e.getMessage());
                    }
                },
                5,  // Initial delay: 1 second
                5,  // Period: flush every 1 second
                TimeUnit.SECONDS
        );
    }

    private void startConsuming(int consumersPerRoom) {
        System.out.println("🚀 Starting " + (consumersPerRoom * 20) +
                " total consumers across 20 rooms...\n");

        for (int i = 0; i < 20; i++) {
            final int roomIndex = i;
            final String roomId = String.valueOf(i + 1);

            // Create multiple consumers per room
            for (int c = 0; c < consumersPerRoom; c++) {
                final int consumerNum = c + 1;
                executorService.submit(() -> consumeRoom(roomIndex, roomId, consumerNum));
            }
        }
    }

    private void consumeRoom(int roomIndex, String roomId, int consumerNum) {
        String queueUrl = queueUrls[roomIndex];

        System.out.println(" Consumer #" + consumerNum + " started for room " + roomId);

        while (running) {
            try {
                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(batchSize)
                        .waitTimeSeconds(pollingWaitSeconds)
                        .visibilityTimeout(30)
                        .build();

                ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
                List<Message> messages = response.messages();

                if (!messages.isEmpty()) {
                    batchesFetched.incrementAndGet();
                    messagesConsumed.addAndGet(messages.size());

//                    System.out.println("📨 Room " + roomId + " [C" + consumerNum + "]: " +
//                            messages.size() + " msgs | Total: " + messagesConsumed.get());

                    for (Message message : messages) {
                        try {
                            MessageQueue queueMsg = objectMapper.readValue(
                                    message.body(), MessageQueue.class);

                            // Add to batch buffer (will auto-send when full)
                            roomManager.broadcastToRoom(roomId, queueMsg);

                            // Delete from SQS immediately
                            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .receiptHandle(message.receiptHandle())
                                    .build();

                            sqsClient.deleteMessage(deleteRequest);

                        } catch (Exception e) {
//                            System.err.println(" Error processing message in room " + roomId +
//                                    ": " + e.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.println(" Consumer #" + consumerNum + " stopped for room " + roomId);
    }

    @PreDestroy
    public void stopConsuming() {
        System.out.println("\n!! Stopping SQS consumers...!!");
        running = false;
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try {
                if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    flushScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                flushScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Stop consumer threads
        if (executorService != null) {
            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                System.out.println("✓ All consumer threads stopped");
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        roomManager.flushAll();

        System.out.println(" SQS Consumer shutdown complete\n");
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>(roomManager.getMetrics());

        long runtime = (System.currentTimeMillis() - startTime) / 1000;
        double consumptionRate = runtime > 0 ?
                (double) messagesConsumed.get() / runtime : 0;

        metrics.put("messagesConsumed", messagesConsumed.get());
        metrics.put("batchesFetched", batchesFetched.get());
        metrics.put("consumptionRate", String.format("%.2f msg/sec", consumptionRate));
        metrics.put("runtimeSeconds", runtime);
        metrics.put("activeConsumers", consumerThreads);

        return metrics;
    }
}