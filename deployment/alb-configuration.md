# Application Load Balancer Configuration Guide

## Overview
Set up AWS Application Load Balancer to distribute traffic across 4 server instances.

## Why ALB for WebSocket?
- Supports WebSocket protocol upgrade
- Sticky sessions maintain persistent connections
- Automatic health monitoring and failover
- Horizontal scalability

## Step 1: Create Target Group

### Using AWS Console:

1. **Navigate to Target Groups:**
    - EC2 Console → Target Groups (left sidebar)
    - Click "Create target group"

2. **Choose Target Type:**
    - Select: **"Instances"**
    - Click "Next"

3. **Basic Configuration:**
    - Target group name: `chat-servers-t2medium`
    - Protocol: **HTTP**
    - Port: **8080**
    - VPC: Select your VPC
    - Protocol version: HTTP1

4. **Health Check Settings:**
    - Health check protocol: HTTP
    - Health check path: `/health`
    - Port: Traffic port
    - Healthy threshold: `2`
    - Unhealthy threshold: `3`
    - Timeout: `5` seconds
    - Interval: `30` seconds
    - Success codes: `200`

5. **Advanced Health Check (Optional):**
    - Keep defaults

6. **Register Targets:**
    - Click "Next"
    - Select all 4 server instances
    - Port: `8080`
    - Click "Include as pending below"
    - Click "Create target group"

7. **Wait for Health Checks:**
    - Status will change: Initial → Unhealthy → Healthy
    - Takes 60-90 seconds for all to become healthy

### Enable Sticky Sessions:

1. **Target Group → Attributes tab**
2. **Click "Edit"**
3. **Stickiness:**
    - ✓ Turn on group stickiness
    - Stickiness type: Load balancer generated cookie
    - Stickiness duration: `86400` seconds (24 hours)
4. **Save changes**

## Step 2: Create Application Load Balancer

### Using AWS Console:

1. **Navigate to Load Balancers:**
    - EC2 Console → Load Balancers
    - Click "Create load balancer"

2. **Select Type:**
    - Choose: **"Application Load Balancer"**
    - Click "Create"

3. **Basic Configuration:**
    - Load balancer name: `chat-app-t2Medium`
    - Scheme: **Internet-facing**
    - IP address type: **IPv4**

4. **Network Mapping:**
    - VPC: Select your VPC
    - Availability Zones: **Select at least 2 AZs**
        - ✓ us-west-2a
        - ✓ us-west-2b
        - ✓ us-west-2c
        - ✓ us-west-2d (if available)

5. **Security Groups:**
    - Create new or select existing
    - Must allow: **Port 80** (HTTP) from 0.0.0.0/0

6. **Listeners and Routing:**
    - Protocol: **HTTP**
    - Port: **80**
    - Default action: **Forward to** → Select `chat-servers-t2medium`

7. **Summary:**
    - Review all settings
    - Click "Create load balancer"

8. **Wait for Provisioning:**
    - State: Provisioning → Active
    - Takes 2-3 minutes

## Step 3: Get ALB DNS Name

1. **Load Balancers page**
2. **Select your ALB:** chat-app-t2Medium
3. **Copy DNS name:**
```
   chat-app-t2Medium-2024449659.us-west-2.elb.amazonaws.com
```
4. **Use this in your client configuration!**

## Verify ALB Configuration

### Test Health Endpoint:
```bash
# Test ALB forwards to servers
curl http://chat-app-t2Medium-2024449659.us-west-2.elb.amazonaws.com/health

# Should return: {"status":"RUNNING"}
```

### Check Target Health:
1. EC2 → Target Groups → chat-servers-t2medium
2. Targets tab
3. All 4 targets should show: **Healthy**

### Test Load Distribution:
```bash
# Make multiple requests
for i in {1..10}; do
  curl http://YOUR-ALB-DNS/health
done

# Check server logs to see which servers handled requests
```

## ALB Configuration Summary

### Load Balancer:
```
Name: chat-app-t2Medium
DNS: chat-app-t2Medium-2024449659.us-west-2.elb.amazonaws.com
Type: Application
Scheme: Internet-facing
IP Type: IPv4
```

### Listener:
```
Protocol: HTTP
Port: 80
Forward to: chat-servers-t2medium (target group)
```

### Target Group:
```
Name: chat-servers-t2medium
Protocol: HTTP
Port: 8080
Targets: 4 EC2 instances
Health Check: /health (30s interval)
Sticky Sessions: Enabled (24h duration)
```

## Advanced Configuration (Optional)

### Modify Attributes:

**Target Group Attributes:**
- Deregistration delay: 300 seconds
- Slow start: Disabled
- Load balancing algorithm: Round robin
- Stickiness: Enabled

**Load Balancer Attributes:**
- Idle timeout: 60 seconds
- HTTP/2: Enabled
- Access logs: Disabled (or enable for debugging)
- Deletion protection: Disabled

## Client Configuration

### Update Client Code:
```java
// Replace server URL with ALB DNS
private static final String SERVER_URL = 
  "ws://chat-app-t2Medium-2024449659.us-west-2.elb.amazonaws.com/chat/";
```

### Rebuild Client:
```bash
cd client-part2
mvn clean package
```

## Monitoring ALB

### CloudWatch Metrics:
- Navigate to CloudWatch → Metrics → ApplicationELB
- Metrics available:
    - ActiveConnectionCount
    - TargetResponseTime
    - RequestCount
    - HealthyHostCount
    - UnhealthyHostCount

### Target Group Monitoring:
- EC2 → Target Groups → Monitoring tab
- View graphs for:
    - Request count per target
    - Response times
    - HTTP status codes

## Troubleshooting

### All Targets Showing Unhealthy:

**Check 1: Health Check Path**
- Verify path is `/health` (case-sensitive)
- Not `/` or `/api/health`

**Check 2: Security Group**
- Instance security group must allow port 8080
- From ALB security group or 0.0.0.0/0

**Check 3: Servers Running**
```bash
# SSH into each server
ssh -i key.pem ec2-user@SERVER_IP

# Check if running
ps aux | grep server.jar

# Test locally
curl http://localhost:8080/health
```

**Check 4: Target Registration**
- Verify correct instances registered
- Verify port is 8080
- Check instances are in same VPC as ALB

### Connection Timeouts:

- Increase idle timeout: ALB → Attributes → Edit
- Set to 120 seconds for long-running WebSocket connections

### Uneven Load Distribution:

- Sticky sessions may cause clustering
- For testing, can disable sticky sessions temporarily
- Or use more client threads to force distribution

## Testing Load Distribution

### Method 1: Check Connection Count
```bash
# On each server, count WebSocket connections
ssh -i key.pem ec2-user@SERVER_IP 'netstat -an | grep :8080 | grep ESTABLISHED | wc -l'

# Should be roughly equal across all 4 servers
```

### Method 2: Check Logs
```bash
# Server logs show incoming connections
tail -f server.log | grep "New connection"

# Should see connections distributed across servers
```

## Scaling

### Add More Servers:
1. Launch new EC2 instance from AMI
2. Start server application
3. Register with target group
4. Wait for health check to pass
5. ALB automatically includes in rotation

### Remove Servers:
1. Deregister from target group
2. Wait for connection draining (300s)
3. Stop or terminate instance

## Cost

**ALB Pricing (us-west-2):**
- $0.0225 per hour (~$16/month)
- $0.008 per LCU-hour (Load Balancer Capacity Unit)
- Estimated: $20-25/month for this workload

## Cleanup

### Delete ALB:
1. Load Balancers → Select ALB
2. Actions → Delete load balancer
3. Confirm deletion

### Delete Target Group:
1. Target Groups → Select group
2. Actions → Delete
3. Confirm deletion

**Note:** Delete ALB first, then target group

## Summary

**ALB distributes HTTP traffic on port 80 to 4 EC2 instances on port 8080**

**Key Features:**
- Health monitoring every 30 seconds
- Automatic failover for unhealthy targets
- Sticky sessions for WebSocket persistence
- Even load distribution via round-robin

**DNS Name:** Use ALB DNS in client for load-balanced testing