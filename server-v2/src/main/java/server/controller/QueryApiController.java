package server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Query API for Assignment 3 - Database Queries
 * Implements all 4 core queries + analytics queries
 */
@RestController
@RequestMapping("/api/query")
public class QueryApiController {

    @Autowired
    private DataSource dataSource;

    // ============================================
    // CORE QUERY 1: Get messages for room in time range
    // Performance target: < 100ms for 1000 messages
    // ============================================
    @GetMapping("/room/{roomId}/messages")
    public ResponseEntity<Map<String, Object>> getRoomMessages(
            @PathVariable int roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1000") int limit) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT message_id, room_id, user_id, username, message, " +
                "timestamp, message_type, server_id, client_ip " +
                "FROM messages " +
                "WHERE room_id = ? AND timestamp BETWEEN ? AND ? " +
                "ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, roomId);
            stmt.setTimestamp(2, Timestamp.valueOf(startTime));
            stmt.setTimestamp(3, Timestamp.valueOf(endTime));
            stmt.setInt(4, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("messageId", rs.getString("message_id"));
                msg.put("roomId", rs.getInt("room_id"));
                msg.put("userId", rs.getInt("user_id"));
                msg.put("username", rs.getString("username"));
                msg.put("message", rs.getString("message"));
                msg.put("timestamp", rs.getTimestamp("timestamp"));
                msg.put("messageType", rs.getString("message_type"));
                msg.put("serverId", rs.getString("server_id"));
                msg.put("clientIp", rs.getString("client_ip"));
                messages.add(msg);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("roomId", roomId);
            response.put("startTime", startTime);
            response.put("endTime", endTime);
            response.put("messageCount", messages.size());
            response.put("messages", messages);
            response.put("queryTimeMs", queryTime);
            response.put("performanceTarget", "< 100ms");
            response.put("targetMet", queryTime < 100);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // CORE QUERY 2: Get user's message history
    // Performance target: < 200ms
    // ============================================
    @GetMapping("/user/{userId}/history")
    public ResponseEntity<Map<String, Object>> getUserHistory(
            @PathVariable int userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "100") int limit) {

        long startQuery = System.currentTimeMillis();

        String sql;
        if (startTime != null && endTime != null) {
            sql = "SELECT message_id, room_id, user_id, username, message, " +
                    "timestamp, message_type FROM messages " +
                    "WHERE user_id = ? AND timestamp BETWEEN ? AND ? " +
                    "ORDER BY timestamp DESC LIMIT ?";
        } else {
            sql = "SELECT message_id, room_id, user_id, username, message, " +
                    "timestamp, message_type FROM messages " +
                    "WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?";
        }

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            if (startTime != null && endTime != null) {
                stmt.setTimestamp(2, Timestamp.valueOf(startTime));
                stmt.setTimestamp(3, Timestamp.valueOf(endTime));
                stmt.setInt(4, limit);
            } else {
                stmt.setInt(2, limit);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("messageId", rs.getString("message_id"));
                msg.put("roomId", rs.getInt("room_id"));
                msg.put("username", rs.getString("username"));
                msg.put("message", rs.getString("message"));
                msg.put("timestamp", rs.getTimestamp("timestamp"));
                msg.put("messageType", rs.getString("message_type"));
                messages.add(msg);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("messageCount", messages.size());
            response.put("messages", messages);
            response.put("queryTimeMs", queryTime);
            response.put("performanceTarget", "< 200ms");
            response.put("targetMet", queryTime < 200);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // CORE QUERY 3: Count active users in time window
    // Performance target: < 500ms
    // ============================================
    @GetMapping("/stats/active-users")
    public ResponseEntity<Map<String, Object>> countActiveUsers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT COUNT(DISTINCT user_id) as active_users FROM messages " +
                "WHERE timestamp BETWEEN ? AND ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(startTime));
            stmt.setTimestamp(2, Timestamp.valueOf(endTime));

            ResultSet rs = stmt.executeQuery();

            int activeUsers = 0;
            if (rs.next()) {
                activeUsers = rs.getInt("active_users");
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("startTime", startTime);
            response.put("endTime", endTime);
            response.put("activeUsers", activeUsers);
            response.put("queryTimeMs", queryTime);
            response.put("performanceTarget", "< 500ms");
            response.put("targetMet", queryTime < 500);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // CORE QUERY 4: Get rooms user has participated in
    // Performance target: < 50ms
    // ============================================
    @GetMapping("/user/{userId}/rooms")
    public ResponseEntity<Map<String, Object>> getUserRooms(@PathVariable int userId) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT DISTINCT room_id, MAX(timestamp) as last_activity, " +
                "COUNT(*) as message_count FROM messages " +
                "WHERE user_id = ? GROUP BY room_id ORDER BY last_activity DESC";

        List<Map<String, Object>> rooms = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> room = new HashMap<>();
                room.put("roomId", rs.getInt("room_id"));
                room.put("lastActivity", rs.getTimestamp("last_activity"));
                room.put("messageCount", rs.getInt("message_count"));
                rooms.add(room);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("roomCount", rooms.size());
            response.put("rooms", rooms);
            response.put("queryTimeMs", queryTime);
            response.put("performanceTarget", "< 50ms");
            response.put("targetMet", queryTime < 50);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // ANALYTICS QUERY 1: Messages per hour statistics
    // ============================================
    @GetMapping("/analytics/messages-per-hour")
    public ResponseEntity<Map<String, Object>> getMessagesPerHour(
            @RequestParam(defaultValue = "24") int hours) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT DATE_FORMAT(timestamp, '%Y-%m-%d %H:00:00') as hour, " +
                "COUNT(*) as message_count FROM messages " +
                "WHERE timestamp >= DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                "GROUP BY hour ORDER BY hour DESC";

        List<Map<String, Object>> stats = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, hours);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("hour", rs.getString("hour"));
                stat.put("messageCount", rs.getInt("message_count"));
                stats.add(stat);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("hoursAnalyzed", hours);
            response.put("stats", stats);
            response.put("queryTimeMs", queryTime);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // ANALYTICS QUERY 2: Most active users (Top N)
    // ============================================
    @GetMapping("/analytics/top-users")
    public ResponseEntity<Map<String, Object>> getTopUsers(
            @RequestParam(defaultValue = "10") int limit) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT user_id, username, COUNT(*) as message_count, " +
                "COUNT(DISTINCT room_id) as rooms_participated, " +
                "MAX(timestamp) as last_activity FROM messages " +
                "GROUP BY user_id, username " +
                "ORDER BY message_count DESC LIMIT ?";

        List<Map<String, Object>> users = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("userId", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("messageCount", rs.getInt("message_count"));
                user.put("roomsParticipated", rs.getInt("rooms_participated"));
                user.put("lastActivity", rs.getTimestamp("last_activity"));
                users.add(user);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("topUsers", users);
            response.put("queryTimeMs", queryTime);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // ANALYTICS QUERY 3: Most active rooms (Top N)
    // ============================================
    @GetMapping("/analytics/top-rooms")
    public ResponseEntity<Map<String, Object>> getTopRooms(
            @RequestParam(defaultValue = "10") int limit) {

        long startQuery = System.currentTimeMillis();

        String sql = "SELECT room_id, COUNT(*) as message_count, " +
                "COUNT(DISTINCT user_id) as unique_users, " +
                "MAX(timestamp) as last_activity FROM messages " +
                "GROUP BY room_id " +
                "ORDER BY message_count DESC LIMIT ?";

        List<Map<String, Object>> rooms = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> room = new HashMap<>();
                room.put("roomId", rs.getInt("room_id"));
                room.put("messageCount", rs.getInt("message_count"));
                room.put("uniqueUsers", rs.getInt("unique_users"));
                room.put("lastActivity", rs.getTimestamp("last_activity"));
                rooms.add(room);
            }

            long queryTime = System.currentTimeMillis() - startQuery;

            Map<String, Object> response = new HashMap<>();
            response.put("topRooms", rooms);
            response.put("queryTimeMs", queryTime);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // COMPREHENSIVE ANALYTICS: All statistics
    // Call this from client after load test
    // ============================================
    @GetMapping("/analytics/summary")
    public ResponseEntity<Map<String, Object>> getAnalyticsSummary() {

        Map<String, Object> summary = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {

            // Total messages
            PreparedStatement stmt1 = conn.prepareStatement(
                    "SELECT COUNT(*) as total FROM messages");
            ResultSet rs1 = stmt1.executeQuery();
            if (rs1.next()) summary.put("totalMessages", rs1.getInt("total"));

            // Total users
            PreparedStatement stmt2 = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT user_id) as total FROM messages");
            ResultSet rs2 = stmt2.executeQuery();
            if (rs2.next()) summary.put("totalUsers", rs2.getInt("total"));

            // Total rooms
            PreparedStatement stmt3 = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT room_id) as total FROM messages");
            ResultSet rs3 = stmt3.executeQuery();
            if (rs3.next()) summary.put("totalRooms", rs3.getInt("total"));

            // Message type distribution
            PreparedStatement stmt4 = conn.prepareStatement(
                    "SELECT message_type, COUNT(*) as count FROM messages GROUP BY message_type");
            ResultSet rs4 = stmt4.executeQuery();
            Map<String, Integer> typeDistribution = new HashMap<>();
            while (rs4.next()) {
                typeDistribution.put(rs4.getString("message_type"), rs4.getInt("count"));
            }
            summary.put("messageTypeDistribution", typeDistribution);

            // Time range
            PreparedStatement stmt5 = conn.prepareStatement(
                    "SELECT MIN(timestamp) as first, MAX(timestamp) as last FROM messages");
            ResultSet rs5 = stmt5.executeQuery();
            if (rs5.next()) {
                summary.put("firstMessage", rs5.getTimestamp("first"));
                summary.put("lastMessage", rs5.getTimestamp("last"));
            }

            return ResponseEntity.ok(summary);

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ============================================
    // Health check for query API
    // ============================================
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection conn = dataSource.getConnection()) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "Connected"
            ));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
        }
    }
}