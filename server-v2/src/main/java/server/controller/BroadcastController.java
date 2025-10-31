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

@RestController
@RequestMapping("/api")
public class BroadcastController {
    @Autowired
    private ChatServerWSHandler wsHandler;

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

    @GetMapping("/broadcast/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}