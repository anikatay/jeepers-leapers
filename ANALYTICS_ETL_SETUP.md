# Analytics ETL Setup Guide

## Status Summary

✅ **COMPLETE**: 
- ETL script created: `analytics/etl/simple_etl.py`
- Environment configuration: `.env` file updated with database URLs
- Database verification script: `analytics/verify_databases.py`
- Dependencies: `sqlalchemy==2.0.23` added to requirements.txt

⚠️ **PENDING**:
- Remote database connectivity (paysprint_analytics database creation)
- ETL script testing

---

## Current Configuration

Your `.env` file is now set up with:

```env
DB_PASSWORD=j33p3rs!
DB_HOST=10.14.141.36

PAYSPRINT_SOURCE_DB_URL=postgresql://paysprint:j33p3rs!@10.14.141.36:5432/paysprint
PAYSPRINT_ANALYTICS_DB_URL=postgresql://paysprint:j33p3rs!@10.14.141.36:5432/paysprint_analytics
```

### Database Requirements

Your ETL script requires:
1. **Source Database (OLTP)**: `paysprint` at `10.14.141.36:5432`
   - Must exist and contain: exchanges, accounts, instruments, trades tables
   
2. **Analytics Database (OLAP/Staging)**: `paysprint_analytics` at `10.14.141.36:5432`
   - Will be auto-created if missing (when script runs)
   - Will contain staging tables: exchanges_raw, accounts_raw, instruments_raw, trades_raw

---

## Setup Steps

### Step 1: Verify Remote Database Access

From the machine where the script will run, test connectivity:

```bash
# Option A: Using psql (if PostgreSQL client tools installed)
psql -h 10.14.141.36 -U paysprint -d paysprint -c "SELECT 1"

# Option B: Using Python (verified to work)
python analytics/verify_databases.py
```

**Expected Result**: Should connect successfully or show clear error message.

---

### Step 2: Create paysprint_analytics Database (if needed)

If the remote database isn't accessible from your local machine, you'll need to create it on the remote server:

```bash
# SSH to remote database server
ssh ec2-user@10.14.142.75

# Connect to PostgreSQL
psql -h localhost -U paysprint -d postgres

# Create analytics database
CREATE DATABASE paysprint_analytics;
```

Or run the verification script from a machine that has network access to `10.14.141.36:5432`:

```bash
python analytics/verify_databases.py
```

---

### Step 3: Verify ETL Script Works

Once databases are accessible, test the complete ETL:

```bash
# From project root
python analytics/etl/simple_etl.py
```

**Expected Output**:
```
============================================================
PHASE 1: EXTRACT
============================================================
[...] Connecting to paysprint (source) database...
[...] Extracted N exchanges
[...] Extracted N accounts
[...] Extracted N instruments
[...] Extracted N trades from last 90 days

============================================================
PHASE 2: TRANSFORM
============================================================
[...] Transformed exchanges: added 3 metadata columns
[...] Transformed accounts: added 3 metadata columns
[...] Transformed instruments: added 3 metadata columns
[...] Transformed trades: added 3 metadata columns

============================================================
PHASE 3: LOAD
============================================================
[...] Loading N rows to staging.exchanges_raw...
[...] Successfully loaded N rows to staging.exchanges_raw
[...] Loading N rows to staging.accounts_raw...
[...] Successfully loaded N rows to staging.accounts_raw
[...] Loading N rows to staging.instruments_raw...
[...] Successfully loaded N rows to staging.instruments_raw
[...] Loading N rows to staging.trades_raw...
[...] Successfully loaded N rows to staging.trades_raw

============================================================
SUMMARY: {"status": "success", "run_id": "...", "rows_loaded": {...}}
============================================================
```

---

### Step 4: Verify Data in Staging Tables

Once the script runs successfully, verify the data was loaded:

```sql
-- Connect to paysprint_analytics
psql -h 10.14.141.36 -U paysprint -d paysprint_analytics

-- Check staging tables
SELECT COUNT(*) as exchange_count FROM staging.exchanges_raw;
SELECT COUNT(*) as account_count FROM staging.accounts_raw;
SELECT COUNT(*) as instrument_count FROM staging.instruments_raw;
SELECT COUNT(*) as trade_count FROM staging.trades_raw;

-- Verify metadata columns exist and are populated
SELECT 
    DISTINCT etl_run_id, 
    etl_timestamp, 
    source_table 
FROM staging.trades_raw 
LIMIT 1;
```

---

## Files Created/Modified

### New Files
- `analytics/etl/simple_etl.py` (350+ lines) — Main ETL pipeline
- `analytics/etl/__init__.py` — Package marker
- `analytics/verify_databases.py` — Database verification script
- `.env.example` — Configuration template

### Modified Files
- `.env` — Added PAYSPRINT_SOURCE_DB_URL and PAYSPRINT_ANALYTICS_DB_URL
- `analytics/requirements.txt` — Added sqlalchemy==2.0.23

---

## Troubleshooting

### Error: Connection refused to 10.14.141.36:5432

**Cause**: Remote database is not accessible from your machine.

**Solutions**:
1. Check if remote server is running and PostgreSQL is listening on port 5432
2. Check firewall rules allow TCP port 5432 from your IP
3. SSH to remote server and run verification script from there
4. Verify DB_HOST and DB_PASSWORD are correct

### Error: paysprint database not found

**Cause**: Source OLTP database doesn't exist on remote server.

**Solution**: Database must be created and populated separately (outside scope of this ETL setup).

### Error: psycopg2-binary not installed

**Solution**:
```bash
pip install -q psycopg2-binary
```

---

## Integration with Airflow (Future)

Once databases are verified and ETL script works, it can be integrated into Airflow DAGs:

```python
from airflow import DAG
from airflow.operators.python import PythonOperator
from analytics.etl.simple_etl import run_etl

def etl_task():
    result = run_etl()
    return result

with DAG('analytics_etl_daily', schedule_interval='0 1 * * *') as dag:
    extract_transform_load = PythonOperator(
        task_id='etl',
        python_callable=etl_task
    )
```

---

## Next Actions for You

1. ✅ `.env` file is ready with credentials
2. ⏳ **Verify database connectivity** from the environment where the script will run
3. ⏳ **Create paysprint_analytics database** if it doesn't exist on remote server
4. ⏳ **Run ETL script** to test: `python analytics/etl/simple_etl.py`
5. ⏳ **Verify data** in staging tables using SQL queries above

---

## Questions or Issues?

- Check script logs: `analytics/etl/etl.log`
- Review ETL logic in: `analytics/etl/simple_etl.py`
- Verify environment vars: `python -c "import os; from dotenv import load_dotenv; load_dotenv(); print('PAYSPRINT_ANALYTICS_DB_URL:', os.getenv('PAYSPRINT_ANALYTICS_DB_URL'))"`
