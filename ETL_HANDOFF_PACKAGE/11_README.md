# Trading Platform Analytics ETL - README

## Overview

Production-ready modular ETL pipeline for trading analytics. Orchestrated with Apache Airflow, runs hourly with full refresh strategy.

```
OLTP → Extract → Staging → Transform → Analytics (Star Schema)
```

## Architecture

**5 Independent ETL Modules:**
1. **Extract ETL** (Runs :00) - OLTP → Staging
2. **P&L ETL** (Runs :15) - Calculate FIFO P&L → fact_trades
3. **Holdings ETL** (Runs :30) - Daily position snapshots → fact_daily_holdings
4. **Summary ETL** (Runs :45) - Account aggregates → fact_daily_account_summary
5. **Validate ETL** (Runs :55) - Quality checks & reconciliation

## Quick Start (5 minutes)

### 1. Setup Database
```bash
psql -U postgres -d trading_analytics -f schemas/staging_schema.sql
psql -U postgres -d trading_analytics -f schemas/analytics_schema.sql
psql -U postgres -d trading_analytics -f schemas/audit_trail_schema.sql
```

### 2. Setup Python Environment
```bash
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Configure
```bash
cp .env.example .env
# Edit .env with your database credentials
```

### 4. Test Extract
```bash
python -c "
from etl.extract_etl import run_extract
stats = run_extract('test_run_001')
print('Extraction stats:', stats)
"
```

## File Structure

```
analytics_etl/
├── README.md                          (This file)
├── requirements.txt
├── .env.example
├── config.py                          (Database connections & constants)
├── schemas/
│   ├── staging_schema.sql
│   ├── analytics_schema.sql
│   └── audit_trail_schema.sql
├── etl/
│   ├── __init__.py
│   ├── extract_etl.py                 (Extract: OLTP → Staging)
│   ├── transform_pnl_etl.py           (Transform: P&L calculation)
│   ├── transform_holdings_etl.py      (Transform: Holdings snapshots)
│   ├── transform_account_summary_etl.py (Transform: Daily aggregates)
│   └── validate_etl.py                (Validate: Quality checks)
├── dags/
│   └── hourly_etl_dag.py              (Airflow DAG)
├── tests/
│   ├── __init__.py
│   ├── test_extract.py
│   ├── test_pnl.py
│   ├── test_holdings.py
│   ├── test_summary.py
│   └── test_validate.py
└── logs/
    └── etl.log
```

## Running Locally

### Extract Only
```bash
python etl/extract_etl.py local_test_001
```

### P&L Transform
```bash
python etl/transform_pnl_etl.py local_test_001
```

### Full Pipeline (Sequential)
```bash
python -c "
from etl.extract_etl import run_extract
from etl.transform_pnl_etl import run_pnl_etl
from etl.transform_holdings_etl import run_holdings_etl
from etl.transform_account_summary_etl import run_summary_etl
from etl.validate_etl import run_validate

run_id = 'local_test_001'
print('1. Extracting...')
run_extract(run_id)

print('2. P&L Transform...')
run_pnl_etl(run_id)

print('3. Holdings Transform...')
run_holdings_etl(run_id)

print('4. Summary Transform...')
run_summary_etl(run_id)

print('5. Validating...')
run_validate(run_id)

print('Done!')
"
```

## Running on Airflow

### 1. Copy DAG
```bash
cp dags/hourly_etl_dag.py ~/airflow/dags/
```

### 2. Start Airflow
```bash
airflow scheduler &
airflow webserver
```

### 3. Trigger
- Go to http://localhost:8080
- Find `trading_analytics_hourly_etl` DAG
- Click "Trigger DAG"
- Monitor in UI

## Troubleshooting

### "Connection refused"
```bash
# Check DB is running
psql -h localhost -U postgres -d trading_analytics -c "SELECT 1"
```

### "Table does not exist"
```bash
# Re-run schema files
psql -U postgres -d trading_analytics -f schemas/staging_schema.sql
```

### "Airflow DAG not showing"
```bash
# Check syntax
python -m py_compile dags/hourly_etl_dag.py

# Restart scheduler
pkill -f "airflow scheduler"
airflow scheduler
```

### Debug Logs
```bash
tail -f logs/etl.log
```

## Key Modules Explained

### Extract ETL (`transform_pnl_etl.py`)
Pulls raw data from OLTP → staging tables. Runs once per hour.

**Key Method**: `extract_all_data()`
- Clears previous run staging data
- Extracts: exchanges, accounts, instruments, holdings, trades
- Returns: dict of (extracted_count, loaded_count)

### P&L ETL (`transform_pnl_etl.py`) ⚠️ MOST COMPLEX
Calculates P&L using FIFO matching algorithm.

**Key Algorithm**: FIFO Matching
- For each (account, instrument) pair:
  - Match oldest BUY with first SELL
  - Calculate: realized_pnl = (sell_price - buy_price) × qty

**Example**:
```
BUY 100 @ $50   (2026-01-01)
BUY 50 @ $55    (2026-01-02)
SELL 80 @ $60   (2026-01-03) → P&L = (60-50) × 80 = $800
SELL 70 @ $65   (2026-01-04) → P&L = (65-50) × 20 + (65-55) × 50 = $800
```

### Holdings ETL (`transform_holdings_etl.py`)
Creates daily snapshots of portfolio holdings with cost basis & market value.

### Summary ETL (`transform_account_summary_etl.py`)
Aggregates daily account-level metrics: P&L, trade counts, volumes.

### Validate ETL (`validate_etl.py`)
Quality checks: row count reconciliation, P&L validation, anomaly detection.

## Data Schemas

### Staging Layer (Temporary)
- `staging.exchanges_raw`
- `staging.accounts_raw`
- `staging.instruments_raw`
- `staging.holdings_raw`
- `staging.trades_raw`
- `staging.dead_letter_queue` (rejected rows)

### Analytics Layer (Star Schema)
**Dimensions**:
- `dim_exchanges` (trading venues)
- `dim_accounts` (customer accounts)
- `dim_instruments` (securities)
- `dim_dates` (calendar)

**Facts**:
- `fact_trades` (individual transactions + P&L)
- `fact_daily_holdings` (position snapshots)
- `fact_daily_account_summary` (daily aggregates)

**Audit**:
- `audit_trail` (ETL operation log)

## Testing

### Unit Tests
```bash
pytest tests/test_extract.py
pytest tests/test_pnl.py
pytest tests/test_holdings.py
pytest tests/test_summary.py
pytest tests/test_validate.py
```

### Integration Tests
```bash
# Run full pipeline with test data
python -c "
from tests.integration_test import run_full_pipeline
run_full_pipeline()
"
```

## Configuration

All settings in `config.py`:

```python
# Database connections
OLTP_CONNECTION_STRING = "postgresql://..."
OLAP_CONNECTION_STRING = "postgresql://..."

# Data quality thresholds
PRICE_MIN = 0.01
PRICE_MAX = 1_000_000
QUANTITY_MIN = 0.0001
QUANTITY_MAX = 1_000_000

# Processing
BATCH_SIZE = 10000
MAX_RETRIES = 3
```

Override with environment variables (see `.env.example`)

## Monitoring & Alerts

### Check Audit Trail
```sql
SELECT * FROM analytics.audit_trail WHERE DATE(operation_timestamp) = CURRENT_DATE ORDER BY operation_timestamp DESC;
```

### Check Dead-Letter Queue
```sql
SELECT * FROM staging.dead_letter_queue WHERE created_at >= CURRENT_DATE ORDER BY created_at DESC;
```

### View Daily Summary
```sql
SELECT * FROM analytics.vw_audit_daily_summary WHERE etl_date = CURRENT_DATE;
```

## Production Deployment

1. **Database Backups**: Schedule daily backups of OLAP database
2. **Monitoring**: Set up alerts for failed ETL runs
3. **Logging**: Centralize logs to CloudWatch/ELK
4. **Scaling**: Monitor execution time, increase parallelism if needed
5. **SLA**: Target: All ETLs complete within 1-hour window

## Next Steps

1. Implement Holdings ETL (`transform_holdings_etl.py`)
2. Implement Summary ETL (`transform_account_summary_etl.py`)
3. Implement Validate ETL (`validate_etl.py`)
4. Wire Airflow DAG (`dags/hourly_etl_dag.py`)
5. Deploy to production

## References

- **Airflow Docs**: https://airflow.apache.org/docs/
- **SQLAlchemy Docs**: https://docs.sqlalchemy.org/
- **Pandas Docs**: https://pandas.pydata.org/docs/
- **Star Schema**: https://en.wikipedia.org/wiki/Star_schema
- **FIFO Algorithm**: Standard in financial trading systems

## Support

- Check logs: `tail -f logs/etl.log`
- Check DB: `psql -h localhost -U postgres -d trading_analytics`
- Check Airflow: http://localhost:8080

---

**Last Updated**: 2026-09-29  
**Maintainers**: Data Engineering Team
