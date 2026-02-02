#!/bin/bash

NODE1="35.93.123.250"
NODE2="34.211.149.83"
NODE3="44.249.98.73"
NODE4="34.212.137.164"

KEY="cs6650_ChatApp.pem"

echo "Starting consumers on all 4 nodes..."

for ip in $NODE1 $NODE2 $NODE3 $NODE4; do
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Starting consumer on $ip..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    ssh -i $KEY ec2-user@$ip << 'REMOTE'
cd /home/ec2-user/consumer

# Stop if already running
pkill -f consumer.jar
sleep 3

# Start consumer
nohup java -jar consumer.jar > consumer.log 2>&1 &

# Wait for startup
sleep 15

# Check status
echo "Health check:"
curl -s http://localhost:8081/health

echo ""
echo "Config loaded:"
tail -50 consumer.log | grep -E "consumer threads:|Writer threads:|Batch size:"

REMOTE
    
    echo ""
done

echo "✅ All consumers started!"
