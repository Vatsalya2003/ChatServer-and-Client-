package server.configure;

import server.model.ChatMessage;
import server.model.MessageQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.InetSocketAddress;
import java.util.UUID;
import server.service.PublicerSQS;

/**
 * class ChatServerWebSocketHandler a handler for chat rooms
 */

@Component
public class
ChatServerWSHandler implements WebSocketHandler {
    //converts Json String to java objects for parsing incoming msg from clients
    private final ObjectMapper objectMapperMSG = new ObjectMapper();
    //validator for objects
    private final Validator validator;
    //ConcurrentHashMap Data Structure for storing chatroom "id" and " Sessions".
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> chatRooms =
            new ConcurrentHashMap<>();
    // for tracking of Joined Sessions
    private final Set<WebSocketSession> joinedSessions =
            ConcurrentHashMap.newKeySet();
    //active user tracking
    private final ConcurrentHashMap<String, UserInfo> activeUsers = new ConcurrentHashMap<>();

    public static class UserInfo {
        private String userId;
        private String username;
        private String roomId;
        private long joinedAt;

        public UserInfo(String userId, String username, String roomId, long joinedAt) {
            this.userId = userId;
            this.username = username;
            this.roomId = roomId;
            this.joinedAt = joinedAt;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRoomId() { return roomId; }
        public long getJoinedAt() { return joinedAt; }
    }
    //SQS Publicer
    @Autowired
    private PublicerSQS sqsPublisher;
    //server indentifier which generates unique ID for server instance
    private final String serverId = UUID.randomUUID().toString().substring(0, 8);

    public ChatServerWSHandler(Validator validator) {
        this.validator = validator;
    }

    //Established TCP Connection
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("---->>>>New connection to room: " + getRoomId(session));
    }

    //Handles Message
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {

        try {
            ChatMessage chatMessage = objectMapperMSG.readValue(
                    message.getPayload().toString(), ChatMessage.class);

            // validator checks all @annotation and validates values
            Set<ConstraintViolation<ChatMessage>> violations = validator.validate(chatMessage);
            if (!violations.isEmpty()) {
                sendResponse(session, "ERROR",
                        "Validation failed: " + violations.iterator().next().getMessage());
                return;
            }

            String roomId = getRoomId(session);
            //give formatted Message and Handles JOIN, LEAVE, TEXT type.
            String formattedMessage = chatMessageTypeProcess(session, roomId, chatMessage);

            //Create a Message to put it in Queue.
            if (formattedMessage != null) {
                MessageQueue queueMsg = new MessageQueue(
                        UUID.randomUUID().toString(),
                        roomId,
                        String.valueOf(chatMessage.getUserId()),
                        chatMessage.getUsername(),
                        chatMessage.getMessage(),
                        Instant.now().toString(),
                        chatMessage.getMessageType(),
                        serverId,
                        getClientIp(session)
                );
                //Check Circuit breaker, gets queue url converts MessageQueue object to JSON
                sqsPublisher.publishMessage(queueMsg);
                sendResponse(session, "OK", "Message published to queue");
            }

        } catch (Exception e) {
            sendResponse(session, "ERROR", "Error: " + e.getMessage());
        }
    }

    private String chatMessageTypeProcess(WebSocketSession session, String roomId, ChatMessage chatMessage) throws IOException {
        switch (chatMessage.getMessageType()) {
            case "JOIN":
                if (joinedSessions.contains(session)) {
                    sendResponse(session, "ERROR", "Already joined");
                    return null;
                }
                chatRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(session);
                joinedSessions.add(session);

                activeUsers.put(session.getId(), new UserInfo(
                        String.valueOf(chatMessage.getUserId()),
                        chatMessage.getUsername(),
                        roomId,
                        System.currentTimeMillis()
                ));

                return chatMessage.getUsername() + " joined the room";

            case "LEAVE":
                if (!joinedSessions.contains(session)) {
                    sendResponse(session, "ERROR", "You must JOIN before LEAVE");
                    return null;
                }
                CopyOnWriteArrayList<WebSocketSession> roomSessions = chatRooms.get(roomId);
                if (roomSessions != null) {
                    roomSessions.remove(session);
                }
                joinedSessions.remove(session);

                activeUsers.remove(session.getId());

                return chatMessage.getUsername() + " left the room";

            case "TEXT":
                if (!joinedSessions.contains(session)) {
                    sendResponse(session, "ERROR", "You must JOIN before sending TEXT");
                    return null;
                }
                return chatMessage.getUsername() + ": " + chatMessage.getMessage();

            default:
                sendResponse(session, "ERROR", "Unknown message type");
                return null;
        }
    }

    //Build response map for server
    private void sendResponse(WebSocketSession session, String status, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", status);
            response.put("serverTimestamp", Instant.now().toString());
            response.put("message", message);

            //Convert to JSON string
            String jsonResponse = objectMapperMSG.writeValueAsString(response);

            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(jsonResponse));
                }
            }
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("Transport error: " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String roomId = getRoomId(session);

        CopyOnWriteArrayList<WebSocketSession> sessions = chatRooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                chatRooms.remove(roomId);
            }
        }

        joinedSessions.remove(session);
        activeUsers.remove(session.getId());
        System.out.println("<<<---Connection closed from room: " + roomId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    //Return room ID
    private String getRoomId(WebSocketSession session) {
        return Objects.requireNonNull(session.getUri()).getPath().split("/chat/")[1];
    }

    //Return IP of Clients
    private String getClientIp(WebSocketSession session) {
        InetSocketAddress addr = session.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    /**
     * Broadcast message to all clients in a room
     * Called by BroadcastController when consumer sends REST request
     *
     * @param roomId The room to broadcast to
     * @param queueMsg The message from SQS queue
     * @return Number of clients message was sent to
     */
    public int broadcastToRoom(String roomId, MessageQueue queueMsg) {
        CopyOnWriteArrayList<WebSocketSession> sessions = chatRooms.get(roomId);

        if (sessions == null || sessions.isEmpty()) {
//            System.out.println(" No clients in room " + roomId + " to broadcast to");
            return 0;
        }

        try {
            // Create broadcast message
            Map<String, Object> broadcastMsg = new HashMap<>();
            broadcastMsg.put("messageType", queueMsg.getMessageType());
            broadcastMsg.put("username", queueMsg.getUsername());
            broadcastMsg.put("message", queueMsg.getMessage());
            broadcastMsg.put("timestamp", queueMsg.getTimestamp());
            broadcastMsg.put("roomId", roomId);
            broadcastMsg.put("userId", queueMsg.getUserId());

            String jsonMessage = objectMapperMSG.writeValueAsString(broadcastMsg);
            TextMessage textMessage = new TextMessage(jsonMessage);

            int successCount = 0;

            // Broadcast to all sessions in room
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                        successCount++;
                    } catch (IOException e) {
//                        System.err.println("!!!!! Failed to broadcast to session: " + e.getMessage());
                    }
                }
            }

//            System.out.println("✓ Broadcasted to " + successCount + " clients in room " + roomId);
            return successCount;

        } catch (Exception e) {
//            System.err.println("!!!! Broadcast error in room " + roomId + ": " + e.getMessage());
            return 0;
        }
    }
}