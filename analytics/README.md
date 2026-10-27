# Analytics ETL Pipeline - Phases 1 & 3 Testing Guide

Complete, tested ETL modules for the trading analytics pipeline. This guide shows how to test **Phase 1 (Extraction)** and **Phase 3 (P&L Transformation)** end-to-end.

## Quick Start

### Prerequisites
- Python 3.9+ with `pip install -r analytics/requirements.txt`
- The stack running: `docker-compose up -d --build` (PostgreSQL 16 on port 8100, app, frontend)
- `.env` in the repo root with `PAYSPRINT_SOURCE_DB_URL` and `PAYSPRINT_ANALYTICS_DB_URL` (see `.env.example`)

### Run the Full Pipeline

```bash
cd ~/jeepers-leapers

# Runs Phase 0 → 1 → 2 → 3 on the trades already in OLTP
python3 analytics/etl/run_pipeline.py

# Verify P&L data
docker exec jeepers-leapers-db-1 psql -U paysprint -d paysprint_analytics -c "
  SELECT account_id, instrument_id, side, quantity,
         realized_pnl, unrealized_pnl, pnl_status
  FROM analytics.fact_trades
  ORDER BY executed_at;
"
```

What the orchestrator does:
- **Phase 0** creates the `paysprint_analytics` database (if missing) and the staging schema.
- **Phase 1** waits for the app to finish creating the OLTP tables, then extracts them to staging.
- **Phase 2** drops and recreates the analytics dimension and fact tables (full refresh, so the
  database always matches `schema/01_ANALYTICS_SCHEMA.sql`) and loads the dimensions.
- **Phase 3** loads `analytics.fact_trades` with FIFO P&L.

The pipeline exits non-zero if any phase fails.

Test trades are **not** inserted unless you ask for them, because seeding writes into the OLTP
database and every run adds new rows:

```bash
python3 analytics/etl/run_pipeline.py --seed               # insert 50 test trades first
python3 analytics/etl/run_pipeline.py --seed --trades 100  # insert 100
```

Seeded trades move cash and holdings the way trades placed in the app do. Each quantity is cut
down to what the account can afford or sell and a trade it cannot make is skipped, so fewer
trades than requested may be inserted. Use a `--start-date` after the trades already in the
database so the seeded sells have purchases behind them.

### Triggering a Refresh from an API (e.g. an admin "P&L analytics" button)

The intended flow is: trigger the pipeline, wait for it to return, then read the admin views.

**1. Run the pipeline.** From Python, call the function. It never raises or exits:

```python
from etl.run_pipeline import run_pipeline   # with analytics/ on sys.path

result = run_pipeline()
# {'status': 'success', 'run_id': 'pipeline-run-20261023-120000',
#  'phases': [...], 'summary': {'trades_loaded': 668, ...},
#  'error': None, 'duration_seconds': 0.9}
```

The analytics container also exposes the same operation for the Spring app:

```text
POST http://etl:8094/refresh
```

The endpoint runs synchronously and returns the pipeline JSON. The `status` can be
`success`, `busy`, or `failed`; the Spring app maps those states to the admin refresh API.
The endpoint is available only on the Docker network and is not published to the host.

From anything else, run the script and read the JSON it prints on stdout (logs go to stderr):

```bash
python3 analytics/etl/run_pipeline.py --json
```

| `status` | Exit code | Meaning |
|----------|-----------|---------|
| `success` | 0 | All phases ran; the views below are up to date |
| `failed` | 1 | A phase failed; `error` says which and why |
| `busy` | 2 | Another run holds the lock; nothing was changed. Wait and read the views |

Only one run can happen at a time (a Postgres advisory lock), so two clicks cannot collide.

**2. Read the results.** Phase 2 creates these views in `paysprint_analytics`. Each is aggregated
across all accounts and is a snapshot of the last run:

| View | Rows | Use for |
|------|------|---------|
| `analytics.v_pnl_summary` | Always 1 | Headline totals: trades, traded value, realized / unrealized / total P&L, `refreshed_at` |
| `analytics.v_pnl_by_instrument` | 1 per instrument | P&L and volume per ticker |
| `analytics.v_pnl_by_account` | 1 per account | P&L and volume per account |
| `analytics.v_pnl_by_day` | 1 per trading date | Trades and realized P&L per day, with a running total |

```sql
SELECT * FROM analytics.v_pnl_summary;
SELECT ticker, realized_pnl, unrealized_pnl, total_pnl FROM analytics.v_pnl_by_instrument ORDER BY total_pnl DESC;
SELECT executed_date, realized_pnl, cumulative_realized_pnl FROM analytics.v_pnl_by_day ORDER BY executed_date;
```

Unrealized P&L is valued at each instrument's `current_price` at the time of the run, so it has no
history; `v_pnl_by_day` covers realized P&L only. For a moment during a refresh the fact table is
empty, so read the views after the run returns rather than while it is in progress.

The steps below run each phase by hand (from the `analytics/` directory).

---

## Step-by-Step Testing

### Step 1: Seed Trade Data

The OLTP database starts with no trades. Generate test data:

```bash
cd ~/jeepers-leapers/analytics

# Generate 50 trades (default)
python3 -c "from etl.seed_trades import seed_trades_main; seed_trades_main()"

# Or generate more trades with custom date
python3 -c "from etl.seed_trades import seed_trades_main; seed_trades_main(num_trades=100, start_date='2025-09-01')"
```

**Expected output:**
```
================================================================================
TRADE DATA SEEDER
================================================================================

Generated 50 trades. Inserting into OLTP database...

✓ BUY    1 shares of AAPL @ $ 221.00 | Alice
✓ SELL   8 shares of GOOGL @ $ 170.50 | Bob
✓ BUY   15 shares of MSFT @ $ 425.00 | Charlie
...

================================================================================
INSERTION SUMMARY
================================================================================
Total trades generated:  50
Successfully inserted:   50
Failed:                  0

Trades by Account:
  Alice        17 trades
  Bob          17 trades
  Charlie      16 trades

Trades by Side:
  BUY         25 trades
  SELL        25 trades
================================================================================
```

**What's created:**
- 50 trades in `paysprint.public.trades` (OLTP)
- Mix of BUY/SELL orders across 3 accounts and 7 instruments
- Realistic prices and quantities

---

### Step 2: Test Phase 1 - Extraction

Extract reference data and trades from OLTP to staging tables:

```bash
python3 -c "from etl.extract_etl import run_etl; result = run_etl(); print(result)"
```

**Expected output:**
```python
{
  'exchanges': (2, 2),      # 2 exchanges extracted and loaded
  'accounts': (5, 5),       # 5 accounts extracted and loaded
  'instruments': (7, 7),    # 7 instruments extracted and loaded
  'holdings': (8, 8),       # 8 holdings extracted and loaded
  'trades': (50, 50)        # 50 trades extracted and loaded (after seeding)
}
```

**What happens in Phase 1:**
1. Connects to OLTP database (`paysprint.public`)
2. Queries all exchanges, accounts, instruments, holdings
3. Queries all trades (set `ETL_LOOKBACK_DAYS` to limit to the last N days)
4. Adds metadata columns: `etl_run_id`, `etl_timestamp`, `source_table`
5. Loads to `paysprint_analytics.staging.*_raw` tables
6. Returns row counts for verification

**Verify in database:**
```bash
docker exec -e PGPASSWORD=j33p3rs! jeepers-leapers-db-1 psql -U paysprint -d paysprint_analytics -c "
  SELECT table_name, COUNT(*) as row_count FROM (
    SELECT 'exchanges' as table_name, COUNT(*) FROM staging.exchanges_raw
    UNION ALL SELECT 'accounts', COUNT(*) FROM staging.accounts_raw
    UNION ALL SELECT 'instruments', COUNT(*) FROM staging.instruments_raw
    UNION ALL SELECT 'holdings', COUNT(*) FROM staging.holdings_raw
    UNION ALL SELECT 'trades', COUNT(*) FROM staging.trades_raw
  ) t
  GROUP BY table_name
  ORDER BY table_name;
"
```

---

### Step 3: Test Phase 3 - P&L Transformation

Calculate profit/loss using FIFO matching algorithm:

```bash
python3 -c "from etl.transform_pnl import run_pnl_transformation; result = run_pnl_transformation('test-run-001'); print(result)"
```

**Expected output (with 50 trades seeded):**
```python
{
  'status': 'success',
  'trades_processed': 50,
  'trades_loaded': 50,
  'matched_trades': 25,      # Matched BUY/SELL pairs
  'orphan_trades': 25,       # Unmatched trades
  'error': None
}
```

**What happens in Phase 3:**
1. Reads all trades from `staging.trades_raw`
2. Groups trades by account and instrument
3. Applies FIFO (First-In-First-Out) matching algorithm:
   - Matches SELL orders with earliest BUY orders
   - Calculates: `realized_pnl = (exit_price - entry_price) × quantity`
   - Leaves unmatched BUY orders as orphans
4. Loads matched trades to `analytics.fact_trades`
5. Returns match statistics

**FIFO Matching Example:**
```
Account: Alice, Instrument: AAPL
  BUY  10 @ $150.00  (entry_price = 150.00)
  BUY  5  @ $155.00  (entry_price = 155.00)
  SELL 12 @ $160.00  ← Matches 10 from first BUY + 2 from second BUY

Results:
  Trade 1: BUY 10, SELL 12 (8 shares matched)
    realized_pnl = (160 - 150) × 10 = $100.00
  Trade 2: BUY 5, SELL 2 (partial match)
    realized_pnl = (160 - 155) × 2 = $10.00
  Orphan: 3 unmatched shares from second BUY (unrealized_pnl = -15.00)
```

---

### Step 4: Verify Results

Query the P&L data loaded to analytics:

```bash
docker exec -e PGPASSWORD=j33p3rs! jeepers-leapers-db-1 psql -U paysprint -d paysprint_analytics << EOF
SELECT 
    account_id,
    instrument_id,
    side,
    quantity,
    entry_price,
    exit_price,
    realized_pnl,
    unrealized_pnl,
    pnl_status,
    executed_at
FROM analytics.fact_trades
ORDER BY executed_at, account_id, instrument_id
LIMIT 20;
EOF
```

**Expected columns:**
- `account_id` — Customer account
- `instrument_id` — Stock/instrument traded
- `side` — BUY or SELL
- `quantity` — Number of shares
- `entry_price` — Price at entry (BUY price for matched trades)
- `exit_price` — Price at exit (SELL price for matched trades)
- `realized_pnl` — Profit/loss if trade was matched
- `unrealized_pnl` — Estimated P&L for orphan trades
- `pnl_status` — MATCHED or ORPHAN
- `executed_at` — Trade timestamp

---

## What Each Phase Does

### Phase 1: Extraction (`extract_etl.py`)

**Purpose:** Move data from OLTP to staging layer  
**Source:** `paysprint.public` (operational database)  
**Target:** `paysprint_analytics.staging.*_raw` (staging schema)

| Table | Source | Rows | Filter |
|-------|--------|------|--------|
| exchanges | public.exchanges | 2 | All |
| accounts | public.accounts | 5 | All |
| instruments | public.instruments | 7 | All |
| holdings | public.holdings | 8 | All |
| trades | public.trades | 50+ | Full history (FIFO needs every BUY) |

### Phase 3: P&L Transformation (`transform_pnl.py`)

**Purpose:** Calculate realized and unrealized profit/loss  
**Algorithm:** FIFO (First-In-First-Out) matching  
**Source:** `staging.trades_raw`  
**Target:** `analytics.fact_trades`

**Key Formula:**
```
realized_pnl = (exit_price - entry_price) × quantity_matched
unrealized_pnl = (current_price - entry_price) × quantity_unmatched
```

**Matching Logic:**
1. Group trades by account + instrument + side
2. For each SELL order, match with earliest BUY orders (FIFO)
3. Calculate realized P&L for matched pairs
4. Leave unmatched BUY orders as orphans

---

## Running Tests

### Unit Tests

```bash
# Run all tests
pytest tests/test_transform_pnl.py -v

# Run specific test class
pytest tests/test_transform_pnl.py::TestFIFOMatcher -v

# Run with coverage
pytest tests/test_transform_pnl.py --cov=etl.transform_pnl --cov-report=term-missing
```

**Test Results (11 passing):**
```
test_transform_pnl.py::TestTradePosition::test_trade_position_creation PASSED
test_transform_pnl.py::TestTradePosition::test_trade_position_repr PASSED
test_transform_pnl.py::TestFIFOMatcher::test_simple_match PASSED
test_transform_pnl.py::TestFIFOMatcher::test_multiple_buys_single_sell PASSED
test_transform_pnl.py::TestFIFOMatcher::test_unmatched_positions PASSED
test_transform_pnl.py::TestFIFOMatcher::test_orphan_trades PASSED
test_transform_pnl.py::TestFIFOMatcher::test_pnl_calculation PASSED
test_transform_pnl.py::TestCalculatePnLFifo::test_main_calculation PASSED
test_transform_pnl.py::TestRunPnLTransformation::test_success_path PASSED
test_transform_pnl.py::TestRunPnLTransformation::test_empty_trades PASSED
test_transform_pnl.py::TestRunPnLTransformation::test_error_handling PASSED

======================== 11 passed in 0.45s ========================
```

### Integration Tests (Full Pipeline)

```bash
# Run complete ETL pipeline on test data
cd ~/jeepers-leapers/analytics

# 1. Seed fresh data
python3 -c "from etl.seed_trades import seed_trades_main; seed_trades_main(num_trades=50)"

# 2. Extract
python3 -c "from etl.extract_etl import run_etl; print(run_etl())"

# 3. Transform
python3 -c "from etl.transform_pnl import run_pnl_transformation; print(run_pnl_transformation('test-run'))"

# 4. Verify row counts match
echo "Expected: 50 trades extracted and loaded"
```

---

## Troubleshooting

### "No trades found in staging.trades_raw"
**Cause:** Phase 1 hasn't been run after seeding trades  
**Fix:**
```bash
python3 -c "from etl.extract_etl import run_etl; print(run_etl())"
```

### "Table trades_raw not found"
**Cause:** Phase 0 (schema initialization) hasn't run  
**Fix:**
```bash
python3 -c "from etl.schema_init import initialize_database; initialize_database()"
```

### "Connection refused" or "password authentication failed"
**Cause:** Database credentials or connection string not set  
**Fix:**
```bash
# Set environment variables
export PAYSPRINT_SOURCE_DB_URL="postgresql://paysprint:<password>@localhost:8100/paysprint"
export PAYSPRINT_ANALYTICS_DB_URL="postgresql://paysprint:<password>@localhost:8100/paysprint_analytics"

# Verify connection
python3 -c "from etl.config import get_oltp_engine; engine = get_oltp_engine(); print('✓ Connected')"
```

---

## Files Overview

```
analytics/
├── etl/
│   ├── __init__.py
│   ├── config.py              # Database connection setup
│   ├── schema_init.py         # Phase 0: Create staging schema
│   ├── extract_etl.py         # Phase 1: Extract data to staging
│   ├── transform_pnl.py       # Phase 3: FIFO P&L calculation
│   └── seed_trades.py         # Generate test trade data
├── tests/
│   ├── test_extract_etl.py    # Phase 1 tests
│   ├── test_transform_pnl.py  # Phase 3 tests (11 passing)
│   └── conftest.py
├── schema/
│   └── 01_ANALYTICS_SCHEMA.sql # Phase 2: Create analytics schema
└── README.md                  # This file
```

---

## Database Schema

### OLTP (`paysprint.public`)
- `users` — Customer accounts
- `accounts` — Trading accounts
- `instruments` — Stocks/securities
- `exchanges` — Stock exchanges
- `trades` — Trade records
- `holdings` — Current positions

### Staging (`paysprint_analytics.staging`)
- `exchanges_raw` — Extracted exchanges
- `accounts_raw` — Extracted accounts
- `instruments_raw` — Extracted instruments
- `holdings_raw` — Extracted holdings
- `trades_raw` — Extracted trades

### Analytics (`paysprint_analytics.analytics`)
- `dim_exchanges` — Exchange dimension
- `dim_accounts` — Account dimension
- `dim_instruments` — Instrument dimension
- `dim_dates` — Date dimension (1,827 rows for 2024-2028)
- `fact_trades` — **P&L data** (Phase 3 output)
- `fact_daily_holdings` — Position snapshots
- `fact_daily_account_summary` — Account-level aggregates

---

## Next Steps

After validating Phase 1 and Phase 3:

- [ ] Phase 2: Create analytics schema
- [ ] Phase 4: Holdings transformation (daily position snapshots)
- [ ] Phase 5: Account summary transformation (daily aggregates)
- [ ] Phase 6: Validation & reconciliation
- [ ] Phase 7: Airflow DAG orchestration
- [ ] Phase 8: Production deployment