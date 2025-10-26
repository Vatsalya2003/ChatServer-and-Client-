package server.configure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import server.service.PublicerSQS;
import java.util.*;

/**
 *
 */
@RestController
public class ServerStatus {

    @Autowired
    private PublicerSQS publicerSQS;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> serverStatus() {
        return ResponseEntity.ok(Map.of("status", "RUNNING"));
    }

    @GetMapping("/sqs-status")
    public ResponseEntity<Map<String, Object>> sqsStatus() {
        return ResponseEntity.ok(publicerSQS.getStatus());
    }
}