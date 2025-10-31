//package com.chatapp.consumer.handler;
//
//
//import com.chatapp.consumer.manager.RoomManager;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.*;
//
//import java.util.Objects;
//
///**
// * WebSocket handler for consumer application
// * Accepts client connections and manages them in RoomManager
// * Clients connect here to RECEIVE broadcasts (they don't send messages to consumer)
// */
//@Component
//public class ConsumerWSHandler implements WebSocketHandler {
//
//    private final RoomManager roomManager;
//
//    /**
//     * Constructor - Spring injects RoomManager
//     */
//    public ConsumerWSHandler(RoomManager roomManager) {
//        this.roomManager = roomManager;
//    }
//
//    /**
//     * Called when a client connects to the consumer
//     * Automatically adds the session to the appropriate room
//     */
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) {
//        String roomId = getRoomId(session);
//        roomManager.addSessionToRoom(roomId, session);
//        System.out.println("✓ Consumer: New client connected to room " + roomId +
//                " (Total in room: " + roomManager.getSessionCount(roomId) + ")");
//    }
//
//    /**
//     * Handle incoming messages from clients
//     * NOTE: Consumer doesn't expect messages from clients!
//     * Clients only RECEIVE broadcasts, they don't send to consumer
//     * If a message is received, it's unexpected (log it)
//     */
//    @Override
//    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
//        String roomId = getRoomId(session);
//        System.out.println("⚠ Consumer received unexpected message from client in room " + roomId);
//        System.out.println("   Message: " + message.getPayload());
//
//        // Consumer is for broadcasting only - clients shouldn't send messages here
//        // They should send to the SERVER (/chat endpoint), not consumer
//    }
//
//    /**
//     * Handle WebSocket transport errors
//     */
//    @Override
//    public void handleTransportError(WebSocketSession session, Throwable exception) {
//        String roomId = getRoomId(session);
//        System.err.println("✗ Transport error in room " + roomId + ": " + exception.getMessage());
//    }
//
//    /**
//     * Called when a client disconnects from the consumer
//     * Automatically removes the session from RoomManager
//     */
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
//        String roomId = getRoomId(session);
//        roomManager.removeSessionFromRoom(roomId, session);
//        System.out.println("✓ Consumer: Client disconnected from room " + roomId +
//                " (Reason: " + closeStatus.getReason() + ")");
//    }
//
//    /**
//     * Indicates this handler does not support partial messages
//     */
//    @Override
//    public boolean supportsPartialMessages() {
//        return false;
//    }
//
//    /**
//     * Extract roomId from WebSocket session URI
//     * Example: ws://consumer:8081/room/5 → returns "5"
//     */
//    private String getRoomId(WebSocketSession session) {
//        return Objects.requireNonNull(session.getUri()).getPath().split("/room/")[1];
//    }
//}