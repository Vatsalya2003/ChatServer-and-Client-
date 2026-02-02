# Database Setup

## Quick Start
1. Run setup script: `./setup.sh`
2. Verify: `mysql -u vatsalya -p chatServerDB`
3. Check tables: `SHOW TABLES;`

## Schema
- Single table: `messages`
- 4 indexes for query optimization
- InnoDB engine for ACID compliance

## Connection
- Host: 172.31.26.160
- Port: 3306
- Database: chatServerDB
- User: vatsalya