#!/bin/bash

DB_HOST="172.31.26.160"
DB_USER="vatsalya"
DB_PASS="Vatsalya2024!"
DB_NAME="chatServerDB"

echo "========================================"
echo "DATABASE FINAL VERIFICATION"
echo "========================================"

# Total messages
echo ""
echo "📊 Total Messages in DB:"
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT COUNT(*) as total_messages FROM messages;" 2>/dev/null

# Message type distribution
echo ""
echo "📈 Message Type Distribution:"
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT message_type, COUNT(*) as count FROM messages GROUP BY message_type;" 2>/dev/null

# Messages per room
echo ""
echo "🏠 Messages Per Room (Top 10):"
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT room_id, COUNT(*) as count FROM messages GROUP BY room_id ORDER BY count DESC LIMIT 10;" 2>/dev/null

# Time range
echo ""
echo "⏰ Message Time Range:"
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT MIN(timestamp) as first_message, MAX(timestamp) as last_message FROM messages;" 2>/dev/null

# Unique users
echo ""
echo "👥 Unique Users:"
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT COUNT(DISTINCT user_id) as unique_users FROM messages;" 2>/dev/null

echo ""
echo "========================================"