# PROMPT FOR NEW AGENT - Trading Analytics ETL

## GOAL
Build a **production-ready, modular ETL pipeline** that transforms raw trading platform data (OLTP) into analytics-ready star schema (OLAP). Each feature gets its own ETL, sharing a common Extract layer. Orchestrate with Apache Airflow for hourly execution.

---

## CONTEXT
- **Domain**: Stock trading platform (accounts, instruments, trades, holdings)
- **Source DB**: OLTP database with 6 core tables (users, accounts, instruments, exchanges, holdings, trades)
- **Target**: Separate analytics schema with star schema (4 dimensions + 3 facts)
- **Data Volume**: < 100K rows/day
- **Update Frequency**: Hourly (24 runs/day)
- **Load Strategy**: Full refresh (delete facts, reload hourly)
- **Stack**: Python 3.9+, PostgreSQL, Apache Airflow, Pandas, SQLAlchemy

---

## WHAT YOU'RE BUILDING

A **5-module ETL pipeline** that runs every hour:

```
:00 → EXTRACT        (OLTP → Staging)
:15 → P&L ETL       (Calculate FIFO P&L, load fact_trades)
:30 → HOLDINGS ETL  (Calculate daily holdings, load fact_daily_holdings)
:45 → SUMMARY ETL   (Aggregate daily metrics, load fact_daily_account_summary)
:55 → VALIDATE ETL  (Quality checks, reconciliation)
```

Each module is a standalone Python file that can be tested independently.

---

## ARCHITECTURE OVERVIEW

### Data Flow
```
OLTP Database                Staging Schema           Analytics Schema
(production)                 (temporary)              (star schema)
├─ users                     ├─ exchanges_raw         ├─ dim_exchanges
├─ accounts          →       ├─ accounts_raw      →   ├─ dim_accounts
├─ instruments               ├─ instruments_raw       ├─ dim_instruments
├─ exchanges                 ├─ holdings_raw          ├─ dim_dates
├─ holdings                  ├─ trades_raw            ├─ fact_trades
└─ trades                    └─ dead_letter_queue     ├─ fact_daily_holdings
                                                      └─ fact_daily_account_summary
```

### Module Breakdown

| Module | Runs | Purpose | Complexity | Output |
|--------|------|---------|-----------|--------|
| **Extract ETL** | :00 | Pull OLTP → Staging | Low | 5 raw tables |
| **P&L ETL** | :15 | FIFO matching + P&L calc | **HIGH** ⚠️ | fact_trades |
| **Holdings ETL** | :30 | Daily position snapshots | Medium | fact_daily_holdings |
| **Summary ETL** | :45 | Daily account aggregates | Low | fact_daily_account_summary |
| **Validate ETL** | :55 | QA checks + reconciliation | Medium | Validation report |

---

## PHASE-BY-PHASE INSTRUCTIONS

### PHASE 1: Setup Infrastructure (1-2 hours)

**Create SQL schemas** (03_STAGING_SCHEMA.sql + 04_ANALYTICS_SCHEMA.sql):
- Staging tables: exchanges_raw, accounts_raw, instruments_raw, holdings_raw, trades_raw
- Dead-Letter Queue: dead_letter_queue (for rejected rows)
- Analytics tables: dim_*, fact_*
- Audit trail: audit_trail

**Create config.py** (05_CONFIG.py template):
- OLTP database connection
- OLAP database connection
- Staging connection
- ETL constants (batch size, thresholds, retry logic)
- Helper functions: get_oltp_engine(), get_olap_engine(), get_staging_engine()

**Create logger** (optional but recommended):
- Centralized logging to file + console
- Log level: INFO/DEBUG

---

### PHASE 2: Implement Extract ETL (2-3 hours)

**File**: `etl/extract_etl.py`

**Class**: `DataExtractor`
- **Method**: `extract_all_data(etl_run_id)` → returns dict of extraction stats
- **Methods**: `_extract_exchanges()`, `_extract_accounts()`, `_extract_instruments()`, `_extract_holdings()`, `_extract_trades()`

**Logic for each _extract_*() method**:
1. Read from OLTP table (using SQL query)
2. Add `etl_run_id` column for tracking
3. Truncate staging table from previous run
4. Insert raw data to staging table
5. Return (rows_extracted, rows_loaded)

**Testing**:
```bash
python -c "from etl.extract_etl import DataExtractor; ext = DataExtractor('test_run_123'); stats = ext.extract_all_data(); print(stats)"
# Should show: {'exchanges': (N, N), 'accounts': (N, N), ...}
# Query staging.trades_raw should have real data
```

---

### PHASE 3: Implement P&L ETL (4-6 hours) ⚠️ MOST COMPLEX

**File**: `etl/transform_pnl_etl.py`

**Class**: `PnLTransformer`
- **Method**: `transform_and_load(etl_run_id)` → returns dict of load stats

**Step 1: Load & Validate Trades**
```python
def _load_and_validate_trades(self):
    # Load trades from staging.trades_raw
    # Validate each trade:
    #   - Price: 0.01 to 1,000,000
    #   - Quantity: 0.0001 to 1,000,000
    #   - Date: not > 1 day in future
    # Route bad records to dead_letter_queue
    # Return: validated_df
```

**Step 2: Calculate Realized P&L (FIFO Matching)**
```python
def _calculate_realized_pnl(self, df):
    # FOR each (account_id, instrument_id):
    #   - Get BUY trades (sorted by executed_at ASC)
    #   - Get SELL trades (sorted by executed_at ASC)
    #   - Match oldest BUY with first SELL (FIFO)
    #   - For each matched pair: realized_pnl = (sell_price - buy_price) × qty
    # Return: df with realized_pnl column populated
    
    # PSEUDO-CODE:
    for (account_id, instrument_id), group in df.groupby(['account_id', 'instrument_id']):
        buys = group[group['side'] == 'BUY'].sort_values('executed_at')
        sells = group[group['side'] == 'SELL'].sort_values('executed_at')
        
        # FIFO matching logic here
        # Update df.loc[sell_idx, 'realized_pnl'] for each sell
```

**Step 3: Calculate Unrealized P&L**
```python
def _calculate_unrealized_pnl(self, df):
    # FOR each open position (BUY not yet fully SOLD):
    #   - unrealized_pnl = (current_price - buy_price) × quantity
    # Return: df with unrealized_pnl column populated
```

**Step 4: Load to Analytics**
```python
def _load_fact_trades(self, df):
    # DELETE all from analytics.fact_trades
    # INSERT batch by batch (10K rows per INSERT)
    # Wrap in transaction (SQLAlchemy engine.begin())
    # Return: rows_loaded
```

**Testing**:
Create 3 sample trades:
```sql
BUY 100 @ $50 (2026-01-01)
SELL 80 @ $60 (2026-01-03)
SELL 20 @ $70 (2026-01-05)
```
Expected realized P&L:
- SELL 80 @ $60: (60-50) × 80 = $800
- SELL 20 @ $70: (70-50) × 20 = $400
- Total: $1200

Verify in analytics.fact_trades:
```sql
SELECT trade_id, side, quantity, execution_price, realized_pnl FROM analytics.fact_trades ORDER BY executed_at;
```

---

### PHASE 4: Implement Holdings ETL (2-3 hours)

**File**: `etl/transform_holdings_etl.py`

**Class**: `HoldingsTransformer`
- **Method**: `transform_and_load(etl_run_id)` → returns dict of load stats

**Step 1: Load Holdings & Trades**
```python
def _load_data(self):
    # Load holdings from staging.holdings_raw
    # Load trades from staging.trades_raw
    # Return: (holdings_df, trades_df)
```

**Step 2: Calculate Cost Basis (FIFO)**
```python
def _calculate_cost_basis(self, account_id, instrument_id, quantity, trades_df):
    # Filter trades for this account/instrument
    # Sort BUY trades by date
    # Sum cost = SUM(buy_price × buy_qty)
    # Sum qty = SUM(buy_qty)
    # cost_basis = sum_cost / sum_qty
    # Return: cost_basis (float)
```

**Step 3: Calculate Market Value & Unrealized P&L**
```python
def _calculate_market_value(self, quantity, current_price):
    # market_value = quantity × current_price
    # unrealized_pnl = market_value - (quantity × cost_basis)
    # Return: (market_value, unrealized_pnl)
```

**Step 4: Load to Analytics**
```python
def _load_fact_daily_holdings(self, df):
    # INSERT or UPDATE to analytics.fact_daily_holdings
    # (account_id, instrument_id, snapshot_date) = unique key
    # snapshot_date = today
    # Return: rows_loaded
```

**Testing**:
Use same sample data as P&L test, verify holdings snapshots.

---

### PHASE 5: Implement Account Summary ETL (1-2 hours)

**File**: `etl/transform_account_summary_etl.py`

**Class**: `AccountSummaryTransformer`
- **Method**: `transform_and_load(etl_run_id)` → returns dict of load stats

**Step 1: Aggregate by Account/Date**
```python
def _aggregate_trades(self, trades_df):
    # GROUP BY account_id, DATE(executed_at)
    # COUNT(BUY trades), COUNT(SELL trades)
    # SUM(quantity), SUM(quantity × price)
    # SUM(realized_pnl), SUM(unrealized_pnl)
    # Return: aggregated_df
```

**Step 2: Calculate Balances**
```python
def _calculate_balances(self, account_id, summary_date):
    # beginning_balance = balance at start of day (from staging)
    # ending_balance = beginning_balance + daily_pnl - withdrawn
    # cumulative_pnl = running total from start date
    # Return: (beginning, ending, cumulative)
```

**Step 3: Load to Analytics**
```python
def _load_fact_daily_account_summary(self, df):
    # INSERT or UPDATE to analytics.fact_daily_account_summary
    # (account_id, summary_date) = unique key
    # Return: rows_loaded
```

**Testing**:
Verify daily totals match sum of individual trades.

---

### PHASE 6: Implement Validate ETL (2-3 hours)

**File**: `etl/validate_etl.py`

**Class**: `DataValidator`
- **Method**: `run_all_validations(etl_run_id)` → returns validation report dict

**Validation Checks**:

1. **Row Count Reconciliation**
```python
def _reconcile_row_counts(self):
    # FOR each table (trades, holdings, accounts):
    #   - Count OLTP rows
    #   - Count OLAP rows
    #   - IF abs(oltp - olap) / oltp > 5%: FAILED
    #   - ELSE: PASSED
```

2. **P&L Validation**
```python
def _validate_pnl(self):
    # Check no NULLs in realized_pnl, unrealized_pnl
    # Check P&L totals reasonable (not $1B)
    # Check realized_pnl only on SELL trades
```

3. **Schema Validation**
```python
def _validate_schema(self):
    # Check no NULLs in key columns (trade_id, account_id, etc.)
    # Check datetime ranges valid
    # Check foreign keys resolve
```

4. **Anomaly Detection**
```python
def _detect_anomalies(self):
    # Price outliers: > 2 standard deviations
    # Volume spikes: > 5× daily average
    # Unusual account activity
```

5. **Data Completeness**
```python
def _check_completeness(self):
    # % of non-NULL values in each column
    # Flag if < 95%
```

**Testing**:
Run after loading, should show PASSED checks.

---

### PHASE 7: Create Airflow DAG (2-3 hours)

**File**: `dags/hourly_etl_dag.py`

**DAG Configuration**:
```python
dag = DAG(
    'trading_analytics_hourly_etl',
    default_args={
        'owner': 'data_engineering',
        'retries': 3,
        'retry_delay': timedelta(minutes=5),
        'retry_exponential_base': 2,  # Exponential backoff
    },
    schedule_interval='0 * * * *',  # Every hour at :00
    max_active_runs=1,  # No concurrent runs
    catchup=False,
)
```

**Task Groups**:
```
setup
  ├─ create_run_id
  └─ validate_connections

extract
  └─ extract_all_data

transform
  ├─ pnl_etl
  ├─ holdings_etl (depends on pnl)
  └─ summary_etl (depends on holdings)

validate
  └─ run_validation

cleanup
  └─ log_completion
```

**Task Dependencies**:
```
setup → extract → pnl_etl → holdings_etl → summary_etl → validate → cleanup
```

**Testing**:
```bash
airflow dags test trading_analytics_hourly_etl 2026-01-01
```

---

## TESTING STRATEGY

### Unit Tests
- Test each module independently
- Use mock data (10-100 sample rows)
- Verify calculations are correct
- Test error cases (bad data, DB connection fails)

### Integration Tests
- Run full pipeline start-to-finish
- Verify data flows: OLTP → Staging → Analytics
- Check row counts match ± 5%
- Spot-check calculations

### End-to-End Tests
- Run on Airflow scheduler
- Monitor logs
- Verify hourly execution
- Check analytics tables updated each hour

---

## DELIVERABLES

✅ **Code Files**:
- etl/extract_etl.py
- etl/transform_pnl_etl.py
- etl/transform_holdings_etl.py
- etl/transform_account_summary_etl.py
- etl/validate_etl.py
- dags/hourly_etl_dag.py
- config.py
- requirements.txt

✅ **SQL Schemas**:
- schemas/staging_schema.sql
- schemas/analytics_schema.sql

✅ **Tests**:
- tests/test_extract.py
- tests/test_pnl.py
- tests/test_holdings.py
- tests/test_summary.py
- tests/test_validate.py

✅ **Documentation**:
- README.md (setup + running instructions)
- 00_ARCHITECTURE.md (this file)
- Inline code comments

---

## SUCCESS CRITERIA

✅ Extract ETL pulls data from OLTP → Staging  
✅ P&L ETL calculates FIFO correctly (verified with manual calculations)  
✅ Holdings ETL calculates cost basis correctly  
✅ Summary ETL aggregates correctly  
✅ Validate ETL passes all checks  
✅ Full pipeline runs hourly on Airflow without errors  
✅ Analytics tables populated with correct data  
✅ Dashboard/reports can query analytics schema successfully  

---

## REFERENCES

**Reference Implementation**: jeepers-leapers/analytics/etl/
- **extract.py**: Pattern for extracting data
- **transform.py**: P&L calculation logic (copy this carefully!)
- **load.py**: Transaction handling + batching
- **validate.py**: Validation checks

**Key Algorithms**:
- FIFO: First-In-First-Out matching for trades
- Star Schema: Kimball methodology
- Type 1 SCD: Slowly Changing Dimensions (current only)

---

## TIMELINE

- **Days 1**: Setup + Extract ETL
- **Day 2-3**: P&L ETL (hardest part, needs careful testing)
- **Day 4**: Holdings + Summary ETLs
- **Day 5**: Validate ETL
- **Day 6**: Airflow DAG + end-to-end testing
- **Day 7**: Deploy + monitoring

**Total: ~1 week of focused development**

---

## SUPPORT

- Ask questions in code comments
- Reference jeepers-leapers implementation
- Test with small sample data first (10 trades, not 100K)
- Use logging extensively for debugging
