# EC2 Instance Setup Guide

## Overview
Launch and configure 5 EC2 instances for the distributed chat system.

## Instance Requirements

**Total Instances Needed:** 5
- 4 instances for WebSocket servers
- 1 instance for consumer application (or co-locate with one server)

**Recommended Instance Type:** t2.medium
- vCPUs: 2
- Memory: 4 GB
- Network: Low to Moderate

## Launch Instances via AWS Console

### Step 1: Launch Instance

1. **Navigate to EC2:**
    - AWS Console → EC2 → Instances
    - Click "Launch instances"

2. **Name and Tags:**
    - Name: `chat-server-1` (or `chat-server-2`, etc.)

3. **Choose AMI:**
    - Amazon Machine Image: **Amazon Linux 2023**
    - Architecture: 64-bit (x86)

4. **Instance Type:**
    - Select: **t2.medium**

5. **Key Pair:**
    - Select existing key pair or create new
    - Example: `cs6650_ChatApp.pem`
    - Download and save securely

6. **Network Settings:**
    - VPC: Default VPC
    - Auto-assign public IP: Enable
    - Create or select security group (see below)

7. **Configure Storage:**
    - Size: 8 GB (default)
    - Volume Type: gp3

8. **Advanced Details:**
    - IAM instance profile: **LabRole** 
    - This provides SQS access permissions

9. **Launch Instance:**
    - Click "Launch instance"
    - Repeat for all 5 instances

## Security Group Configuration

**Create security group:** `chat-server-sg`

### Inbound Rules:

| Type | Protocol | Port | Source | Description |
|------|----------|------|--------|-------------|
| SSH | TCP | 22 | My IP | SSH access |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 | Server HTTP |
| Custom TCP | TCP | 8081 | 0.0.0.0/0 | Consumer metrics |

### Outbound Rules:
- All traffic allowed (default)

## Install Java on Each Instance

### Connect via SSH:
```bash
# Set key permissions
chmod 400 cs6650_ChatApp.pem

# SSH into instance
ssh -i cs6650_ChatApp.pem ec2-user@INSTANCE_PUBLIC_IP
```

### Install Java 21:
```bash
# Update system
sudo dnf update -y

# Install Java
sudo dnf install -y java-21-amazon-corretto

# Verify installation
java -version
# Should show: openjdk version "21.0.x"

# Install AWS CLI (optional, for debugging)
sudo dnf install -y aws-cli
aws --version
```

## Deploy Application JARs

### Upload Server JAR:
```bash
# From your local machine
scp -i cs6650_ChatApp.pem \
  server-v2/target/Server-0.01-SNAPSHOT.jar \
  ec2-user@INSTANCE_IP:/home/ec2-user/server.jar
```

### Upload Consumer JAR:
```bash
# Only on consumer instance
scp -i cs6650_ChatApp.pem \
  consumer/target/consumer-0.0.1-SNAPSHOT.jar \
  ec2-user@CONSUMER_IP:/home/ec2-user/consumer.jar
```

## Start Services

### Start Server:
```bash
# SSH into server instance
ssh -i cs6650_ChatApp.pem ec2-user@INSTANCE_IP

# Run server in background
nohup java -jar server.jar > server.log 2>&1 &

# Verify it's running
ps aux | grep server.jar

# Check logs
tail -f server.log
# Press Ctrl+C to exit

# Test health endpoint
curl http://localhost:8080/health
# Should return: {"status":"RUNNING"}
```

### Start Consumer:
```bash
# SSH into consumer instance
ssh -i cs6650_ChatApp.pem ec2-user@CONSUMER_IP

# Run consumer in background
nohup java -jar consumer.jar > consumer.log 2>&1 &

# Verify it's running
ps aux | grep consumer.jar

# Check logs
tail -f consumer.log

# Test metrics endpoint
curl http://localhost:8081/metrics
```

## Verify Deployment

### Check All Servers:
```bash
# Test each server health endpoint
curl http://SERVER1_IP:8080/health
curl http://SERVER2_IP:8080/health
curl http://SERVER3_IP:8080/health
curl http://SERVER4_IP:8080/health

# All should return: {"status":"RUNNING"}
```

### Check Consumer:
```bash
curl http://CONSUMER_IP:8081/health
# Should return: {"status":"UP","service":"Chat Consumer"}
```

### Check SQS Connectivity:
```bash
# From any server
curl http://localhost:8080/sqs-status
# Should show: {"state":"CLOSED","failures":0}
```

## Instance Details

**Instance IDs (Example):**
- Server 1: i-07f593aa18360d495 (52.12.239.32)
- Server 2: i-027354424a71d974e (35.91.44.114)
- Server 3: i-0bef1bf8dafaf90fc (18.237.244.191)
- Server 4: i-0cedee409d4f38fcc (44.243.189.214)

## Stopping Services
```bash
# Stop server
sudo pkill -f server.jar

# Stop consumer
sudo pkill -f consumer.jar

# Verify stopped
ps aux | grep -E "(server|consumer).jar"
```

## Creating AMI for Easy Replication

1. **Configure one instance completely:**
    - Install Java
    - Upload JAR files
    - Test that it works

2. **Create AMI:**
    - EC2 Console → Select instance
    - Actions → Image and templates → Create image
    - Name: `chat-server-configured`
    - Create image

3. **Launch from AMI:**
    - Launch 3 more instances from this AMI
    - Saves time vs configuring each manually

## Quick Reference

| Component | Port | Endpoint | Check |
|-----------|------|----------|-------|
| Server | 8080 | /health | Server running |
| Consumer | 8081 | /health | Consumer running |
| Consumer | 8081 | /metrics | Performance stats |
| Server | 8080 | /sqs-status | Circuit breaker |

## Region
All resources must be in: **us-west-2 (Oregon)**

## Cost Estimate
- 5 × t2.medium instances: ~$0.05/hour each = $0.25/hour
- 20 SQS FIFO queues: ~$0.50 per million requests
- 1 ALB: ~$0.025/hour
- **Total: ~$0.30/hour or ~$220/month**

## Next Steps
After deployment is complete:
1. Configure client to use ALB DNS
2. Run load tests
3. Monitor metrics and queue depths
4. Take screenshots for documentation