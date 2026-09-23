# Extract Module Testing Guide

## Status: Testing Extract in Isolation

The extract module has been fixed and is ready to test independently. All other DAG phases (transform, load, validate) are disconnected for now.

---

## Quick Start

### Prerequisites

Before testing, make sure you have:

1. **Analytics database created**:
   ```bash
   # On your Linux VM
   docker-compose exec db psql -U paysprint -d postgres -c \
     "CREATE DATABASE paysprint_analytics OWNER paysprint;"
   ```

2. **Staging schema deployed**:
   ```bash
   # On your Linux VM
   docker-compose exec db psql -U paysprint -d paysprint_analytics < \
     analytics/schema/staging_schema.sql
   ```

3. **Python dependencies installed**:
   ```bash
   pip install -r analytics/requirements.txt
   ```

---

## Test Method 1: Standalone Script (Easiest)

```powershell
# From your Windows machine, set environment variables:
$env:ENVIRONMENT = "local"
$env:DB_HOST = "localhost"
$env:DB_PORT = "8100"
$env:DB_NAME = "paysprint"
$env:DB_ANALYTICS_NAME = "paysprint_analytics"
$env:DB_USER = "paysprint"
$env:DB_PASSWORD = "your_password"

# Run the test script
cd c:\Users\Administrator\Documents\jeepers-leapers
python test_extract.py
```

**Expected Output**:
```
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] ANALYTICS ETL - EXTRACT MODULE TEST
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] TESTING DATABASE CONNECTIONS
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] Environment: local
[2026-09-23] [root] [INFO] OLTP Config: {...}
[2026-09-23] [root] [INFO] Staging Config: {...}
[2026-09-23] [root] [INFO] Testing OLTP connection...
[2026-09-23] [root] [INFO] ✓ OLTP connection successful
[2026-09-23] [root] [INFO] Testing Staging connection...
[2026-09-23] [root] [INFO] ✓ Staging connection successful
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] RUNNING EXTRACTION TEST
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] Extracted X exchanges, Y accounts, Z instruments, etc.
[2026-09-23] [root] [INFO] ==================================================
[2026-09-23] [root] [INFO] ✓ ALL TESTS PASSED
[2026-09-23] [root] [INFO] ==================================================
```

---

## Test Method 2: Python REPL (Debug)

```python
# From Python REPL in the project directory:
import os
os.environ['ENVIRONMENT'] = 'local'
os.environ['DB_HOST'] = 'localhost'
os.environ['DB_PORT'] = '8100'
os.environ['DB_NAME'] = 'paysprint'
os.environ['DB_ANALYTICS_NAME'] = 'paysprint_analytics'
os.environ['DB_USER'] = 'paysprint'
os.environ['DB_PASSWORD'] = 'your_password'

from analytics.etl.extract import run_extraction
from datetime import datetime

etl_run_id = f"manual_test_{datetime.utcnow().isoformat()}"
stats = run_extraction(etl_run_id)
print(stats)
```

---

## Test Method 3: Airflow DAG (If Deployed)

The simplified DAG `analytics_daily_etl_extract_only` runs only the extract phase.

```bash
# On the Linux VM:
docker-compose up -d airflow-webserver airflow-scheduler

# Then access at http://<vm-ip>:8082
# DAG: analytics_daily_etl_extract_only
# Manually trigger to test
```

---

## Verifying Results

After extraction succeeds, check the staging tables:

```bash
# Connect to analytics database
psql -h localhost -p 8100 -U paysprint -d paysprint_analytics

# Check what was extracted
SELECT COUNT(*) as exchange_count FROM staging.exchanges_raw;
SELECT COUNT(*) as account_count FROM staging.accounts_raw;
SELECT COUNT(*) as instrument_count FROM staging.instruments_raw;
SELECT COUNT(*) as holding_count FROM staging.holdings_raw;
SELECT COUNT(*) as trade_count FROM staging.trades_raw;

# Check ETL metadata
SELECT etl_run_id, COUNT(*) FROM staging.trades_raw GROUP BY etl_run_id;
```

---

## Common Issues & Fixes

### Issue: "psycopg2: cannot connect to server"
**Cause**: Database not reachable on specified host:port  
**Fix**: Verify PostgreSQL is running and port is correct
```bash
# Test connection manually
psql -h localhost -p 8100 -U paysprint -d paysprint -c "SELECT 1;"
```

### Issue: "relation 'staging.exchanges_raw' does not exist"
**Cause**: Staging schema not deployed  
**Fix**: Deploy staging schema
```bash
docker-compose exec db psql -U paysprint -d paysprint_analytics < \
  analytics/schema/staging_schema.sql
```

### Issue: "no rows extracted"
**Cause**: OLTP tables are empty  
**Fix**: Populate OLTP database with sample data
```bash
# Check what's in OLTP
psql -h localhost -p 8100 -U paysprint -d paysprint -c "SELECT COUNT(*) FROM public.trades;"
```

### Issue: "Module not found: analytics.etl"
**Cause**: Running from wrong directory  
**Fix**: Run from project root
```bash
cd c:\Users\Administrator\Documents\jeepers-leapers
python test_extract.py
```

---

## Changes Made to Fix Extract

1. **config.py**: Use DEFAULT_HOST (container-aware hostname) for all DB configs
2. **extract.py**: 
   - Fixed SQL parameter passing (`:param_name` instead of `%s`)
   - Use `sqlalchemy.text()` for parameterized queries
   - Changed params from Tuple to Dict
3. **daily_etl.py**: 
   - Simplified to extract-only mode for testing
   - Removed transform, load, validate, cleanup, alert tasks
   - DAG renamed to `analytics_daily_etl_extract_only`

---

## Next Steps

Once extract is confirmed working:

1. ✓ Extract phase working
2. ⏳ Add transform phase back
3. ⏳ Add load phase back
4. ⏳ Add validate phase back
5. ⏳ Run full end-to-end pipeline

For now, **extract is isolated and ready to test**.

---

## Debug Mode

To enable detailed logging:

```python
import logging
logging.basicConfig(level=logging.DEBUG)

from analytics.etl.extract import run_extraction
stats = run_extraction("test")
```

This will show SQL queries and detailed connection info.
