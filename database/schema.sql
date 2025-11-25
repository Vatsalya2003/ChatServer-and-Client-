-- CS6650 Assignment 3: Database Schema
-- Author: Vatsalya Dabhi
-- Date: November 24, 2025

CREATE DATABASE IF NOT EXISTS chatServerDB;
USE chatServerDB;

CREATE TABLE messages (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          message_id VARCHAR(36) UNIQUE NOT NULL,
                          room_id INT NOT NULL,
                          user_id INT NOT NULL,
                          username VARCHAR(20) NOT NULL,
                          message TEXT NOT NULL,
                          timestamp DATETIME(3) NOT NULL,
                          message_type VARCHAR(10) NOT NULL,
                          server_id VARCHAR(10),
                          client_ip VARCHAR(45),
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                          INDEX idx_room_timestamp (room_id, timestamp DESC),
                          INDEX idx_user_timestamp (user_id, timestamp DESC),
                          INDEX idx_timestamp (timestamp DESC),
                          INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;