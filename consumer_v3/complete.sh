#!/bin/bash
echo "========================================="
echo "POST-TEST RESULTS - 1000 MESSAGES"
echo "========================================="

# 1. Client results
echo ""
echo "1️⃣ CLIENT OUTPUT:"
echo "Paste your client's final output:"
echo "  Successful: ????"
echo "  Failed: ????"

# 2. Server counters
echo ""
echo "2️⃣ ALL SERVER SQS COUNTERS:"
total_pub=0
total_drop=0
total_fail=0

for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
    echo ""
    echo "$server:"
    stats=$(curl -s http://$server:8080/sqs-status)
    echo "$stats" | python3 -m json.tool

    pub=$(echo "$stats" | grep -o '"published":[0-9]*' | grep -o '[0-9]*')
    drop=$(echo "$stats" | grep -o '"circuitBreakerDrops":[0-9]*' | grep -o '[0-9]*')
    fail=$(echo "$stats" | grep -o '"publishFailures":[0-9]*' | grep -o '[0-9]*')

    total_pub=$((total_pub + ${pub:-0}))
    total_drop=$((total_drop + ${drop:-0}))
    total_fail=$((total_fail + ${fail:-0}))
done

# 3. Consumer metrics
echo ""
echo "3️⃣ ALL CONSUMER METRICS:"
total_cons=0
total_writ=0

for server in 18.236.94.113 34.208.245.83 34.221.32.34 34.215.72.202; do
    cons=$(curl -s http://$server:8081/metrics 2>/dev/null | grep -o '"messagesConsumed":[0-9]*' | grep -o '[0-9]*')
    writ=$(curl -s http://$server:8081/db-stats 2>/dev/null | grep -o '"written":[0-9]*' | grep -o '[0-9]*')
    echo "$server: consumed=${cons:-0}, written=${writ:-0}"
    total_cons=$((total_cons + ${cons:-0}))
    total_writ=$((total_writ + ${writ:-0}))
done

# 4. Database
echo ""
echo "4️⃣ DATABASE:"
db=$(ssh ec2-user@18.236.94.113 "mysql -h 172.31.26.160 -u vatsalya -pVatsalya2024! chatServerDB -e 'SELECT COUNT(*) FROM messages;' 2>/dev/null" | tail -1)

# 5. Summary
echo ""
echo "========================================="
echo "COMPLETE FLOW:"
echo "========================================="
echo "Client sent:           ~1,0000"
echo "Servers published:     $total_pub"
echo "Servers dropped:       $total_drop"
echo "Servers failed:        $total_fail"
echo "Consumers consumed:    $total_cons"
echo "Consumers wrote:       $total_writ"
echo "Database has:          $db"
echo ""

if [ $total_drop -gt 0 ]; then
    echo "🚨 $total_drop messages dropped by circuit breaker!"
fi

if [ $total_pub -eq $total_cons ] && [ $total_cons -eq $db ]; then
    echo "✅ NO LOSS! All $total_pub messages made it through!"
else
    echo "⚠️  Messages lost somewhere in pipeline"
fi

echo "========================================="