#!/bin/bash
# Monitor SQS queue depths for all chat rooms

# Configuration - CHANGE WITH YOUR VALUES
ACCOUNT_ID="211125505"
REGION="us-west-2"

echo "-------- SQS Queue Depths --------"
echo ""

TOTAL=0
for i in {1..20}; do
  QUEUE_URL="https://sqs.$REGION.amazonaws.com/$ACCOUNT_ID/chat-room-${i}.fifo"
  
  DEPTH=$(aws sqs get-queue-attributes \
    --queue-url $QUEUE_URL \
    --attribute-names ApproximateNumberOfMessages \
    --query 'Attributes.ApproximateNumberOfMessages' \
    --output text 2>/dev/null || echo "0")
  
  TOTAL=$((TOTAL + DEPTH))
  
  printf "Room %2d: %6s messages\n" $i $DEPTH
done

echo ""
echo "Total: $TOTAL messages"
echo ""