# Quick Start Guide - ETL Setup in 30 Minutes

## Prerequisites
- PostgreSQL database (OLTP + OLAP)
- Python 3.9+
- Apache Airflow 2.0+

## Step 1: Create Database Schemas (5 min)

```bash
# Connect to your OLAP database
psql -U your_user -d your_analytics_db -f schemas/staging_schema.sql
psql -U your_user -d your_analytics_db -f schemas/analytics_schema.sql
psql -U your_user -d your_analytics_db -f schemas/audit_trail_schema.sql
```

## Step 2: Setup Python Environment (5 min)

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Create .env from template
cp .env.example .env
# Edit .env with your database credentials
```

## Step 3: Test Config & Connections (5 min)

```bash
# Test OLTP connection
python -c "from config import get_oltp_engine; engine = get_oltp_engine(); conn = engine.connect(); print('✓ OLTP connected')"

# Test OLAP connection
python -c "from config import get_olap_engine; engine = get_olap_engine(); conn = engine.connect(); print('✓ OLAP connected')"

# Test Staging connection
python -c "from config import get_staging_engine; engine = get_staging_engine(); conn = engine.connect(); print('✓ Staging connected')"
```

## Step 4: Test Extract ETL (5 min)

```bash
# Run extract manually
python -c "
from etl.extract_etl import DataExtractor
extractor = DataExtractor('test_run_001')
stats = extractor.extract_all_data()
print('Extraction stats:', stats)
"

# Verify data landed in staging
psql -U your_user -d your_analytics_db -c "SELECT COUNT(*) as trades FROM staging.trades_raw;"
```

## Step 5: Deploy to Airflow (5 min)

```bash
# Copy DAG to Airflow DAGs folder
cp dags/hourly_etl_dag.py ~/airflow/dags/

# Restart Airflow scheduler
airflow scheduler

# In separate terminal, start webserver
airflow webserver

# Go to http://localhost:8080
# Find 'trading_analytics_hourly_etl' DAG
# Trigger manually to test
```

## Troubleshooting

**"Connection refused" error**
- Check DB host/port in .env
- Verify PostgreSQL is running

**"Retrying connection..."**
- Check DB credentials
- Verify user has permissions on databases

**"Table does not exist"**
- Run schema SQL files again
- Check schema name in config.py

**Airflow DAG not showing up**
- Copy DAG file to ~/airflow/dags/
- Restart Airflow scheduler
- Check for syntax errors: `python -m py_compile dags/hourly_etl_dag.py`

---

## Next: Build the ETL Modules

Once this works, follow 01_PROMPT_FOR_AGENT.md to build:
1. Extract ETL (complete but test it)
2. P&L ETL (most complex)
3. Holdings ETL
4. Summary ETL
5. Validate ETL
