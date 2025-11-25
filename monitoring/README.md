# Monitoring Scripts

## Scripts

### check_all_metrics.sh
Collects complete system snapshot from all nodes.
Usage: `./check_all_metrics.sh > snapshot.txt`

### monitor_realtime.sh
Live monitoring during tests (updates every 5 seconds).
Usage: `./monitor_realtime.sh`

### check_db_health.sh
Database connection and health check.
Usage: `./check_db_health.sh`

## Metrics Collected
- Server: SQS publish counters, circuit breaker status
- Consumer: Messages consumed, write throughput
- Database: Connection count, message totals
```

---

## **📁 FOLDER STRUCTURE:**
```
/database/
├── schema.sql
├── setup.sh
└── README.md

/monitoring/
├── check_metrics.sh
├── monitor.sh
├── check_db_health.sh
└── README.md