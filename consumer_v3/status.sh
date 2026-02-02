#!/bin/bash

SERVER1="18.236.94.113"
SERVER2="34.208.245.83"
SERVER3="34.221.32.34"
SERVER4="34.215.72.202"

echo "Quick Status Check:"
echo ""

for i in 1 2 3 4; do
    eval server=\$SERVER$i

    echo -n "Node $i: "

    # Server
    server_status=$(curl -s --connect-timeout 1 http://$server:8080/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    # Consumer
    consumer_status=$(curl -s --connect-timeout 1 http://$server:8081/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$server_status" == "RUNNING" ] && [ "$consumer_status" == "UP" ]; then
        echo "✅ HEALTHY"
    else
        echo "❌ ISSUE (Server: $server_status, Consumer: $consumer_status)"
    fi
done