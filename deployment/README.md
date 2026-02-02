# Deployment Guide

Complete instructions for deploying the distributed chat system on AWS.

## Overview
This guide covers the deployment of all system components:
- 20 AWS SQS FIFO queues
- 4 EC2 instances for WebSocket servers
- 1 EC2 instance for consumer application
- Application Load Balancer for traffic distribution

## Prerequisites
- AWS Account with appropriate permissions
- AWS CLI installed and configured locally
- SSH key pair created (e.g., cs6650_ChatApp.pem)
- Basic knowledge of AWS EC2, SQS, and ALB

## Deployment Order

### Step 1: Create SQS Queues
See [sqs-setup.md](sqs-setup.md) for detailed instructions.
- Create 20 FIFO queues (chat-room-1.fifo through chat-room-20.fifo)
- Configure deduplication and visibility timeout
- Verify queues are accessible

### Step 2: Launch EC2 Instances
See [ec2-setup.md](ec2-setup.md) for detailed instructions.
- Launch 4 t2.medium instances for servers
- Launch 1 t2.medium instance for consumer (or co-locate with server)
- Configure security groups
- Attach IAM role (LabRole)
- Install Java 21 on all instances

### Step 3: Configure Load Balancer
See [alb-configuration.md](alb-configuration.md) for detailed instructions.
- Create target group with 4 server instances
- Create Application Load Balancer
- Configure health checks and sticky sessions
- Verify all targets are healthy

### Step 4: Deploy Applications

**Build all components:**
```bash
# Server
cd server-v2
mvn clean package

# Consumer
cd ../consumer
mvn clean package

# Client
cd ../client-part2
mvn clean package
```

**Deploy to EC2:**
```bash
# Deploy server to all 4 instances
for IP in SERVER1_IP SERVER2_IP SERVER3_IP SERVER4_IP; do
  scp -i cs6650_ChatApp.pem target/Server-0.01-SNAPSHOT.jar ec2-user@$IP:~/server.jar
  ssh -i cs6650_ChatApp.pem ec2-user@$IP 'nohup java -jar server.jar > server.log 2>&1 &'
  echo "✓ Deployed to $IP"
done

# Deploy consumer
scp -i cs6650_ChatApp.pem target/consumer-0.0.1-SNAPSHOT.jar ec2-user@CONSUMER_IP:~/consumer.jar
ssh -i cs6650_ChatApp.pem ec2-user@CONSUMER_IP 'nohup java -jar consumer.jar > consumer.log 2>&1 &'
```

### Step 5: Verify Deployment
```bash
# Check servers
curl http://SERVER_IP:8080/health

# Check consumer
curl http://CONSUMER_IP:8081/health

# Check ALB
curl http://ALB_DNS/health

# All should return success status
```

## Configuration Files

### Server (application.properties)
```properties
server.port=8080
aws.region=us-west-2
aws.account.id=YOUR_ACCOUNT_ID
```

### Consumer (application.properties)
```properties
server.port=8081
aws.region=us-west-2
aws.account.id=YOUR_ACCOUNT_ID
consumer.threads=80
consumer.polling.wait.seconds=1
consumer.batch.size=10
server.broadcast.url=http://localhost:8080/api/broadcast
```
## Architecture Summary
```
Client → ALB → [Server1, Server2, Server3, Server4] → SQS → Consumer → Broadcast
```

## Region
All resources must be in the same region: **us-west-2 (Oregon)**