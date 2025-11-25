#!/bin/bash
# Health check script for all system components

# Configuration - CHANGE WITH YOUR IPs
SERVER1_IP="SERVER1_IP"
SERVER2_IP="SERVER2_IP"
SERVER3_IP="SERVER3_IP"
SERVER4_IP="SERVER4_IP"
CONSUMER_IP="SERVER1_IP"
ALB_DNS="YOUR ALB_DNS"

echo "-------- Server Health --------"
for IP in $SERVER1_IP $SERVER2_IP $SERVER3_IP $SERVER4_IP; do
  STATUS=$(curl -s http://$IP:8080/health | grep -o "RUNNING" || echo "DOWN")
  echo "Server $IP: $STATUS"
done

echo ""
echo "-------- Consumer Health --------"
curl -s http://$CONSUMER_IP:8081/health

echo ""
echo "-------- ALB Health --------"
curl -s http://$ALB_DNS/health

echo ""
echo "-------- Health Check Complete --------"