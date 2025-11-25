package client2;

import client2.model.ChatMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OPTIMIZED: Fast response tracking with short timeout
 */
public class MSGSenderThread implements Runnable {

    private static final int MAX_RETRIES = 3;
    private static final int RESPONSE_TIMEOUT = 1000;  // 100ms instead of 1000ms!

    private String serverUrl;
    private BlockingQueue<ChatMessage> queue;
    private int messagesToSend;
    private CountDownLatch latch;
    private int ThreadNumber;

    private WebSocketClient client;
    private volatile boolean connected = false;
    private int roomId;

    private CountDownLatch responseLatch;
    private volatile boolean gotResponse;
    private volatile String serverResponse;
    private ArrayList<MessageData> messageDataList;

    public MSGSenderThread(String url, BlockingQueue<ChatMessage> queue,
                           int messages, CountDownLatch latch, int id,
                           ArrayList<MessageData> dataList) {
        this.serverUrl = url;
        this.queue = queue;
        this.messagesToSend = messages;
        this.latch = latch;
        this.ThreadNumber = id;
        this.roomId = (id % 20) + 1;
        this.messageDataList = dataList;
    }

    // Callbacks
    public void onSuccess() {}
    public void onFail() {}
    public void onConnect() {}
    public void onReconnect() {}

    @Override
    public void run() {
        try {
            if (!connect()) {
                System.err.println("Thread-" + ThreadNumber + ": Failed to connect");
                return;
            }

            int sent = 0;
            int success = 0;
            long startTime = System.currentTimeMillis();

            while (sent < messagesToSend) {
                ChatMessage msg = queue.poll(100, TimeUnit.MILLISECONDS);

                if (msg == null) {
                    continue;
                }

                // Send and measure
                MessageData data = sendMessageAndWait(msg);

                if (data != null && data.latency >= 0) {
                    success++;
                    onSuccess();
                    synchronized(messageDataList) {
                        messageDataList.add(data);
                    }
                } else {
                    onFail();
                }

                sent++;

                // Progress every 50 messages
                if (sent % 50 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double rate = (sent * 1000.0) / elapsed;
                    System.out.println("Thread-" + ThreadNumber + ": " + sent + "/" + messagesToSend +
                            " (" + String.format("%.0f", rate) + " msg/sec, " +
                            success + " success)");
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            double rate = (sent * 1000.0) / elapsed;
            System.out.println("✅ Thread-" + ThreadNumber + " COMPLETE: " + sent +
                    " sent, " + success + " success in " + elapsed + "ms (" +
                    String.format("%.0f", rate) + " msg/sec)");

        } catch (Exception e) {
            System.err.println("❌ Thread-" + ThreadNumber + " error: " + e.getMessage());
        } finally {
            cleanup();
            latch.countDown();
        }
    }

    private boolean connect() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String url = serverUrl + "/chat/" + roomId;
                URI uri = new URI(url);

                client = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        connected = true;
                        onConnect();
                    }

                    @Override
                    public void onMessage(String message) {
                        gotResponse = true;
                        serverResponse = message;
                        if (responseLatch != null) {
                            responseLatch.countDown();
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        connected = false;
                    }

                    @Override
                    public void onError(Exception ex) {
                        connected = false;
                    }
                };

                boolean connectResult = client.connectBlocking(10, TimeUnit.SECONDS);

                if (connected && connectResult) {
                    if (attempt > 1) {
                        onReconnect();
                    }
                    return true;
                }

            } catch (Exception e) {
                if (attempt < 3) {
                    try {
                        Thread.sleep(50 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * OPTIMIZED: Short timeout (100ms) with fast retry
     */
    private MessageData sendMessageAndWait(ChatMessage msg) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Check connection
                if (!connected) {
                    if (!connect()) {
                        return null;
                    }
                    onReconnect();
                }

                // Prepare for response
                responseLatch = new CountDownLatch(1);
                gotResponse = false;
                serverResponse = null;

                // Record start time
                long startTime = System.currentTimeMillis();

                // Send message
                client.send(msg.toJson());

                // Wait for response - SHORT TIMEOUT!
                boolean got = responseLatch.await(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);

                // Calculate latency
                long latency = System.currentTimeMillis() - startTime;

                // Check if got response
                if (got && gotResponse) {
                    String status = "OK";
                    if (serverResponse != null && serverResponse.contains("ERROR")) {
                        status = "ERROR";
                    }

                    return new MessageData(
                            startTime,
                            msg.getMessageType(),
                            latency,
                            status,
                            roomId
                    );
                }

                // Timeout - retry immediately on first attempt
                if (attempt == 0) {
                    continue;  // Fast retry
                }

            } catch (Exception e) {
                // Error - retry with minimal backoff
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(20);  // Just 20ms backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        return null;  // Failed after retries
    }

    private void cleanup() {
        if (client != null) {
            try {
                client.closeBlocking();
            } catch (Exception e) {}
        }
    }
}
//package client2;
//
//import client2.model.ChatMessage;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//import java.net.URI;
//import java.util.ArrayList;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
///**
// * Thread that sends messages and tracks latency
// */
//public class MSGSenderThread implements Runnable {
//
//    private static final int Retries = 5;
//    private static final int Timeout_Response = 1000;
//
//    private String serverUrl;
//    private BlockingQueue<ChatMessage> queue;
//    private int messagesToSend;
//    private CountDownLatch latch;
//    private int ThreadNumber;
//
//    private WebSocketClient client;
//    private boolean connected = false;
//    private int roomId;
//
//    private CountDownLatch responseLatch;
//    private volatile boolean gotResponse;
//    private volatile String serverResponse;
//    private ArrayList<MessageData> messageDataList;
//
//    public MSGSenderThread(String url, BlockingQueue<ChatMessage> queue,
//                           int messages, CountDownLatch latch, int id,
//                           ArrayList<MessageData> dataList) {
//        this.serverUrl = url;
//        this.queue = queue;
//        this.messagesToSend = messages;
//        this.latch = latch;
//        this.ThreadNumber = id;
//        this.roomId = (id % 20) + 1;
//        this.messageDataList = dataList;    }
//
//    // Callbacks
//    public void onSuccess() {}
//    public void onFail() {}
//    public void onConnect() {}
//    public void onReconnect() {}
//
//    @Override
//    public void run() {
//        try {
//            if (!connect()) {
//                System.err.println("Thread-" + ThreadNumber + ": Failed to connect");
//                return;
//            }
//
//            int sent = 0;
//            while (sent < messagesToSend) {
//                ChatMessage msg = queue.poll(5, TimeUnit.SECONDS);
//
//                if (msg == null) {
//                    break;
//                }
//
//                // Send and get data
//                MessageData data = sendMessageAndWait(msg);
//
//                if (data != null && data.latency >= 0) {
//                    onSuccess();
//                    synchronized(messageDataList) {
//                        messageDataList.add(data);
//                    }
//                } else {
//                    onFail();
//                }
//
//                sent++;
//            }
//
//            System.out.println("Thread " + ThreadNumber + ": Sent " + sent + " messages");
//
//        } catch (Exception e) {
//            System.err.println("Thread-" + ThreadNumber + " error: " + e.getMessage());
//        } finally {
//            cleanup();
//            latch.countDown();
//        }
//    }
//
//    private boolean connect() {
//        for (int attempt = 1; attempt <= 3; attempt++) {
//            try {
//                String url = serverUrl + "/chat/" + roomId;
//                System.out.println("Thread-" + ThreadNumber + " (Attempt " + attempt + "): Connecting to " + url);
//                URI uri = new URI(url);
////                client = new WebSocketClient(uri) {
////                    @Override
////                    public void onOpen(ServerHandshake handshake) {
////                        connected = true;
////                        onConnect();
////                    }
////
////                    @Override
////                    public void onMessage(String message) {
////                        gotResponse = true;
////                        serverResponse = message;
////                        if (responseLatch != null) {
////                            responseLatch.countDown();
////                        }
////                    }
////
////                    @Override
////                    public void onClose(int code, String reason, boolean remote) {
////                        connected = false;
////                    }
////
////                    @Override
////                    public void onError(Exception ex) {
////                        connected = false;
////                    }
////                };
//                client = new WebSocketClient(uri) {
//                    @Override
//                    public void onOpen(ServerHandshake handshake) {
//                        connected = true;
//                        onConnect();
//                        System.out.println("✅ Thread-" + ThreadNumber + ": Connected to " + uri);
//                    }
//
//                    @Override
//                    public void onMessage(String message) {
//                        gotResponse = true;
//                        serverResponse = message;
//                        if (responseLatch != null) {
//                            responseLatch.countDown();
//                        }
//                    }
//
//                    @Override
//                    public void onClose(int code, String reason, boolean remote) {
//                        connected = false;
//                        if (code != 1000) {  // 1000 = normal close
//                            System.err.println("❌ Thread-" + ThreadNumber + ": Connection closed - Code: " + code + ", Reason: " + reason);
//                        }
//                    }
//
//                    @Override
//                    public void onError(Exception ex) {
//                        connected = false;
//                        System.err.println("❌ Thread-" + ThreadNumber + ": WebSocket error: " + ex.getClass().getName() + " - " + ex.getMessage());
//                        if (ex.getCause() != null) {
//                            System.err.println("   Cause: " + ex.getCause().getMessage());
//                        }
//                    }
//                };
////                client.connectBlocking(15, TimeUnit.SECONDS);
////
////                if (connected) {
////                    if (attempt > 1) {
////                        onReconnect();
////                    }
//////                    sendJoinMessage();
////                    return true;
////                }
//                System.out.println("Thread-" + ThreadNumber + ": Attempting connection...");
//                boolean connectResult = client.connectBlocking(15, TimeUnit.SECONDS);
//                System.out.println("Thread-" + ThreadNumber + ": connectBlocking returned: " + connectResult + ", connected flag: " + connected);
//
//                if (connected) {
//                    System.out.println("✅ Thread-" + ThreadNumber + ": Successfully connected!");
//                    if (attempt > 1) {
//                        onReconnect();
//                    }
//                    return true;
//                } else {
//                    System.err.println("❌ Thread-" + ThreadNumber + ": Connection failed on attempt " + attempt);
//                }
//
//            } catch (Exception e) {
//                if (attempt < 3) {
//                    try {
//                        Thread.sleep(50);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        return false;
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    private void sendJoinMessage() {
//        ChatMessage join = new ChatMessage(
//                ThreadNumber,
//                "user" + ThreadNumber,
//                "Joining",
//                java.time.Instant.now().toString(),
//                "JOIN"
//        );
//        sendMessageAndWait(join);
//    }
//
//    /**
//     * Send and measure latency - returns latency in ms or -1 if failed
//     */
//    private MessageData sendMessageAndWait(ChatMessage msg) {
//        // Retry up to 5 times
//        for (int attempt = 0; attempt < Retries; attempt++) {
//            try {
//                // Check connection
//                if (!connected) {
//                    if (!connect()) {
//                        return null;
//                    }
//                    onReconnect();
//                }
//
//                // Prepare to wait for response
//                responseLatch = new CountDownLatch(1);
//                gotResponse = false;
//                serverResponse = null;
//
//                // Record START timestamp
//                long timestamp = System.currentTimeMillis();
//
//                // Send message
//                client.send(msg.toJson());
//
//                // Wait for server response
//                boolean got = responseLatch.await(
//                        Timeout_Response,
//                        TimeUnit.MILLISECONDS
//                );
//
//                // Calculate latency
//                long latency = System.currentTimeMillis() - timestamp;
//
//                // Check if got response
//                if (got && gotResponse) {
//                    // Extract status from server response
//                    String status = "OK";
//                    if (serverResponse != null && serverResponse.contains("ERROR")) {
//                        status = "ERROR";
//                    }
//
//                    // Return complete data object
//                    return new MessageData(
//                            timestamp,
//                            msg.getMessageType(),
//                            latency,
//                            status,
//                            roomId
//                    );
//                }
//
//                // No response - will retry
//
//            } catch (Exception e) {
//                // Error occurred - retry with backoff
//                if (attempt < Retries - 1) {
//                    try {
//                        Thread.sleep((long) Math.pow(2, attempt) * 40);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        return null;
//                    }
//                }
//            }
//        }
//
//        return null;  // Failed after all retries
//    }
//
//    private void cleanup() {
//        if (client != null) {
//            try {
//                client.closeBlocking();
//            } catch (Exception e) {}
//        }
//    }
//}
//package client2;
//
//import client2.model.ChatMessage;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//import java.net.URI;
//import java.util.ArrayList;
//import java.util.concurrent.*;
//
///**
// * OPTIMIZED: Accurate messageId-based tracking
// */
//public class MSGSenderThread implements Runnable {
//
//    private static final int MAX_RETRIES = 3;
//    private static final int RESPONSE_TIMEOUT = 100;  // 100ms (was 1000ms!)
//
//    private String serverUrl;
//    private BlockingQueue<ChatMessage> queue;
//    private int messagesToSend;
//    private CountDownLatch latch;
//    private int ThreadNumber;
//
//    private WebSocketClient client;
//    private volatile boolean connected = false;
//    private int roomId;
//
//    // ========== NEW: Track pending messages by messageId ==========
//    private ConcurrentHashMap<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
//    // ==============================================================
//
//    private ArrayList<MessageData> messageDataList;
//
//    public MSGSenderThread(String url, BlockingQueue<ChatMessage> queue,
//                           int messages, CountDownLatch latch, int id,
//                           ArrayList<MessageData> dataList) {
//        this.serverUrl = url;
//        this.queue = queue;
//        this.messagesToSend = messages;
//        this.latch = latch;
//        this.ThreadNumber = id;
//        this.roomId = (id % 20) + 1;
//        this.messageDataList = dataList;
//    }
//
//    // Callbacks
//    public void onSuccess() {}
//    public void onFail() {}
//    public void onConnect() {}
//    public void onReconnect() {}
//
//    // ========== NEW: Track pending message info ==========
//    static class PendingMessage {
//        long sendTime;
//        String messageType;
//
//        PendingMessage(long sendTime, String messageType) {
//            this.sendTime = sendTime;
//            this.messageType = messageType;
//        }
//    }
//    // ====================================================
//
//    @Override
//    public void run() {
//        try {
//            if (!connect()) {
//                System.err.println("Thread-" + ThreadNumber + ": Failed to connect");
//                return;
//            }
//
//            int sent = 0;
//            int acknowledged = 0;
//            long startTime = System.currentTimeMillis();
//
//            while (sent < messagesToSend) {
//                ChatMessage msg = queue.poll(100, TimeUnit.MILLISECONDS);
//
//                if (msg == null) {
//                    continue;
//                }
//
//                // Send message (fire and forget style, but track it)
//                if (sendMessage(msg)) {
//                    sent++;
//                } else {
//                    onFail();
//                }
//
//                // Progress every 50 messages
//                if (sent % 50 == 0) {
//                    acknowledged = countAcknowledged();
//                    long elapsed = System.currentTimeMillis() - startTime;
//                    double rate = (sent * 1000.0) / elapsed;
//                    System.out.println("Thread-" + ThreadNumber + ": " + sent + "/" + messagesToSend +
//                            " sent, " + acknowledged + " ACK'd (" +
//                            String.format("%.0f", rate) + " msg/sec)");
//                }
//            }
//
//            // Wait a bit for remaining ACKs
//            Thread.sleep(2000);
//
//            acknowledged = countAcknowledged();
//            long elapsed = System.currentTimeMillis() - startTime;
//            double rate = (sent * 1000.0) / elapsed;
//
//            System.out.println("✅ Thread-" + ThreadNumber + " COMPLETE: " +
//                    sent + " sent, " + acknowledged + " ACK'd in " + elapsed + "ms (" +
//                    String.format("%.0f", rate) + " msg/sec)");
//
//        } catch (Exception e) {
//            System.err.println("❌ Thread-" + ThreadNumber + " error: " + e.getMessage());
//        } finally {
//            cleanup();
//            latch.countDown();
//        }
//    }
//
//    private boolean connect() {
//        // Stagger connections to avoid overwhelming ALB
//        try {
//            Thread.sleep(ThreadNumber * 20);  // 20ms per thread
//        } catch (InterruptedException e) {
//            return false;
//        }
//
//        for (int attempt = 1; attempt <= 5; attempt++) {  // Increased from 3 to 5
//            try {
//                String url = serverUrl + "/chat/" + roomId;
//                URI uri = new URI(url);
//
//                client = new WebSocketClient(uri) {
//                    @Override
//                    public void onOpen(ServerHandshake handshake) {
//                        connected = true;
//                        onConnect();
//                    }
//
//                    @Override
//                    public void onMessage(String message) {
//                        handleServerResponse(message);
//                    }
//
//                    @Override
//                    public void onClose(int code, String reason, boolean remote) {
//                        connected = false;
//                    }
//
//                    @Override
//                    public void onError(Exception ex) {
//                        connected = false;
//                    }
//                };
//
//                boolean connectResult = client.connectBlocking(30, TimeUnit.SECONDS);  // Increased timeout
//
//                if (connected && connectResult) {
//                    if (attempt > 1) {
//                        onReconnect();
//                    }
//                    return true;
//                }
//
//            } catch (Exception e) {
//                if (attempt < 5) {
//                    try {
//                        Thread.sleep(200 * attempt);  // Longer backoff
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        return false;
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    /**
//     * Send message and track it (no waiting!)
//     */
//    private boolean sendMessage(ChatMessage msg) {
//        for (int retry = 0; retry < MAX_RETRIES; retry++) {
//            try {
//                if (!connected) {
//                    if (!connect()) {
//                        return false;
//                    }
//                    onReconnect();
//                }
//
//                // Record send time and track by messageId
//                long sendTime = System.currentTimeMillis();
//                String messageId = msg.getMessageId();
//
//                pendingMessages.put(messageId, new PendingMessage(sendTime, msg.getMessageType()));
//
//                // Send (fire and forget!)
//                client.send(msg.toJson());
//
//                return true;
//
//            } catch (Exception e) {
//                connected = false;
//
//                if (retry < MAX_RETRIES - 1) {
//                    try {
//                        Thread.sleep(20);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        return false;
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    /**
//     * Handle server response - match by messageId
//     */
//    private void handleServerResponse(String response) {
//        try {
//            // Parse server response
//            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
//
//            // Check if response contains messageId
//            if (!json.has("messageId")) {
//                return;  // Not a message ACK
//            }
//
//            String messageId = json.get("messageId").getAsString();
//
//            // Find pending message
//            PendingMessage pending = pendingMessages.remove(messageId);
//
//            if (pending != null) {
//                // Calculate latency
//                long latency = System.currentTimeMillis() - pending.sendTime;
//
//                // Check status
//                String status = "OK";
//                if (json.has("status") && json.get("status").getAsString().equals("ERROR")) {
//                    status = "ERROR";
//                    onFail();
//                } else {
//                    onSuccess();  // ← Only count when THIS specific message ACK'd!
//                }
//
//                // Record metrics
//                MessageData data = new MessageData(
//                        pending.sendTime,
//                        pending.messageType,
//                        latency,
//                        status,
//                        roomId
//                );
//
//                synchronized(messageDataList) {
//                    messageDataList.add(data);
//                }
//            }
//
//        } catch (Exception e) {
//            // Ignore malformed responses
//        }
//    }
//
//    /**
//     * Count how many messages were acknowledged
//     */
//    private int countAcknowledged() {
//        return messageDataList.size();
//    }
//
//    private void cleanup() {
//        if (client != null) {
//            try {
//                client.closeBlocking();
//            } catch (Exception e) {}
//        }
//    }
//}