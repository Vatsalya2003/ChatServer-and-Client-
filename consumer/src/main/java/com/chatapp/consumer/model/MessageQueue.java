package com.chatapp.consumer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message format from SQS queue (Assignment 2)
 * Contains all fields published by the server
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageQueue {
    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private String messageType;
    private String serverId;
    private String clientIp;

    // Default constructor (required for Jackson JSON parsing)
    public MessageQueue() {}

    // Full constructor
    public MessageQueue(String messageId, String roomId, String userId, String username,
                        String message, String timestamp, String messageType,
                        String serverId, String clientIp) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.serverId = serverId;
        this.clientIp = clientIp;
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    @Override
    public String toString() {
        return "MessageQueue{" +
                "messageId='" + messageId + '\'' +
                ", roomId='" + roomId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", message='" + message + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", messageType='" + messageType + '\'' +
                ", serverId='" + serverId + '\'' +
                ", clientIp='" + clientIp + '\'' +
                '}';
    }
}