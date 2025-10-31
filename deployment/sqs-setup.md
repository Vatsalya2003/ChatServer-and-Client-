# AWS SQS Queue Setup Guide

## Overview
Create 20 AWS SQS FIFO queues for chat rooms 1-20.

## Using AWS Console (Recommended for Beginners)

### Step-by-Step Instructions:

1. **Navigate to SQS:**
    - Go to AWS Console
    - Search for "SQS" in services
    - Click "Amazon SQS"

2. **Create First Queue:**
    - Click "Create queue"
    - Select **"FIFO"** queue type
    - Queue name: `chat-room-1.fifo`

3. **Configuration:**
    - **Content-based deduplication:** ✓ Enable
    - **Message retention period:** 4 days (default)
    - **Visibility timeout:** 30 seconds
    - **Maximum message size:** 256 KB (default)
    - **Delivery delay:** 0 seconds
    - **Receive message wait time:** 20 seconds

4. **Create Queue:**
    - Click "Create queue" button
    - Wait for confirmation

5. **Repeat for Rooms 2-20:**
    - Create `chat-room-2.fifo` through `chat-room-20.fifo`
    - Use same settings for all queues

## Using AWS CLI (Faster for Multiple Queues)

### Prerequisites:
```bash
# Configure AWS CLI
aws configure
# Enter your credentials and set region to us-west-2
```

### Create All 20 Queues:
```bash
# Set variables
REGION="us-west-2"
ACCOUNT_ID="211125351505"  # Replace with your account ID

# Create all queues
for i in {1..20}; do
  aws sqs create-queue \
    --queue-name chat-room-${i}.fifo \
    --region $REGION \
    --attributes '{
      "FifoQueue":"true",
      "ContentBasedDeduplication":"true",
      "VisibilityTimeout":"30",
      "MessageRetentionPeriod":"345600",
      "ReceiveMessageWaitTimeSeconds":"20"
    }'
  echo "✓ Created chat-room-${i}.fifo"
done
```

## Verify Queues Created
```bash
# List all queues
aws sqs list-queues \
  --region us-west-2 \
  --queue-name-prefix chat-room

# Should show 20 queue URLs
```

## Queue URLs Format
```
https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-1.fifo
https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-2.fifo
...
https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-20.fifo
```

## Configuration Summary

| Parameter | Value |
|-----------|-------|
| Queue Type | FIFO |
| Deduplication | Content-based |
| Visibility Timeout | 30 seconds |
| Message Retention | 4 days |
| Max Message Size | 256 KB |
| Polling Wait Time | 20 seconds |
| Number of Queues | 20 |

## Permissions Required

**IAM Role (LabRole) must have:**
- `sqs:SendMessage` - For servers to publish
- `sqs:ReceiveMessage` - For consumer to poll
- `sqs:DeleteMessage` - For consumer to remove processed messages
- `sqs:GetQueueAttributes` - For monitoring
- `sqs:ListQueues` - For verification

## Testing Queue Access
```bash
# Send test message
aws sqs send-message \
  --queue-url https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-1.fifo \
  --message-body "Test message" \
  --message-group-id "test" \
  --message-deduplication-id "test-123"

# Receive message
aws sqs receive-message \
  --queue-url https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-1.fifo

# Delete message
aws sqs delete-message \
  --queue-url https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-1.fifo \
  --receipt-handle "RECEIPT_HANDLE_FROM_RECEIVE"
```

## Cleanup (After Testing)
```bash
# Purge all queues
for i in {1..20}; do
  aws sqs purge-queue \
    --queue-url https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-${i}.fifo
  echo "✓ Purged chat-room-${i}"
done

# Delete all queues (if needed)
for i in {1..20}; do
  aws sqs delete-queue \
    --queue-url https://sqs.us-west-2.amazonaws.com/211125351505/chat-room-${i}.fifo
  echo "✓ Deleted chat-room-${i}"
done
```

## Notes
- FIFO queues ensure message ordering within each room
- Content-based deduplication prevents duplicate messages
- 20 queues allow parallel processing across rooms
- Each queue handles one chat room independently