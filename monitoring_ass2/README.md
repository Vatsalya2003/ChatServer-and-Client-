# Monitoring Scripts

## Overview
Simple scripts to monitor system health and SQS queue depths.

## Scripts

### check-health.sh
Checks health status of all system components:
- 4 server instances (port 8080)
- Consumer application (port 8081)
- Application Load Balancer

### monitor-queues.sh
Monitors SQS queue depths:
- Shows message count for all 20 rooms
- Displays total messages across all queues
- Helps identify queue backlogs

## Setup
```bash
# Make scripts executable
chmod +x check-health.sh
chmod +x monitor-queues.sh
```

## Usage

### Health Check:
```bash
./check-health.sh
```

**Output:**
```
------ Server Health ------
Server 52.12.239.32: RUNNING
Server 35.91.44.114: RUNNING
Server 18.237.244.191: RUNNING
Server 44.243.189.214: RUNNING

------ Consumer Health ------
{"status":"UP","service":"Chat Consumer"}

------ ALB Health ------
{"status":"RUNNING"}
```

### Queue Monitoring:
```bash
./monitor-queues.sh
```

**Output:**
```
------ SQS Queue Depths ------

Room  1:    1128 messages
Room  2:    1078 messages
Room  3:      979 messages
...
Room 20:    1220 messages
```

## Configuration

Update IP addresses in scripts:
- **check-health.sh:** Lines 4-9 (server IPs, consumer IP, ALB DNS)
- **monitor-queues.sh:** Lines 4-5 (AWS account ID, region)

## Requirements
- curl (for health checks)
- AWS CLI (for queue monitoring)
- Network access to EC2 instances on ports 8080, 8081