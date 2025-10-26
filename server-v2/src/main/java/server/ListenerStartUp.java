package server;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import server.service.PublicerSQS;

/**
 * Tests SQS connection on startup
 */
@Component
public class ListenerStartUp {

    private final PublicerSQS publicerSQS;

    public ListenerStartUp(PublicerSQS publicerSQS) {
        this.publicerSQS = publicerSQS;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        System.out.println("\n" + "-------------------------");
        System.out.println("Testing SQS Connection...");
        publicerSQS.testConnection();
        System.out.println("-------------------------");
    }
}