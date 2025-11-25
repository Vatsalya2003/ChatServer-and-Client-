#!/bin/bash
# Database setup script

echo "Setting up database..."

# Install MariaDB
sudo yum update -y
sudo yum install mariadb-server -y

# Start MariaDB
sudo systemctl start mariadb
sudo systemctl enable mariadb

# Create database and user
mysql -u root << EOF
CREATE DATABASE IF NOT EXISTS chatServerDB;
CREATE USER IF NOT EXISTS 'vatsalya'@'%' IDENTIFIED BY 'Vatsalya2024!';
GRANT ALL PRIVILEGES ON chatServerDB.* TO 'vatsalya'@'%';
FLUSH PRIVILEGES;
EOF

# Import schema
mysql -u vatsalya -pVatsalya2024! chatServerDB < schema.sql

echo "Database setup complete!"