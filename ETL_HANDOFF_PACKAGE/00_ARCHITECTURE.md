# Trading Platform Analytics ETL - Complete Architecture

## EXECUTIVE SUMMARY

Build a **modular, hourly ETL pipeline** that transforms raw trading data (OLTP) into analytics-ready star schema (OLAP). Each feature gets its own ETL, sharing a common Extract layer.

**Tech Stack**: Python 3.9+, PostgreSQL, Apache Airflow, Pandas, SQLAlchemy  
**Data Volume**: < 100K rows/day  
**Update Frequency**: Hourly  
**Load Strategy**: Full refresh (delete all facts, reload each hour)

---

## ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────────────────┐
│                      TRADING PLATFORM OLTP                      │
│  (users, accounts, instruments, exchanges, holdings, trades)    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │   EXTRACT   │ (Runs @ :00)
                    │   SHARED    │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
        ┌──────────────────┐    ┌──────────────────┐
        │  STAGING SCHEMA  │    │  ANALYTICS SCHEMA│
        │ (temporary data) │    │ (star schema)    │
        └────────┬─────────┘    └────────▲─────────┘
                 │                       │
    ┌────────────┼───────────────────────┤
    ▼            ▼                       │
 ┌──────┐ ┌────────────────────┐        │
 │PnL   │ │Holdings │ Account  │        │
 │ETL   │ │ETL      │ Summary  │        │
 │(:15) │ │(:30)    │ ETL(:45) │        │
 └──┬───┘ └────┬─────┴────┬────┘        │
    │          │          │             │
    └──────────┼──────────┴─────────────┤
               ▼                        │
         ┌───────────────┐              │
         │  VALIDATE ETL │ (:55)        │
         │ (reconcile)   │              │
         └───────────────┘              │
                                        │
         ┌──────────────────────────────┘
         ▼
    ANALYTICS READY
    (dashboards, reports, data science)
```

---

## DATA MODELS

### OLTP SCHEMA (Source)
```
users (user_id, role, status)
  ├─ accounts (account_id, user_id, balance, currency, status, created_at)
  │   ├─ holdings (account_id, instrument_id, quantity)
  │   └─ trades (trade_id, account_id, instrument_id, side, qty, price, executed_at)
  └─ user_pii (user_id, name, ssn, email)

instruments (instrument_id, ticker, exchange_id, current_price, updated_at)
  └─ exchanges (exchange_id, name, region, timezone, currency)
```

### STAGING SCHEMA (Working Area - Cleared Each Run)
```
staging.*_raw (temporary raw staging tables)
├─ exchanges_raw (exchange_id, name, region, timezone, currency, etl_run_id)
├─ accounts_raw (account_id, user_id, status, currency, balance, created_at, etl_run_id)
├─ instruments_raw (instrument_id, ticker, exchange_id, current_price, created_at, etl_run_id)
├─ holdings_raw (account_id, instrument_id, quantity, etl_run_id)
└─ trades_raw (trade_id, account_id, instrument_id, side, qty, price, executed_at, etl_run_id)

staging.dead_letter_queue (dlq_id, etl_run_id, operation, source_table, rejection_reason, raw_data)
```

### ANALYTICS SCHEMA (Star Schema - OLAP)
```
DIMENSIONS (Type 1 SCD: current state only)
├─ dim_exchanges (exchange_id, name, region, timezone, currency)
├─ dim_accounts (account_id, user_id, status, currency, created_at)
├─ dim_instruments (instrument_id, ticker, exchange_id, current_price, sector, type)
└─ dim_dates (date_key, year, quarter, month, day_name, is_trading_day, etc.)

FACTS
├─ fact_trades (trade_id, account_id, instrument_id, side, qty, price, realized_pnl, unrealized_pnl)
├─ fact_daily_holdings (account_id, instrument_id, snapshot_date, qty, cost_basis, market_value, unrealized_pnl)
└─ fact_daily_account_summary (account_id, summary_date, daily_pnl, cumulative_pnl, num_trades, total_volume)

AUDIT
└─ audit_trail (operation_timestamp, etl_run_id, table_name, operation, rows_affected, status)
```

---

## ETL MODULES (5 TOTAL)

### 1. EXTRACT ETL (`extract_etl.py`)
**Runs**: Hourly @ :00  
**Purpose**: Pull raw data from OLTP → Staging schema  
**Output**: 5 raw staging tables

**Logic**:
```
FOR each table (exchanges, accounts, instruments, holdings, trades):
  1. Load data from OLTP
  2. Add etl_run_id for tracking
  3. Truncate staging table from previous run
  4. Insert raw data to staging
  5. Log extraction stats (rows extracted)
```

**Error Handling**: Retry on DB errors, log to DLQ on data issues

---

### 2. P&L ETL (`transform_pnl_etl.py`)
**Runs**: Hourly @ :15 (after Extract)  
**Purpose**: Calculate profit/loss and load fact_trades  
**Output**: analytics.fact_trades

**Complexity**: HIGH ⚠️

**Logic**:
```
1. LOAD trades from staging.trades_raw

2. VALIDATE each trade:
   - Price: $0.01 - $1,000,000
   - Quantity: 0.0001 - 1,000,000 shares
   - Date: no future dates (> 1 day ahead)
   → Bad records → dead_letter_queue

3. CALCULATE REALIZED P&L (FIFO matching):
   FOR each (account_id, instrument_id):
     - Sort BUY trades by executed_at (oldest first)
     - Sort SELL trades by executed_at (oldest first)
     - Match oldest BUY with first SELL (FIFO)
     - For each SELL: realized_pnl = (sell_price - buy_price) × matched_qty
     - Update fact_trades with realized_pnl
   
   EXAMPLE:
   BUY 100 @ $50 (2026-01-01)
   BUY 50 @ $55 (2026-01-02)
   SELL 80 @ $60 (2026-01-03)  → P&L = (60-50) × 80 = $800
   SELL 70 @ $65 (2026-01-04)  → P&L = (65-50) × 20 + (65-55) × 50 = $800

4. CALCULATE UNREALIZED P&L:
   FOR each open position (BUY not yet SOLD):
     - unrealized_pnl = (current_price - buy_price) × quantity

5. LOAD to analytics.fact_trades:
   - DELETE all existing fact_trades
   - INSERT all new fact_trades (with realized + unrealized P&L)
   - Wrapped in transaction (all-or-nothing)

6. VALIDATE:
   - No NULLs in key columns
   - P&L values reasonable
   - Row count matches ± 5%
```

---

### 3. HOLDINGS ETL (`transform_holdings_etl.py`)
**Runs**: Hourly @ :30 (after P&L)  
**Purpose**: Calculate daily position snapshots  
**Output**: analytics.fact_daily_holdings

**Complexity**: MEDIUM

**Logic**:
```
1. LOAD holdings from staging.holdings_raw + trades from staging.trades_raw

2. FOR each (account_id, instrument_id) position:
   
   CALCULATE COST BASIS (average price using FIFO):
   - Filter trades for this account/instrument
   - Separate BUY trades (sorted by date)
   - Calculate: cost_basis = total_cost / total_quantity
   
   EXAMPLE:
   BUY 100 @ $50 = cost $5,000
   BUY 50 @ $55 = cost $2,750
   Total cost = $7,750, Total qty = 150
   cost_basis = $7,750 / 150 = $51.67 per share
   
   CALCULATE MARKET VALUE:
   - market_value = current_quantity × current_price
   
   CALCULATE UNREALIZED P&L:
   - unrealized_pnl = market_value - (quantity × cost_basis)

3. INSERT to analytics.fact_daily_holdings:
   - snapshot_date = today
   - (account_id, instrument_id, snapshot_date) = unique key
   - On conflict: update (Type 1 SCD)

4. VALIDATE:
   - All positions have cost_basis
   - No negative quantities
   - P&L calculations correct
```

---

### 4. ACCOUNT SUMMARY ETL (`transform_account_summary_etl.py`)
**Runs**: Hourly @ :45 (after Holdings)  
**Purpose**: Aggregate daily account-level metrics  
**Output**: analytics.fact_daily_account_summary

**Complexity**: LOW

**Logic**:
```
1. LOAD trades from staging.trades_raw

2. FOR each (account_id, summary_date):
   
   COUNT TRADES:
   - num_buy_trades = COUNT(trades WHERE side='BUY')
   - num_sell_trades = COUNT(trades WHERE side='SELL')
   
   SUM VOLUMES:
   - total_shares_traded = SUM(quantity)
   - total_volume_traded = SUM(quantity × price)
   
   CALCULATE P&L:
   - daily_pnl = SUM(realized_pnl + unrealized_pnl) for today
   - cumulative_pnl = running total from start_date
   
   CALCULATE BALANCES:
   - beginning_balance = balance at start of day
   - ending_balance = balance at end of day
   - (pulled from staging.accounts_raw)

3. INSERT to analytics.fact_daily_account_summary:
   - summary_date = today
   - (account_id, summary_date) = unique key
   - On conflict: update

4. VALIDATE:
   - No negative totals
   - Balance makes sense
```

---

### 5. VALIDATE ETL (`validate_etl.py`)
**Runs**: Hourly @ :55 (after all loads)  
**Purpose**: Post-load quality checks & reconciliation  
**Output**: Validation report (log + optionally analytics.validation_report table)

**Complexity**: MEDIUM

**Logic**:
```
1. ROW COUNT RECONCILIATION (OLTP ↔ OLAP):
   FOR each table (trades, holdings, accounts):
     - Count OLTP rows
     - Count OLAP rows
     - IF abs(oltp - olap) / oltp > 5%:
         → FAILED ❌
     - ELSE:
         → PASSED ✓

2. P&L VALIDATION:
   - Check no NULLs in realized_pnl / unrealized_pnl columns
   - Check P&L totals are reasonable (not $1B daily)
   - Check realized_pnl <= 0 for sells only
   
3. SCHEMA VALIDATION:
   - Check no NULLs in key columns (trade_id, account_id, etc.)
   - Check date ranges valid
   - Check foreign keys resolve

4. ANOMALY DETECTION:
   - Price outliers: > 2 standard deviations
   - Volume spikes: > 5× daily average
   - Unusual account activity
   
5. DATA COMPLETENESS:
   - % of data populated in each column
   - Flag < 95% completeness

6. REPORT GENERATION:
   - Print validation_results (PASSED / FAILED / WARNING)
   - Log to audit_trail
   - Optionally send email/Slack on failure

7. RETURN:
   {
     "passed": true/false,
     "failed_checks": [...],
     "warnings": [...],
     "summary": {...}
   }
```

---

## FILE STRUCTURE

```
analytics_etl/
├── config.py                          (Database connections, constants)
├── base_logger.py                     (Logging setup)
├── schemas/
│   ├── staging_schema.sql             (Create staging tables/DLQ)
│   ├── analytics_schema.sql           (Create analytics tables)
│   └── audit_trail_schema.sql         (Create audit table)
├── etl/
│   ├── extract_etl.py                 (Extract OLTP → Staging)
│   ├── transform_pnl_etl.py           (Transform: P&L calculation)
│   ├── transform_holdings_etl.py      (Transform: Holdings snapshots)
│   ├── transform_account_summary_etl.py (Transform: Daily aggregates)
│   └── validate_etl.py                (Validate: Quality checks)
├── dags/
│   └── hourly_etl_dag.py              (Airflow orchestration)
├── tests/
│   ├── test_extract.py
│   ├── test_pnl_calculation.py
│   ├── test_holdings.py
│   ├── test_account_summary.py
│   └── test_validate.py
├── requirements.txt
├── README.md
└── .env.example
```

---

## EXECUTION TIMELINE (Hourly)

```
:00 → EXTRACT runs
      Pulls OLTP → staging tables
      Duration: 2-5 min
      
:15 → P&L ETL runs (after extract finishes)
      Calculates FIFO P&L
      Loads fact_trades
      Duration: 3-10 min
      
:30 → HOLDINGS ETL runs (after P&L finishes)
      Calculates daily holdings
      Loads fact_daily_holdings
      Duration: 2-5 min
      
:45 → ACCOUNT SUMMARY ETL runs (after holdings finishes)
      Aggregates daily metrics
      Loads fact_daily_account_summary
      Duration: 1-3 min
      
:55 → VALIDATE ETL runs (after all loads finalize)
      Reconciliation checks
      Anomaly detection
      Generates report
      Duration: 2-5 min

Full pipeline window: :00 to 1:00 (one hour buffer before next cycle)
```

---

## KEY DESIGN DECISIONS

### 1. SHARED EXTRACT LAYER
✅ Extract runs once per hour → all downstream ETLs reuse same staging data  
✅ Reduces OLTP load (1 extraction vs 3)  
✅ Consistent data across all ETLs  

### 2. MODULAR TRANSFORM ETLS
✅ Each feature = separate Python module + Airflow task  
✅ P&L breaks? Holdings still works.  
✅ Easy to add new features (new ETL, don't touch existing)  

### 3. FULL REFRESH LOADS
✅ Simple: delete all facts, insert all new facts  
✅ Slow: reloads everything hourly  
✅ For < 100K rows/day, perfectly fine  
✅ Future: can migrate to incremental if needed  

### 4. TYPE 1 DIMENSIONS (Current Only)
✅ No historical tracking (we only keep latest state)  
✅ Simpler: delete + insert  
✅ Future: can add Type 2 SCD for history if needed  

### 5. TRANSACTION-WRAPPED LOADS
✅ All-or-nothing semantics: all dimensions load + all facts load  
✅ Prevents partial loads (inconsistent state)  
✅ Rollback on error: clean slate for next run  

### 6. DEAD-LETTER QUEUE (DLQ)
✅ Bad records not dropped (auditable)  
✅ Can investigate later  
✅ Helps debug data quality issues  

---

## ERROR HANDLING STRATEGY

```
EXTRACT fails
  → Airflow retries 3× with exponential backoff
  → If still fails: STOP (don't run downstream ETLs)
  → Alert: Email/Slack to data team

P&L ETL fails
  → Airflow retries 3× with exponential backoff
  → If still fails: SKIP Holdings/Account Summary
  → Alert: Only P&L failed, holdings/summary will be stale

All ETLs fail
  → Validate ETL detects missing data
  → Validation report shows red flags
  → Alert to team

Validation fails
  → Log to audit_trail with full details
  → Alert: Data quality issue detected
  → Team reviews, manually fixes, re-runs ETL
```

---

## NEXT STEPS FOR THE TEAM

1. **Setup** (1 hour)
   - Create staging + analytics schemas in database
   - Create config.py with DB connections
   - Verify connectivity

2. **Implement Extract ETL** (2-3 hours)
   - Implement extract_etl.py
   - Test: Run manually, verify data in staging

3. **Implement P&L ETL** (4-6 hours) ⚠️ HARDEST PART
   - Implement transform_pnl_etl.py
   - Implement FIFO matching algorithm
   - Test with sample data: 10 trades, verify P&L calculations

4. **Implement Holdings ETL** (2-3 hours)
   - Implement transform_holdings_etl.py
   - Test with same sample data

5. **Implement Account Summary ETL** (1-2 hours)
   - Implement transform_account_summary_etl.py
   - Test with same sample data

6. **Implement Validate ETL** (2-3 hours)
   - Implement validate_etl.py
   - Add reconciliation checks

7. **Wire Airflow DAG** (2-3 hours)
   - Implement hourly_etl_dag.py
   - Add task dependencies
   - Add error handling / retries

8. **End-to-end Testing** (3-4 hours)
   - Run full pipeline locally
   - Verify data integrity
   - Deploy to Airflow

**Total: 17-27 hours** (or ~2-3 days of focused work)

---

## REFERENCES

- **Jeepers-Leapers Repo**: Reference implementation (jeepers-leapers/analytics/etl/)
  - extract.py → copy pattern
  - transform.py → copy P&L logic
  - load.py → copy loading pattern
  - validate.py → copy validation logic

- **FIFO Algorithm**: Trading standard for P&L matching
- **Star Schema**: Kimball methodology for data warehousing
- **Type 1 SCD**: Slowly Changing Dimensions (current state only)
