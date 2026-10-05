# ETL Evolution Plan: From Simple to Production-Grade

## Current State vs. Desired State

### ✅ What You Have Now (simple_etl.py)
- **Scope**: Metadata-only transformation
- **Functionality**: 
  - Extract: exchanges, accounts, instruments, trades (last 90 days)
  - Transform: Add etl_run_id, etl_timestamp, source_table (3 columns only)
  - Load: Insert to staging tables
- **Testing**: Basic unit tests with fixtures
- **Complexity**: Low (~350 lines)
- **Output**: Raw staging tables with no business logic

### ❌ What's Missing (from Handoff Package)

The handoff package provides a **complete analytics platform** with:

1. **Five Specialized ETL Modules** (not one monolithic script):
   - Extract ETL (extracts raw data) → :00
   - PnL ETL (calculates profit/loss via FIFO) → :15
   - Holdings ETL (daily portfolio snapshots) → :30
   - Summary ETL (daily account aggregates) → :45
   - Validate ETL (reconciliation + QA) → :55

2. **Advanced Data Transformations**:
   - FIFO matching algorithm for realized P&L
   - Cost basis calculations
   - Market value + unrealized P&L
   - Daily aggregations + running totals
   - Anomaly detection

3. **Production Infrastructure**:
   - Dead-letter queue (DLQ) for error handling
   - Audit trail for compliance
   - Full star schema (dimensions + facts)
   - Monitoring views + DLQ summary views
   - Airflow DAG for hourly orchestration

4. **Comprehensive Testing**:
   - Unit tests for each module
   - Integration tests for full pipeline
   - Data quality validations

---

## Phase-by-Phase Implementation Plan

### PHASE 0: Foundation (You Are Here)
**Current**: You have basic extract + tests  
**Status**: ✅ Working (2 exchanges, 5 accounts, 7 instruments loaded)

### PHASE 1: Refactor Extract (4-6 hours)
**Goal**: Convert simple_etl.py → professional extract_etl.py

**Changes**:
1. Rename `simple_etl.py` → `extract_etl.py` 
2. Refactor into `DataExtractor` class (matches template)
3. Add staging table management (TRUNCATE instead of DROP)
4. Add extraction statistics tracking
5. Add holdings extraction (currently missing)
6. Create `config.py` for database engines (reusable across modules)

**Why**: The template `07_EXTRACT_ETL_TEMPLATE.py` is production-ready. Your script needs to match this pattern for other modules to reuse it.

**Deliverables**:
- ✅ `analytics/etl/extract_etl.py` (refactored)
- ✅ `analytics/etl/config.py` (new)
- ✅ Tests still pass
- ✅ Data quality: staging tables verified

---

### PHASE 2: Implement Analytics Schema (2-3 hours)
**Goal**: Create star schema for BI

**Database Changes**:
1. Create analytics schema (separate from staging)
2. Create dimensions:
   - `dim_exchanges` (10 rows)
   - `dim_accounts` (5 rows)
   - `dim_instruments` (7 rows)
   - `dim_dates` (730 rows for 2 years)
3. Create facts:
   - `fact_trades` (with realized_pnl, unrealized_pnl)
   - `fact_daily_holdings` (with cost_basis, market_value)
   - `fact_daily_account_summary` (with daily/cumulative P&L)
4. Create audit_trail + dead-letter queue

**SQL Files**:
- ✅ Use `03_STAGING_SCHEMA.sql` (run once to create DLQ + views)
- ✅ Use `04_ANALYTICS_SCHEMA.sql` (run once for star schema)
- ✅ Use `05_AUDIT_TRAIL_SCHEMA.sql` (run once for audit logging)

**Deliverables**:
- ✅ Three new schemas: staging, analytics, (audit tables)
- ✅ 8 tables total (3 facts + 4 dimensions + date + DLQ)
- ✅ Indexes created for performance

---

### PHASE 3: PnL Transformation - THE HARD PART (6-10 hours)
**Goal**: Calculate P&L using FIFO matching algorithm

**Complexity**: ⚠️ This is the most complex part. It requires:
1. FIFO matching logic (complicated)
2. Cost basis calculations
3. Realized vs. unrealized P&L distinction
4. Edge cases (partial fills, short sales, etc.)

**Implementation**:
1. Create `analytics/etl/transform_pnl_etl.py`
2. Copy logic from template `08_PNL_ETL_TEMPLATE.py`
3. Implement `_calculate_realized_pnl()` - FIFO matching
4. Implement `_calculate_unrealized_pnl()` - current prices
5. Load to `analytics.fact_trades`

**Why This is Hard**:
```python
# Example: FIFO Matching for P&L
# Account buys 100 shares @ $10 (cost = $1000)
# Account buys 100 shares @ $12 (cost = $1200)
# Account sells 150 shares @ $15
#
# FIFO says: First 100 @ $10, then 50 @ $12
# Realized P&L = (150 × $15) - (100 × $10 + 50 × $12)
#              = $2250 - $1600 = $650 profit
#
# But what about the remaining 50 shares @ $12?
# That's unrealized P&L = calculated daily
```

**Deliverables**:
- ✅ `transform_pnl_etl.py` (250+ lines)
- ✅ FIFO matching algorithm working
- ✅ P&L correctly calculated
- ✅ Unit tests (test_pnl.py) with 10+ test cases
- ✅ Fact_trades table populated with P&L columns

---

### PHASE 4: Holdings Transformation (3-4 hours)
**Goal**: Daily portfolio snapshots with cost basis

**Implementation**:
1. Create `analytics/etl/transform_holdings_etl.py`
2. For each (account, instrument, date):
   - Calculate total quantity held
   - Calculate cost_basis (average cost of all held shares)
   - Calculate market_value = quantity × current_price
   - Calculate unrealized_pnl = market_value - (quantity × cost_basis)
3. Load to `analytics.fact_daily_holdings`

**Formula**:
```
cost_basis = SUM(BUY trades value) / SUM(BUY trades qty)
market_value = quantity × current_price
unrealized_pnl = market_value - (quantity × cost_basis)
```

**Deliverables**:
- ✅ `transform_holdings_etl.py`
- ✅ Tests for holdings calculations
- ✅ Fact_daily_holdings populated

---

### PHASE 5: Summary Transformation (2-3 hours)
**Goal**: Daily account-level aggregates

**Implementation**:
1. Create `analytics/etl/transform_summary_etl.py`
2. For each (account_id, summary_date):
   - Count: num_buy_trades, num_sell_trades
   - Sum: total_volume, total_volume_traded
   - Calculate: daily_pnl, cumulative_pnl
   - Get: beginning_balance, ending_balance
3. Load to `analytics.fact_daily_account_summary`

**Deliverables**:
- ✅ `transform_summary_etl.py`
- ✅ Tests for summary calculations
- ✅ Fact_daily_account_summary populated

---

### PHASE 6: Validation & Error Handling (3-4 hours)
**Goal**: Data quality checks + error handling

**Implementation**:
1. Create `analytics/etl/validate_etl.py`
2. Implement checks:
   - Row count reconciliation (staging vs. facts)
   - P&L validation (realized + unrealized = total)
   - Price anomalies (outlier detection)
   - Null value checks
   - Foreign key integrity
3. Log rejections to dead_letter_queue
4. Generate audit trail

**Deliverables**:
- ✅ `validate_etl.py`
- ✅ Dead-letter queue populated for bad data
- ✅ Audit trail logged for all operations

---

### PHASE 7: Airflow Orchestration (2-3 hours)
**Goal**: Hourly orchestration of all 5 modules

**Implementation**:
1. Create Airflow DAG: `dags/trading_analytics_hourly_etl.py`
2. Define tasks:
   - extract_task @ :00
   - pnl_task @ :15 (depends on extract)
   - holdings_task @ :30 (depends on pnl)
   - summary_task @ :45 (depends on holdings)
   - validate_task @ :55 (depends on summary)
3. Set retries + alerting

**Deliverables**:
- ✅ Airflow DAG file
- ✅ All 5 tasks chained correctly
- ✅ DAG runs hourly
- ✅ Logs captured for debugging

---

### PHASE 8: Production Setup (2-3 hours)
**Goal**: Make it production-ready

**Items**:
1. Environment variables + .env management
2. Logging configuration (rotate logs, etc.)
3. Database backup strategy
4. Monitoring + alerting
5. Runbooks for common issues
6. Documentation

**Deliverables**:
- ✅ Production .env setup
- ✅ Logging configured
- ✅ README.md with runbooks

---

## Effort Estimate

| Phase | Task | Hours | Complexity | Priority |
|-------|------|-------|-----------|----------|
| 0 | Current State (Done) | - | ✅ | - |
| 1 | Refactor Extract | 4-6 | Medium | 🔴 FIRST |
| 2 | Analytics Schema | 2-3 | Low | 🔴 FIRST |
| 3 | PnL Transformation | 6-10 | 🔴 **HARD** | 🟠 CRITICAL |
| 4 | Holdings Transform | 3-4 | Medium | 🟢 Normal |
| 5 | Summary Transform | 2-3 | Low | 🟢 Normal |
| 6 | Validation | 3-4 | Medium | 🟢 Normal |
| 7 | Airflow DAG | 2-3 | Medium | 🟢 Normal |
| 8 | Production | 2-3 | Low | 🟢 Normal |
| **TOTAL** | | **25-36 hours** | | |

---

## Recommended Approach

### Option A: Do It All (Best)
Follow phases 1-8 sequentially. You'll have a production-grade analytics platform.
**Timeline**: 4-5 days of focused work

### Option B: MVP Path (Faster)
Do phases 1-3 (Extract + Schema + P&L) first. Get a working star schema with P&L.
**Timeline**: 1-2 days
**Missing**: Holdings, Summary, Validation, Airflow

### Option C: Incremental (Recommended for Learning)
1. Do Phase 1 (refactor extract)
2. Do Phase 2 (create schema)
3. Do Phase 3 (P&L - the hardest, most important)
4. Do Phase 6 (validation - critical for data quality)
5. Do Phase 4-5 (holdings + summary - easier after P&L works)
6. Do Phase 7-8 (Airflow + production)

---

## Key Decisions to Make

1. **Trades Table History**:
   - Keep only last 90 days in fact_trades? Or all history?
   - Recommended: Keep all history (allow historical analysis)

2. **P&L Calculation Strategy**:
   - FIFO (first in, first out) - simple
   - LIFO (last in, first out) - more realistic
   - Average cost - tax-advantaged
   - **Recommendation**: Start with FIFO (template provided), add others later

3. **Scheduled Frequency**:
   - Hourly (current plan from handoff)
   - Daily (simpler, less load)
   - Real-time (complex)
   - **Recommendation**: Start with hourly, meets business needs

4. **Data Retention**:
   - Staging tables: Truncate each run (current approach)
   - Archive old runs? Recommendation: Keep last 7 days for debugging

---

## Next Immediate Action

**Start with Phase 1: Refactor Extract**

This is the foundation for everything else. Once you have a clean `extract_etl.py` that matches the template pattern:
- Other modules can import from it
- Config can be shared
- Testing framework is established

Shall I help you start with Phase 1?

