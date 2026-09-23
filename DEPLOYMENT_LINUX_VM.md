# Analytics ETL - Linux VM Deployment Guide

## Quick Start (Linux VM)

This guide assumes you're deploying to a Linux VM with docker-compose already running `paysprint` database.

### Step 1: Create Analytics Database

```bash
# SSH into your Linux VM
ssh <user>@<vm-ip>

# Navigate to project
cd jeepers-leapers

# Create the paysprint_analytics database
docker-compose exec db psql -U paysprint -d postgres -c "CREATE DATABASE paysprint_analytics OWNER paysprint;"

# Verify
docker-compose exec db psql -U paysprint -d paysprint_analytics -c "SELECT 1;"
```

### Step 2: Deploy Analytics Schemas

```bash
# Deploy OLAP schema
docker-compose exec db psql -U paysprint -d paysprint_analytics < analytics/schema/olap_schema.sql

# Deploy Staging schema
docker-compose exec db psql -U paysprint -d paysprint_analytics < analytics/schema/staging_schema.sql

# Verify tables exist
docker-compose exec db psql -U paysprint -d paysprint_analytics -c "
  SELECT table_name FROM information_schema.tables 
  WHERE table_schema = 'analytics' AND table_type = 'BASE TABLE';"
```

### Step 3: Start All Services (Including Airflow)

```bash
# Start all services (app, db, frontend, airflow)
docker-compose up -d

# Or follow logs in real-time
docker-compose up

# Verify all services are running
docker-compose ps
```

Services running:
- `app` - Spring backend (port 8095)
- `db` - PostgreSQL (port 8100 external, internal 5432)
- `frontend` - Angular frontend (port 8090)
- `airflow-webserver` - Airflow UI (port 8082)
- `airflow-scheduler` - DAG scheduler (no external port)

### Step 4: Access Airflow UI

From your Windows machine:

```
http://<vm-ip>:8082

Username: airflow
Password: airflow
```

Find the DAG: `analytics_daily_etl`

### Step 5: Trigger ETL (Manual Test)

Option A - Via Airflow UI:
1. Go to http://<vm-ip>:8080/home
2. Find "analytics_daily_etl" DAG
3. Click "Trigger DAG"
4. Watch execution in UI

Option B - Via CLI:
```bash
docker-compose exec airflow-webserver airflow dags trigger analytics_daily_etl
```

### Step 6: Verify Results

After ETL runs, check analytics database:

```bash
# Connect to analytics database
docker-compose exec db psql -U paysprint -d paysprint_analytics

# Check fact tables have data
SELECT COUNT(*) FROM analytics.fact_trades;
SELECT COUNT(*) FROM analytics.fact_daily_holdings;
SELECT COUNT(*) FROM analytics.fact_daily_account_summary;

# Check P&L calculations
SELECT account_id, sum(realized_pnl) as total_realized_pnl
FROM analytics.fact_trades
GROUP BY account_id LIMIT 5;
```

---

## Networking Explained

When running in docker-compose:
- **OLTP Database**: Service name `db`, accessible internally as `db:5432`
- **Analytics Database**: Same `db` service, different database name `paysprint_analytics`
- **Airflow**: Service names `airflow-webserver`, `airflow-scheduler`, accessible as `http://airflow-webserver:8080` internally
- **External Access**: 
  - Airflow UI: `http://<vm-ip>:8080`
  - Database (from Windows): `localhost:8100` (if forwarded) or `<vm-ip>:5432` (if exposed)

The `ENVIRONMENT=docker` variable in docker-compose tells the ETL code to use service names (`db`) instead of localhost.

---

## Troubleshooting

### Airflow won't start
```bash
# Check logs
docker-compose logs airflow-webserver
docker-compose logs airflow-scheduler

# Check if ports are in use
docker-compose ps

# Rebuild images
docker-compose up -d --build airflow-webserver airflow-scheduler
```

### ETL fails with database connection error
```bash
# Verify analytics database exists
docker-compose exec db psql -U paysprint -d postgres -c "\l"

# Check schemas created
docker-compose exec db psql -U paysprint -d paysprint_analytics -c "\dn"

# Test connection from ETL container
docker-compose exec airflow-webserver psql -h db -U paysprint -d paysprint_analytics -c "SELECT 1;"
```

### Port 8080 already in use
Update docker-compose.yml to use different port:
```yaml
airflow-webserver:
  ports:
    - "8081:8080"  # Use 8081 instead
```

### Database schema deployment failed
```bash
# Check what went wrong
docker-compose exec db psql -U paysprint -d paysprint_analytics -c "\dt"

# If tables don't exist, try deploying manually
docker-compose exec db bash -c 'psql -U paysprint -d paysprint_analytics < /dev/stdin' < analytics/schema/olap_schema.sql
```

---

## Development vs Production

### Development Setup (Windows Machine)
- OLTP: `localhost:8100` (port-forwarded from docker-compose)
- Analytics: `localhost:8100` (same container)
- ETL: Run manually with `python -m etl.extract`

```bash
$env:DB_HOST = "localhost"
$env:DB_PORT = "8100"
$env:ENVIRONMENT = "local"
python -m analytics.etl.extract
```

### Production Setup (Linux VM)
- OLTP: `db:5432` (internal service name)
- Analytics: `db:5432` (same service, different database)
- ETL: Runs in Airflow container

```yaml
environment:
  DB_HOST: db
  ENVIRONMENT: docker
```

---

## Next Steps

1. **Monitor Airflow DAG**: Watch the first run complete successfully
2. **Spot-check P&L**: Manually verify 5 trades have correct P&L calculations
3. **Set up alerting**: Configure email/Slack notifications for DAG failures
4. **Scale analytics**: Add more complex views once data is flowing
5. **Phase 2**: Implement hourly summaries (optional enhancement)

---

## Support

- **Airflow Logs**: `docker-compose logs -f airflow-scheduler`
- **Database Logs**: `docker-compose logs -f db`
- **Database Issues**: Check `staging.dead_letter_queue` for rejected rows
- **Reconciliation**: Run validation query from README.md

