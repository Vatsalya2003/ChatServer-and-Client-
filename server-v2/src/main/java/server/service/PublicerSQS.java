package server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import server.model.MessageQueue;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe SQS Publisher with connection pooling and circuit breaker
 */
@Service
public class PublicerSQS {
    //injected by spring
    private final SqsClient sqsClient;
    //json serialization
    private final ObjectMapper objectMapper;
    private final CircuitBreakerSQS circuitBreaker = new CircuitBreakerSQS();

    @Value("${aws.account.id}")
    private String accountId;

    @Value("${aws.region}")
    private String region;

    //Thread-safe map ConcurrentHashMap for Stroing queue URLs
    private final ConcurrentHashMap<String, String> queueUrlCache = new ConcurrentHashMap<>();

    /**
     * Constructor with dependency injection.
     * @param sqsClient The AWS SQS client for queue operations
     * @param objectMapper The Jackson ObjectMapper for JSON serialization
     */
    public PublicerSQS(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        System.out.println("*Initializing SQS Publisher...*");
        for (int i = 1; i <= 20; i++) {
            String roomId = String.valueOf(i);
            String queueUrl = String.format(
                    "https://sqs.%s.amazonaws.com/%s/chat-room-%s.fifo",
                    region, accountId, roomId
            );
            queueUrlCache.put(roomId, queueUrl);
        }
        System.out.println("*** Queue URL cache initialized for 20 rooms ***");
    }

    /**
     * Publishes a message to the appropriate SQS FIFO queue
     * @param message The MessageQueue object containing message details and metadata
     * @throws Error if circuit breaker is OPEN or publish fails after retries
     */
    public void publishMessage(MessageQueue message) {
        // S1: Check circuit breaker
        if (!circuitBreaker.allowRequest()) {
            throw new RuntimeException("Circuit breaker is OPEN");
        }

        try {
            String queueUrl = queueUrlCache.get(message.getRoomId());
            String messageBody = objectMapper.writeValueAsString(message);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(message.getRoomId())
                    .messageDeduplicationId(message.getMessageId())
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);
            circuitBreaker.recordSuccess();

//            System.out.println("✓ Published - Room: " + message.getRoomId());

        } catch (Exception e) {
            circuitBreaker.recordFailure();
            System.err.println("✗ SQS Error: " + e.getMessage());
            throw new RuntimeException("Failed to publish", e);
        }
    }

    /**
     * Tests the connection to AWS SQS by listing available queues.
     * @throws Error if unable to connect to SQS or list queues
     */
    public void testConnection() {
        try {
            ListQueuesResponse response = sqsClient.listQueues(
                    ListQueuesRequest.builder().queueNamePrefix("chat-room-").build()
            );
            System.out.println("✓ SQS Connected! Found " + response.queueUrls().size() + " queues");
        } catch (Exception e) {
            System.err.println("✗ SQS Connection failed: " + e.getMessage());
            throw new RuntimeException("Cannot connect to SQS", e);
        }
    }

    /**
     * Retrieves the current status of the circuit breaker
     * @return Map containing circuit breaker state and failure count
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("state", circuitBreaker.getState().name());
        status.put("failures", circuitBreaker.getFailureCount());
        return status;
    }
}