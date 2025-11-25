#!/bin/bash
# Collect all system metrics

echo "--- SYSTEM METRICS ---"
echo "Time: $(date)"


# Replace the Server IPS accroding to your servers

# Server metrics
echo -e "\nSERVER SQS COUNTERS:"
for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
    echo "$server:"
    curl -s http://$server:8080/sqs-status | python3 -m json.tool
done

# Consumer metrics
echo -e "\nCONSUMER METRICS:"
for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
    echo "$server:"
    curl -s http://$server:8081/metrics | python3 -m json.tool
    curl -s http://$server:8081/db-stats | python3 -m json.tool
done

# Database
echo -e "\nDATABASE:"
mysql -h 172.31.26.160 -u vatsalya -pVatsalya2024! chatServerDB -e "SELECT COUNT(*) as total FROM messages;"