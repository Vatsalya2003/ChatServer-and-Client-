//package server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import server.model.MessageQueue;
//import server.configure.ChatServerWSHandler;
//import java.time.Instant;
//import java.util.*;
//
///**
// * REST endpoint for consumer to request broadcasts
// */
//@RestController
//@RequestMapping("/api")
//public class BroadcastController {
//
//    @Autowired
//    private ChatServerWSHandler wsHandler;
//
//    @PostMapping("/broadcast")
//    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody MessageQueue message) {
//        try {
//            int clientCount = wsHandler.broadcastToRoom(message.getRoomId(), message);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("roomId", message.getRoomId());
//            response.put("clientCount", clientCount);
//            response.put("messageId", message.getMessageId());
//
//            System.out.println("✓ Broadcast via API - Room " + message.getRoomId() +
//                    " to " + clientCount + " clients");
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> error = new HashMap<>();
//            error.put("success", false);
//            error.put("error", e.getMessage());
//
//            return ResponseEntity.internalServerError().body(error);
//        }
//    }
//
//    @GetMapping("/broadcast/health")
//    public ResponseEntity<Map<String, String>> health() {
//        return ResponseEntity.ok(Map.of("status", "UP"));
//    }
//}

package server.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import server.model.MessageQueue;
import server.configure.ChatServerWSHandler;
import java.time.Instant;
import java.util.*;

/**
 * REST API controller for message broadcasting operations.
 */
 @RestController
@RequestMapping("/api")
public class BroadcastController {
    @Autowired
    private ChatServerWSHandler wsHandler;

    /**
     * Broadcasts a single message to all clients in the specified room
     * @param message The MessageQueue object containing message content and metadata
     * @return ResponseEntity with broadcast status
     * @throws Error if broadcast operation fails
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody MessageQueue message) {
        try {
            int clientCount = wsHandler.broadcastToRoom(message.getRoomId(), message);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("roomId", message.getRoomId());
            response.put("clientCount", clientCount);
            response.put("messageId", message.getMessageId());
            System.out.println("✓ Broadcast via API - Room " + message.getRoomId() +
                    " to " + clientCount + " clients");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }


        /**
         * Broadcasts a batch of messages to their respective rooms.
         * @param messages List of MessageQueue objects to broadcast, typically 10 messages per batch
         * @return ResponseEntity with batch processing status
         * @throws Error if broadcast operation fails
             */
    @PostMapping("/broadcast/batch")
    public ResponseEntity<Map<String, Object>> broadcastBatch(@RequestBody List<MessageQueue> messages) {
        try {
            int totalClients = 0;

            for (MessageQueue message : messages) {
                totalClients += wsHandler.broadcastToRoom(message.getRoomId(), message);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messagesProcessed", messages.size());
            response.put("totalClientCount", totalClients);

//            System.out.println("✓ Batch broadcast - " + successCount + " messages to " +
//                    totalClients + " total clients");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
        * Health check endpoint for the broadcast service.
     */
     @GetMapping("/broadcast/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}