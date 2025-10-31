//package com.chatapp.consumer.config;
//
//import com.chatapp.consumer.handler.ConsumerWSHandler;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.socket.config.annotation.*;
//
///**
// * WebSocket Configuration for Consumer
// * Maps /room/{roomId} endpoint to ConsumerWebSocketHandler
// * Clients connect here to receive broadcasts
// */
//@Configuration
//@EnableWebSocket
//public class ConfigureWebSocket implements WebSocketConfigurer {
//
//    private final ConsumerWSHandler handler;
//
//    /**
//     * Constructor - Spring injects ConsumerWebSocketHandler
//     */
//    public ConfigureWebSocket(ConsumerWSHandler handler) {
//        this.handler = handler;
//    }
//
//    /**
//     * Register WebSocket handlers
//     * Endpoint: ws://consumer:8081/room/{roomId}
//     * Example: ws://consumer:8081/room/1 (for room 1)
//     */
//    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        registry.addHandler(handler, "/room/{roomId}")
//                .setAllowedOrigins("*");  // Allow connections from any origin
//    }
//}