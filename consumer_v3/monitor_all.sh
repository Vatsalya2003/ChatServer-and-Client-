#!/bin/bash

# Server IPs
SERVER1="18.236.94.113"
SERVER2="34.208.245.83"
SERVER3="34.221.32.34"
SERVER4="34.215.72.202"

# Consumer runs on same IPs, port 8081
CONSUMER1="18.236.94.113"
CONSUMER2="34.208.245.83"
CONSUMER3="34.221.32.34"
CONSUMER4="34.215.72.202"

echo "========================================"
echo "DISTRIBUTED CHAT SYSTEM MONITOR"
echo "Time: $(date)"
echo "========================================"

# Server Health
echo ""
echo "📡 SERVER HEALTH:"
for server in $SERVER1 $SERVER2 $SERVER3 $SERVER4; do
    echo -n "Server $server: "
    curl -s --connect-timeout 2 http://$server:8080/health | grep -o '"status":"[^"]*"' || echo "OFFLINE"
done

# Consumer Metrics
echo ""
echo "📊 CONSUMER METRICS:"
for i in 1 2 3 4; do
    eval consumer=\$CONSUMER$i
    echo ""
    echo "--- Consumer Node $i ($consumer) ---"
    curl -s --connect-timeout 2 http://$consumer:8081/metrics | python3 -m json.tool 2>/dev/null | grep -E '(messagesConsumed|consumptionRate|messagesProcessed|activeConsumers)' || echo "OFFLINE"
done

# Database Stats
echo ""
echo "💾 DATABASE WRITER STATS:"
for i in 1 2 3 4; do
    eval consumer=\$CONSUMER$i
    echo ""
    echo "--- DB Writer Node $i ($consumer) ---"
    curl -s --connect-timeout 2 http://$consumer:8081/db-stats | python3 -m json.tool 2>/dev/null | grep -E '(written|throughput|queueSize|circuitOpen)' || echo "OFFLINE"
done

echo ""
echo "========================================"