#!/bin/bash
6535
1018

SERVER1="18.236.94.113"
SERVER2="34.208.245.83"
SERVER3="34.221.32.34"
SERVER4="34.215.72.202"

echo "Real-time Throughput Monitor (updates every 3 seconds)"
echo "Press Ctrl+C to stop"
echo ""

while true; do
    clear
    echo "========================================"
    echo "THROUGHPUT MONITOR - $(date +%H:%M:%S)"
    echo "========================================"

    total_consumed=0
    total_written=0

    for i in 1 2 3 4; do
        eval server=\$SERVER$i

        # Get metrics
        metrics=$(curl -s --connect-timeout 2 http://$server:8081/metrics)
        dbstats=$(curl -s --connect-timeout 2 http://$server:8081/db-stats)

        consumed=$(echo "$metrics" | grep -o '"messagesConsumed":[0-9]*' | grep -o '[0-9]*')
        rate=$(echo "$metrics" | grep -o '"consumptionRate":"[^"]*"' | cut -d'"' -f4)
        written=$(echo "$dbstats" | grep -o '"written":[0-9]*' | grep -o '[0-9]*')
        throughput=$(echo "$dbstats" | grep -o '"throughput":"[^"]*"' | cut -d'"' -f4)
        queue=$(echo "$dbstats" | grep -o '"queueSize":[0-9]*' | grep -o '[0-9]*')

        echo "Node $i ($server):"
        echo "  Consumed: ${consumed:-0} | Rate: ${rate:-0}"
        echo "  Written:  ${written:-0} | Throughput: ${throughput:-0}"
        echo "  DB Queue: ${queue:-0}"
        echo ""

        total_consumed=$((total_consumed + ${consumed:-0}))
        total_written=$((total_written + ${written:-0}))
    done

    echo "========================================"
    echo "CLUSTER TOTALS:"
    echo "  Total Consumed: $total_consumed"
    echo "  Total Written:  $total_written"
    echo "========================================"

    sleep 3
done