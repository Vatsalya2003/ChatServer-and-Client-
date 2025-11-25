#!/bin/bash
# Real-time monitoring during test

# Replace the Server IPS accroding to your servers

while true; do
    clear
    echo "=== REAL-TIME MONITOR $(date +%H:%M:%S) ==="

    total_consumed=0
    total_written=0

    for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
        consumed=$(curl -s http://$server:8081/metrics 2>/dev/null | grep -o '"messagesConsumed":[0-9]*' | grep -o '[0-9]*')
        written=$(curl -s http://$server:8081/db-stats 2>/dev/null | grep -o '"written":[0-9]*' | grep -o '[0-9]*')
        echo "$server: consumed=${consumed:-0}, written=${written:-0}"
        total_consumed=$((total_consumed + ${consumed:-0}))
        total_written=$((total_written + ${written:-0}))
    done

    echo "TOTAL: consumed=$total_consumed, written=$total_written"

    sleep 5
done