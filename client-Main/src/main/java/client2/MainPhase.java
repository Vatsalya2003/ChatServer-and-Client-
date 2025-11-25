package client2;

import client2.model.ChatMessage;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * Class MainPhase for 500000 MSG Test with Analytics
 * mvn exec:java -Dexec.mainClass="client2.MainPhase"
 */
public class MainPhase {

    private static final String Server_Url = "ws://chatapp-ALB-965993072.us-west-2.elb.amazonaws.com:8080";
    private static final int Threads = 256;
    private static final int TotalMessages = 5760000;
    private static final int ThreadMessages = TotalMessages / Threads;

    // Data Variables
    private static int successCount = 0;
    private static int failCount = 0;
    private static int connectionCount = 0;
    private static int reconnectCount = 0;
    private static long startTime = 0;
    private static long endTime = 0;

    //ArrayList to store data for csv
    private static ArrayList<MessageData> allMessageData = new ArrayList<>();

    public static void main(String[] args) {
        runPhase1();
    }

    private static synchronized void addSuccess() {
        successCount++;
    }

    private static synchronized void addFail() {
        failCount++;
    }

    private static synchronized void addConnection() {
        connectionCount++;
    }

    private static synchronized void addReconnect() {
        reconnectCount++;
    }

    private static void runPhase1() {

        System.out.println("Configuration Detail:");
        System.out.println("  Threads: " + Threads);
        System.out.println("  Messages per thread: " + ThreadMessages);
        System.out.println("  Total messages: " + TotalMessages + "\n");

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(100000);
        CountDownLatch latch = new CountDownLatch(Threads);

        Thread msgGenerate = new Thread(new GenerateMessage(queue, TotalMessages));
        msgGenerate.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        startTime = System.currentTimeMillis();

        System.out.println("Starting " + Threads + " threads...\n");

        ExecutorService pool = Executors.newFixedThreadPool(Threads);

        for (int i = 1; i <= Threads; i++) {
            MSGSenderThread thread = new MSGSenderThread(
                    Server_Url, queue, ThreadMessages, latch, i, allMessageData
            ) {
                @Override
                public void onSuccess() {
                    addSuccess();
                }

                @Override
                public void onFail() {
                    addFail();
                }

                @Override
                public void onConnect() {
                    addConnection();
                }

                @Override
                public void onReconnect() {
                    addReconnect();
                }
            };
            pool.submit(thread);
        }

        try {
            System.out.println("Waiting for threads to finish...\n");
            latch.await();
            endTime = System.currentTimeMillis();
            msgGenerate.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
        }

        dataAnalytics();
    }

    private static void dataAnalytics() {
        long totalTime = endTime - startTime;
        double throughput = (successCount * 1000.0) / totalTime;

        System.out.println(" ------------ RESULTS ---------------");
        System.out.println("Successful: " + successCount);
        System.out.println("Failed: " + failCount);
        System.out.println("Runtime: " + totalTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " msg/sec");
        System.out.println("Connections: " + connectionCount);
        System.out.println("Reconnections: " + reconnectCount);

        // Calculating  statistics from latencies
        // Calculate statistics from message data
        if (!allMessageData.isEmpty()) {

            // Extract latencies from MessageData objects
            ArrayList<Long> latencies = new ArrayList<>();
            for (int i = 0; i < allMessageData.size(); i++) {
                latencies.add(allMessageData.get(i).latency);
            }

            // Sort for percentile calculations
            Collections.sort(latencies);

            // Calculate mean
            long sum = 0;
            for (int i = 0; i < latencies.size(); i++) {
                sum += latencies.get(i);
            }
            double mean = (double) sum / latencies.size();

            // Get values
            int size = latencies.size();
            long median = latencies.get(size / 2);
            long p95 = latencies.get((int)(size * 0.95));
            long p99 = latencies.get((int)(size * 0.99));
            long min = latencies.get(0);
            long max = latencies.get(size - 1);

            System.out.println("\n-------- Statistical Analysis ------");
            System.out.println("  Mean: " + String.format("%.2f", mean) + " ms");
            System.out.println("  Median: " + median + " ms");
            System.out.println("  95th percentile: " + p95 + " ms");
            System.out.println("  99th percentile: " + p99 + " ms");
            System.out.println("  Min: " + min + " ms");
            System.out.println("  Max: " + max + " ms");
        }

        //Genearting CSV File For timestamp, msgtypoe, latency, stauscode and roomID
        writeCSV();
        throughputChartCSV();

        // ========== ADD THIS ==========
        // Call metrics API after test
        callMetricsAPI();
    }

    /**
     * CSV File For timestamp, msgtypoe, latency, stauscode and roomID
     */
    private static void writeCSV() {
        try {
            java.io.File dir = new java.io.File("Result");
            if (!dir.exists()) {
                dir.mkdir();
            }

            PrintWriter writer = new PrintWriter(new FileWriter("Result/MessageMetrics.csv"));

            // Header
            writer.println("timestamp,messageType,latency,statusCode,roomId");

            // Data
            for (int i = 0; i < allMessageData.size(); i++) {
                MessageData d = allMessageData.get(i);
                writer.println(d.timestamp + "," +
                        d.messageType + "," +
                        d.latency + "," +
                        d.statusCode + "," +
                        d.roomId);
            }
            writer.close();
        } catch (Exception e) {
            System.err.println("Eroor while Creating CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void throughputChartCSV() {

        if (allMessageData.isEmpty()) {
            System.out.println("NO Data --");
            return;
        }

        try {
            // Find first and last message time
            long firstTime = allMessageData.get(0).timestamp;
            long lastTime = allMessageData.get(0).timestamp;

            for (int i = 0; i < allMessageData.size(); i++) {
                long timestamp = allMessageData.get(i).timestamp;
                if (timestamp < firstTime) firstTime = timestamp;
                if (timestamp > lastTime) lastTime = timestamp;
            }

            // Calculating how many 10 second buckets
            long totalSeconds = (lastTime - firstTime) / 1000;
            int numBuckets = (int)(totalSeconds / 10) + 1;

            // Counting messages in each bucket
            int[] bucketCounts = new int[numBuckets];

            for (int i = 0; i < allMessageData.size(); i++) {
                long elapsed = allMessageData.get(i).timestamp - firstTime;
                int bucketNum = (int)(elapsed / 10000);

                if (bucketNum >= 0 && bucketNum < numBuckets) {
                    bucketCounts[bucketNum]++;
                }
            }

            java.io.File dir = new java.io.File("Result");
            if (!dir.exists()) {
                dir.mkdir();
            }

            PrintWriter writer = new PrintWriter(new FileWriter("Result/Throughput.csv"));
            writer.println("Time_Seconds,Messages_Per_Second");

            for (int i = 0; i < numBuckets; i++) {
                int timeInSeconds = i * 10;
                double messagesPerSec = bucketCounts[i] / 10.0;

                if (bucketCounts[i] > 0) {
                    writer.println(timeInSeconds + "," + messagesPerSec);
                }
            }

            writer.close();
        } catch (Exception e) {
            System.err.println("Error creating throughput CSV: " + e.getMessage());
        }
    }
    /**
     * Call server's metrics API after test completes
     */
    private static void callMetricsAPI() {
        System.out.println("\n========================================");
        System.out.println("  CALLING SERVER METRICS API");
        System.out.println("========================================\n");

        String serverUrl = "http://chatapp-ALB-965993072.us-west-2.elb.amazonaws.com:8080";

        try {
            // Wait for all DB writes to complete
            System.out.println("⏳ Waiting 60 seconds for all database writes to complete...\n");
            Thread.sleep(60000);

            System.out.println("📊 Fetching analytics from server...\n");

            // 1. Analytics Summary
            System.out.println("━".repeat(60));
            System.out.println("1️⃣  ANALYTICS SUMMARY");
            System.out.println("━".repeat(60));
            String summary = makeHttpGet(serverUrl + "/api/query/analytics/summary");
            System.out.println(summary);

            // 2. Top 10 Users
            System.out.println("\n" + "━".repeat(60));
            System.out.println("2️⃣  TOP 10 ACTIVE USERS");
            System.out.println("━".repeat(60));
            String topUsers = makeHttpGet(serverUrl + "/api/query/analytics/top-users?limit=10");
            System.out.println(topUsers);

            // 3. Top 10 Rooms
            System.out.println("\n" + "━".repeat(60));
            System.out.println("3️⃣  TOP 10 ACTIVE ROOMS");
            System.out.println("━".repeat(60));
            String topRooms = makeHttpGet(serverUrl + "/api/query/analytics/top-rooms?limit=10");
            System.out.println(topRooms);

            // 4. Messages Per Hour (Last 24h)
            System.out.println("\n" + "━".repeat(60));
            System.out.println("4️⃣  MESSAGES PER HOUR (Last 24 Hours)");
            System.out.println("━".repeat(60));
            String messagesPerHour = makeHttpGet(serverUrl + "/api/query/analytics/messages-per-hour?hours=24");
            System.out.println(messagesPerHour);

            // 5. Test Core Query 1: Room Messages
            System.out.println("\n" + "━".repeat(60));
            System.out.println("5️⃣  CORE QUERY TEST: Get Room 1 Messages");
            System.out.println("━".repeat(60));

            String now = java.time.Instant.now().toString();
            String hourAgo = java.time.Instant.now().minusSeconds(7200).toString();
            String roomMessages = makeHttpGet(
                    serverUrl + "/api/query/room/1/messages?startTime=" + hourAgo +
                            "&endTime=" + now + "&limit=1000"
            );
            System.out.println(roomMessages);

            // 6. Test Core Query 2: User History
            System.out.println("\n" + "━".repeat(60));
            System.out.println("6️⃣  CORE QUERY TEST: Get User 1 History");
            System.out.println("━".repeat(60));
            String userHistory = makeHttpGet(serverUrl + "/api/query/user/1/history?limit=100");
            System.out.println(userHistory);

            // 7. Test Core Query 3: Active Users Count
            System.out.println("\n" + "━".repeat(60));
            System.out.println("7️⃣  CORE QUERY TEST: Count Active Users");
            System.out.println("━".repeat(60));
            String activeUsers = makeHttpGet(
                    serverUrl + "/api/query/stats/active-users?startTime=" + hourAgo +
                            "&endTime=" + now
            );
            System.out.println(activeUsers);

            // 8. Test Core Query 4: User's Rooms
            System.out.println("\n" + "━".repeat(60));
            System.out.println("8️⃣  CORE QUERY TEST: Get User 1 Rooms");
            System.out.println("━".repeat(60));
            String userRooms = makeHttpGet(serverUrl + "/api/query/user/1/rooms");
            System.out.println(userRooms);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("✅ METRICS API CALLS COMPLETE");
            System.out.println("=".repeat(60));
            System.out.println("⚠️  TAKE SCREENSHOT OF THIS OUTPUT FOR REPORT!");
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("❌ Error calling metrics API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Make HTTP GET request
     */
    private static String makeHttpGet(String urlString) throws Exception {
        java.net.URL url = new java.net.URL(urlString);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            // Pretty print JSON
            return prettyPrintJson(response.toString());
        } else {
            return "❌ ERROR: HTTP " + responseCode;
        }
    }

    /**
     * Pretty print JSON for readability
     */
    private static String prettyPrintJson(String json) {
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            Object obj = gson.fromJson(json, Object.class);
            return gson.toJson(obj);
        } catch (Exception e) {
            return json;  // Return as-is if can't parse
        }
    }
}

//package client2;
//
//import client2.model.ChatMessage;
//import java.io.FileWriter;
//import java.io.PrintWriter;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.concurrent.*;
//
///**
// * OPTIMIZED: Accurate throughput calculation
// */
//public class MainPhase {
//
//    private static final String Server_Url = "ws://chatapp-ALB-965993072.us-west-2.elb.amazonaws.com:8080";
//    private static final int Threads = 128;
//    private static final int TotalMessages = 10000;
//    private static final int ThreadMessages = TotalMessages / Threads;
//
//    // Data Variables
//    private static int successCount = 0;
//    private static int failCount = 0;
//    private static int connectionCount = 0;
//    private static int reconnectCount = 0;
//    private static long startTime = 0;
//    private static long endTime = 0;
//
//    private static ArrayList<MessageData> allMessageData = new ArrayList<>();
//
//    public static void main(String[] args) {
//        runPhase1();
//    }
//
//    private static synchronized void addSuccess() {
//        successCount++;
//    }
//
//    private static synchronized void addFail() {
//        failCount++;
//    }
//
//    private static synchronized void addConnection() {
//        connectionCount++;
//    }
//
//    private static synchronized void addReconnect() {
//        reconnectCount++;
//    }
//
//    private static void runPhase1() {
//
//        System.out.println("========================================");
//        System.out.println("  CHAT CLIENT - OPTIMIZED WITH MESSAGE ID TRACKING");
//        System.out.println("========================================");
//        System.out.println("Configuration:");
//        System.out.println("  Threads: " + Threads);
//        System.out.println("  Messages per thread: " + ThreadMessages);
//        System.out.println("  Total messages: " + TotalMessages);
//        System.out.println("  Server: " + Server_Url);
//        System.out.println("========================================\n");
//
//        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(100000);
//        CountDownLatch latch = new CountDownLatch(Threads);
//
//        Thread msgGenerate = new Thread(new GenerateMessage(queue, TotalMessages));
//        msgGenerate.start();
//
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        System.out.println("🚀 Starting " + Threads + " threads...\n");
//
//        startTime = System.currentTimeMillis();
//
//        ExecutorService pool = Executors.newFixedThreadPool(Threads);
//
//        for (int i = 1; i <= Threads; i++) {
//            MSGSenderThread thread = new MSGSenderThread(
//                    Server_Url, queue, ThreadMessages, latch, i, allMessageData
//            ) {
//                @Override
//                public void onSuccess() {
//                    addSuccess();
//                }
//
//                @Override
//                public void onFail() {
//                    addFail();
//                }
//
//                @Override
//                public void onConnect() {
//                    addConnection();
//                }
//
//                @Override
//                public void onReconnect() {
//                    addReconnect();
//                }
//            };
//            pool.submit(thread);
//        }
//
//        try {
//            System.out.println("⏳ Waiting for threads to finish...\n");
//            latch.await();
//            endTime = System.currentTimeMillis();
//            msgGenerate.join();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        } finally {
//            pool.shutdown();
//        }
//
//        // Wait a bit more for remaining ACKs
//        System.out.println("\n⏳ Waiting 5 seconds for remaining ACKs...");
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        dataAnalytics();
//    }
//
//    private static void dataAnalytics() {
//        long totalTime = endTime - startTime;
//
//        // ========== NEW: Accurate counts ==========
//        int actualAcknowledged = allMessageData.size();
//        int attempted = Threads * ThreadMessages;
//        int missing = attempted - actualAcknowledged;
//        // =========================================
//
//        double sendThroughput = (attempted * 1000.0) / totalTime;
//        double ackThroughput = (actualAcknowledged * 1000.0) / totalTime;
//
//        System.out.println("\n========================================");
//        System.out.println("         TEST RESULTS");
//        System.out.println("========================================");
//        System.out.println("Messages attempted:    " + attempted);
//        System.out.println("Messages ACK'd:        " + actualAcknowledged +
//                " (" + String.format("%.1f%%", (actualAcknowledged * 100.0) / attempted) + ")");
//        System.out.println("Messages missing:      " + missing +
//                " (" + String.format("%.1f%%", (missing * 100.0) / attempted) + ")");
//        System.out.println("Failed sends:          " + failCount);
//        System.out.println("Runtime:               " + totalTime + " ms");
//        System.out.println("Send throughput:       " + String.format("%.2f", sendThroughput) + " msg/sec");
//        System.out.println("ACK throughput:        " + String.format("%.2f", ackThroughput) + " msg/sec");
//        System.out.println("Connections:           " + connectionCount);
//        System.out.println("Reconnections:         " + reconnectCount);
//        System.out.println("========================================");
//
//        // Calculate statistics from message data
//        if (!allMessageData.isEmpty()) {
//
//            // Extract latencies
//            ArrayList<Long> latencies = new ArrayList<>();
//            for (int i = 0; i < allMessageData.size(); i++) {
//                latencies.add(allMessageData.get(i).latency);
//            }
//
//            Collections.sort(latencies);
//
//            long sum = 0;
//            for (int i = 0; i < latencies.size(); i++) {
//                sum += latencies.get(i);
//            }
//            double mean = (double) sum / latencies.size();
//
//            int size = latencies.size();
//            long median = latencies.get(size / 2);
//            long p95 = latencies.get((int)(size * 0.95));
//            long p99 = latencies.get((int)(size * 0.99));
//            long min = latencies.get(0);
//            long max = latencies.get(size - 1);
//
//            System.out.println("\n-------- Latency Analysis ------");
//            System.out.println("  Samples:        " + size);
//            System.out.println("  Mean:           " + String.format("%.2f", mean) + " ms");
//            System.out.println("  Median (p50):   " + median + " ms");
//            System.out.println("  95th percentile: " + p95 + " ms");
//            System.out.println("  99th percentile: " + p99 + " ms");
//            System.out.println("  Min:            " + min + " ms");
//            System.out.println("  Max:            " + max + " ms");
//            System.out.println("========================================");
//        }
//
//        // Generate CSV files
//        writeCSV();
//        throughputChartCSV();
//
//        System.out.println("\n✅ CSV files generated in Result/ folder");
//        System.out.println("========================================");
//    }
//
//    private static void writeCSV() {
//        try {
//            java.io.File dir = new java.io.File("Result");
//            if (!dir.exists()) {
//                dir.mkdir();
//            }
//
//            PrintWriter writer = new PrintWriter(new FileWriter("Result/MessageMetrics.csv"));
//            writer.println("timestamp,messageType,latency,statusCode,roomId");
//
//            for (int i = 0; i < allMessageData.size(); i++) {
//                MessageData d = allMessageData.get(i);
//                writer.println(d.timestamp + "," +
//                        d.messageType + "," +
//                        d.latency + "," +
//                        d.statusCode + "," +
//                        d.roomId);
//            }
//            writer.close();
//        } catch (Exception e) {
//            System.err.println("Error creating CSV: " + e.getMessage());
//        }
//    }
//
//    private static void throughputChartCSV() {
//        if (allMessageData.isEmpty()) {
//            return;
//        }
//
//        try {
//            long firstTime = allMessageData.get(0).timestamp;
//            long lastTime = allMessageData.get(0).timestamp;
//
//            for (int i = 0; i < allMessageData.size(); i++) {
//                long timestamp = allMessageData.get(i).timestamp;
//                if (timestamp < firstTime) firstTime = timestamp;
//                if (timestamp > lastTime) lastTime = timestamp;
//            }
//
//            long totalSeconds = (lastTime - firstTime) / 1000;
//            int numBuckets = (int)(totalSeconds / 10) + 1;
//
//            int[] bucketCounts = new int[numBuckets];
//
//            for (int i = 0; i < allMessageData.size(); i++) {
//                long elapsed = allMessageData.get(i).timestamp - firstTime;
//                int bucketNum = (int)(elapsed / 10000);
//
//                if (bucketNum >= 0 && bucketNum < numBuckets) {
//                    bucketCounts[bucketNum]++;
//                }
//            }
//
//            java.io.File dir = new java.io.File("Result");
//            if (!dir.exists()) {
//                dir.mkdir();
//            }
//
//            PrintWriter writer = new PrintWriter(new FileWriter("Result/Throughput.csv"));
//            writer.println("Time_Seconds,Messages_Per_Second");
//
//            for (int i = 0; i < numBuckets; i++) {
//                int timeInSeconds = i * 10;
//                double messagesPerSec = bucketCounts[i] / 10.0;
//
//                if (bucketCounts[i] > 0) {
//                    writer.println(timeInSeconds + "," + messagesPerSec);
//                }
//            }
//
//            writer.close();
//        } catch (Exception e) {
//            System.err.println("Error creating throughput CSV: " + e.getMessage());
//        }
//    }
//}