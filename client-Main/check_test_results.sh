#!/bin/bash

# Will be filled in after test
START_TIME=" 2025-11-24T07:31:00Z"  # 1 min before test
END_TIME=" 2025-11-24T07:32:37Z"    # 5 min after test start

echo "========================================="
echo "TEST RESULTS ANALYSIS"
echo "Checking from $START_TIME to $END_TIME"
echo "========================================="

# 1. CloudWatch - Messages sent to SQS
echo ""
echo "1️⃣  MESSAGES SENT TO SQS (from CloudWatch):"
total_sent=0
for i in {1..20}; do
    sent=$(aws cloudwatch get-metric-statistics \
        --namespace AWS/SQS \
        --metric-name NumberOfMessagesSent \
        --dimensions Name=QueueName,Value=chat-room-$i.fifo \
        --start-time $START_TIME \
        --end-time $END_TIME \
        --period 360 \
        --statistics Sum \
        --region us-west-2 \
        --query 'Datapoints[].Sum' \
        --output text 2>/dev/null | \
        awk '{for(i=1;i<=NF;i++) sum+=$i} END {print sum+0}')

    if [ $sent -gt 0 ]; then
        echo "  Room $i: $sent"
        total_sent=$((total_sent + sent))
    fi
done
echo "  -------------------"
echo "  TOTAL: $total_sent"

# 2. Consumer metrics
echo ""
echo "2️⃣  CONSUMER METRICS:"
total_consumed=0
for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
    consumed=$(curl -s http://$server:8081/metrics 2>/dev/null | grep -o '"messagesConsumed":[0-9]*' | grep -o '[0-9]*')
    written=$(curl -s http://$server:8081/db-stats 2>/dev/null | grep -o '"written":[0-9]*' | grep -o '[0-9]*')
    echo "  $server: consumed=${consumed:-0}, written=${written:-0}"
    total_consumed=$((total_consumed + ${consumed:-0}))
done
echo "  -------------------"
echo "  TOTAL: $total_consumed"

# 3. Database count
echo ""
echo "3️⃣  DATABASE:"
db_count=$(mysql -h 172.31.26.160 -u vatsalya -pVatsalya2024! chatServerDB \
    -e "SELECT COUNT(*) FROM messages;" 2>/dev/null | tail -1)
echo "  Total messages: $db_count"

# 4. Message distribution
echo ""
echo "4️⃣  MESSAGE TYPE DISTRIBUTION:"
mysql -h 172.31.26.160 -u vatsalya -pVatsalya2024! chatServerDB << 'EOF'
SELECT
    message_type,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM messages), 1) as percentage
FROM messages
GROUP BY message_type;
EOF

# 5. SQS current state
echo ""
echo "5️⃣  SQS QUEUES (current):"
total_in_sqs=0
for i in {1..20}; do
    depth=$(aws sqs get-queue-attributes \
        --queue-url "https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-$i.fifo" \
        --attribute-names ApproximateNumberOfMessages \
        --region us-west-2 2>/dev/null | \
        grep -o '"ApproximateNumberOfMessages": "[0-9]*"' | grep -o '[0-9]*')
    if [ "${depth:-0}" -gt 0 ]; then
        echo "  Room $i: $depth"
        total_in_sqs=$((total_in_sqs + ${depth:-0}))
    fi
done
echo "  TOTAL: $total_in_sqs"

# 6. Analysis
echo ""
echo "========================================="
echo "ANALYSIS:"
echo "========================================="
echo "Client should send:     ~10,000"
echo "Server → SQS sent:      $total_sent"
echo "Consumers consumed:     $total_consumed"
echo "Database written:       $db_count"
echo "Still in SQS:           $total_in_sqs"
echo ""

if [ $total_sent -gt 0 ]; then
    sqs_to_consumer_rate=$(awk "BEGIN {printf \"%.1f\", ($total_consumed/$total_sent)*100}")
    echo "SQS → Consumer rate:   $sqs_to_consumer_rate%"
fi

if [ $total_consumed -gt 0 ]; then
    consumer_to_db_rate=$(awk "BEGIN {printf \"%.1f\", ($db_count/$total_consumed)*100}")
    echo "Consumer → DB rate:    $consumer_to_db_rate%"
fi

echo ""
echo "🔍 WHERE ARE MESSAGES LOST?"
if [ $total_sent -lt 9000 ]; then
    echo "  ⚠️  CLIENT → SQS: Server only sent $total_sent to SQS"
    echo "      Problem: Server not publishing all messages"
fi

if [ $total_consumed -lt $total_sent ]; then
    lost=$((total_sent - total_consumed))
    echo "  ⚠️  SQS → CONSUMER: Lost $lost messages"
    echo "      Problem: Consumer not pulling fast enough"
fi

if [ $db_count -lt $total_consumed ]; then
    lost=$((total_consumed - db_count))
    echo "  ⚠️  CONSUMER → DB: Lost $lost messages"
    echo "      Problem: Database writer dropping messages"
fi

if [ $total_sent -ge 9000 ] && [ $total_consumed -ge $total_sent ] && [ $db_count -ge $total_consumed ]; then
    echo "  ✅ NO LOSS! Pipeline is 100% reliable!"
fi

echo "========================================="