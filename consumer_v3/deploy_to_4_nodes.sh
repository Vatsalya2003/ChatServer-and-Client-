#!/bin/bash

# ========================================
# 4-NODE CONSUMER DEPLOYMENT SCRIPT
# ========================================

# REPLACE THESE WITH YOUR 4 SERVER IPs
SERVER1="35.93.123.250"
SERVER2="34.211.149.83"
SERVER3="44.249.98.73"
SERVER4="34.212.137.164"

KEY="your-key.pem"  # REPLACE with your key file path

echo "========================================="
echo "  DEPLOYING CONSUMER TO 4 NODES"
echo "========================================="
echo ""

# Check if JAR exists
if [ ! -f target/consumer-*.jar ]; then
    echo "❌ Consumer JAR not found!"
    echo "Run: mvn clean package -DskipTests"
    exit 1
fi

JAR_FILE=$(ls target/consumer-*.jar | head -1)
echo "Deploying: $JAR_FILE"
echo ""

# Deploy to all 4 nodes
for ip in $SERVER1 $SERVER2 $SERVER3 $SERVER4; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Deploying to $ip..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Create directory
    ssh -i $KEY ec2-user@$ip "mkdir -p /home/ec2-user/consumer" 2>/dev/null
    
    # Upload JAR
    scp -i $KEY $JAR_FILE ec2-user@$ip:/home/ec2-user/consumer/consumer.jar
    
    if [ $? -eq 0 ]; then
        echo "✅ Deployed to $ip"
    else
        echo "❌ Failed to deploy to $ip"
    fi
    echo ""
done

echo "========================================="
echo "✅ Deployment complete!"
echo "========================================="