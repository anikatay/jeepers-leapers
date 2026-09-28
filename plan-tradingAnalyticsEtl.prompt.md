# Plan: Trading Platform ETL Pipeline & Analytics Warehouse

## TL;DR
Build a modular star-schema analytics database with an Airflow-orchestrated Python ETL pipeline that nightly performs full refreshes of trades, holdings, and account summaries. Start with performance analytics (P&L, returns) + compliance audit trail, designed for extensibility to add risk, user, and market analytics later. MVP in 1-2 weeks: core schema + basic daily load + Java backend query endpoints + Angular dashboard integration.

---

## Architecture Overview

### Schema Design: Star Schema (OLAP)
**Fact Tables** (events, transactions, daily snapshots):
- `fact_trades` - individual trade transactions with P&L, execution metrics
- `fact_daily_holdings` - daily portfolio snapshots (account + instrument positions)
- `fact_daily_account_summary` - daily account-level aggregates (balance, PnL, trade count)

**Dimension Tables** (slowly-changing reference data):
- `dim_accounts` - trading accounts (account_id, user_id, currency, status, created_at)
- `dim_instruments` - tradable securities (instrument_id, ticker, exchange_id, current_price, sector/type - extensible)
- `dim_exchanges` - trading venues (exchange_id, name, region, timezone, currency)
- `dim_dates` - date dimension for time-series queries (date, year, month, quarter, week, day_of_week, is_trading_day)

**Audit Table** (compliance):
- `audit_trail` - immutable log of all data operations (timestamp, table, operation, record_id, user, row_hash for change detection)

**Rationale**: Star schema is optimal for BI dashboards, separates slow-changing dimensions from fact-driven queries, denormalized for query speed, supports performance analytics (P&L by account/instrument/date) and compliance audits.

---

## Business Intelligence Requirements

### Performance Analytics (MVP)
1. **Trade-Level Metrics**
   - P&L per trade: realized gain/loss (execution_price vs current_price)
   - Trade efficiency: slippage, time-to-fill, volume-weighted avg price (VWAP)
   - Execution metrics: buy/sell ratios, order size distribution

2. **Daily Portfolio Metrics**
   - Daily P&L (realized + unrealized)
   - Cumulative returns (daily, month-to-date, year-to-date)
   - Position concentration: % allocation per instrument
   - Daily turnover and churn

3. **Account-Level Dashboards**
   - Account balance trend, equity curve
   - Holdings by instrument, exchange
   - Top performing instruments
   - Trade frequency and volume

4. **Compliance & Audit**
   - Complete trade audit trail (account → trade → instrument → exchange)
   - Account activity log (creation, balance changes, status)
   - Data integrity checks (row counts, hash verification)
   - Regulatory-ready reports (can add SOX/FINRA templates later)

### Future Analytics (Modular, add later):
- **Risk**: Exposure by exchange/instrument, concentration risk, portfolio volatility
- **User Behavior**: Trading frequency trends, customer lifetime value, retention
- **Market**: Instrument price trends, exchange volumes, correlations
- **Executive KPIs**: AUM, customer count, trade volumes, platform growth

---

## ETL Pipeline Architecture

### Orchestration: Apache Airflow
**DAG Structure**:
```
daily_etl_pipeline (scheduled: nightly at 1 AM)
├── extract_task (read from OLTP, store CSV staging)
├── transform_task (pandas transformations, validate, load to staging tables)
├── reconcile_task (row count checks, P&L validation, anomalies)
├── load_fact_tables (upsert/replace into fact tables)
├── load_dimension_tables (slowly-changing dimension logic)
├── audit_task (log all changes to audit_trail)
└── alert_task (on failure, send notification)

hourly_realtime_task (scheduled: every hour, 6 AM-10 PM)
└── refresh_daily_summary_facts (fast recalculation of day's totals, no recompute of history)
```

**Why full refresh nightly vs incremental?**
- Simpler to implement initially (no change tracking complexity)
- Trading datasets are small/medium (easily fits in memory)
- Daily refresh aligns with market day boundaries
- Can add incremental later as performance needs grow

### Data Flow
1. **Extract**: Read from OLTP PostgreSQL (same DB server, separate schema)
   - Use SQLAlchemy + psycopg2 to query OLTP tables
   - Extract to CSV staging files (or direct staging tables)
   
2. **Transform**: Python/Pandas transformations
   - Calculate P&L: (trade.execution_price - current_price) * quantity
   - Aggregate daily holdings: group by account_id, instrument_id, date
   - Calculate cumulative metrics: running P&L, balance trends
   - Date dimension expansion: generate date lookups
   
3. **Validate**: Quality checks
   - Row count reconciliation: OLTP trade count == OLAP fact_trades count
   - P&L validation: aggregate P&L by account matches account.balance changes
   - Schema validation: column types, non-null constraints
   - Anomaly detection: detect unusual prices, volumes, trades

4. **Load**: Upsert into analytics schema
   - Fact tables: DELETE + INSERT (full refresh)
   - Dimension tables: MERGE/UPSERT (slowly-changing dimensions: Type 2 with effective dates)
   - Audit table: APPEND transaction log

### Error Handling & Observability
- Airflow task retries (3 attempts with exponential backoff)
- Graceful failure: email alerts, Slack notifications
- Logging: structured logs with timestamps, row counts, error stacktraces
- Dbt-style tests: custom assertions on data quality

---

## Data Quality & Validation

### Intermediate SLA (As Selected)
1. **Reconciliation Checks**
   - Source count = Target count (OLTP.trades == OLAP.fact_trades)
   - P&L sum reconciliation: SUM(trade.realized_pnl) by account == account.balance - initial_balance
   - Balance continuity: no gaps, monotonic increases/decreases align with trades

2. **Schema Validation**
   - Column types match (NUMERIC(18,4) for prices, UUID for IDs)
   - Non-null constraints enforced (no nulls in fact keys)
   - Foreign key integrity (fact_trades.account_id exists in dim_accounts)

3. **Business Logic Validation**
   - Trade P&L calculation: (entry_price - exit_price) * quantity
   - Holdings quantities: positive (BUY adds, SELL subtracts)
   - Prices: positive, recent dates only

4. **Anomaly Detection**
   - Price outliers: instrument price > 2σ from 30-day average
   - Volume spikes: trade volume > 5x median daily volume
   - Account anomalies: sudden balance swings, unusual trade patterns

### Implementation
- Custom validation functions in Python (Pandas assertions)
- dbt tests for automated checks (future enhancement)
- Airflow sensor tasks to block if quality fails
- Audit trail stores validation results

---

## Implementation Roadmap

### Phase 1: Core Schema & ETL (Week 1, Days 1-3)
**Deliverable**: Functional star schema with first data load

**Steps**:
1. Create OLAP schema (separate schema `analytics` in same PostgreSQL DB)
2. Create all dimension tables: `dim_accounts`, `dim_instruments`, `dim_exchanges`, `dim_dates`
3. Create fact table: `fact_trades` with P&L columns, aggregates
4. Create audit table: `audit_trail`
5. Write Python extraction script (extract from OLTP, write to staging CSV)
6. Write Python transformation script (Pandas: P&L calc, date joins, aggregations)
7. Write Python load script (load staging → analytics tables)
8. Manual end-to-end test (extract → transform → load → reconcile)
9. Airflow DAG creation and scheduling (nightly 1 AM trigger)

**Files to Create**:
- `analytics/schema/olap_schema.sql` - all dimension + fact DDL
- `analytics/etl/extract.py` - SQLAlchemy queries
- `analytics/etl/transform.py` - Pandas transformations
- `analytics/etl/load.py` - SQLAlchemy inserts
- `analytics/etl/validate.py` - reconciliation & quality checks
- `analytics/airflow/dags/daily_etl.py` - Airflow DAG definition

### Phase 2: Daily Holdings & Account Summaries (Week 1, Days 4-5)
**Deliverable**: Multi-fact setup, hourly real-time updates

**Steps**:
1. Create fact tables: `fact_daily_holdings`, `fact_daily_account_summary`
2. Add transformations to calculate daily holdings snapshots (account+instrument+quantity+date)
3. Add transformations to aggregate daily account summaries (account+date+total_balance+daily_pnl)
4. Add hourly task to re-aggregate daily summaries (fast lightweight recalc)
5. Update validation logic to cover new fact tables
6. Test multi-fact consistency (trades + holdings + summaries align)

**Files to Create**:
- Updated `analytics/etl/transform.py` (new fact transformations)
- Updated `analytics/airflow/dags/daily_etl.py` (add hourly task)
- `analytics/airflow/dags/hourly_summary.py` (hourly recalc DAG)

### Phase 3: Analytics Query Endpoints (Week 2, Days 1-2)
**Deliverable**: Java backend REST API querying analytics DB

**Steps**:
1. Add analytics-specific services in Java backend (e.g., `AnalyticsService`, `TradeAnalyticsService`)
2. Create DTOs for analytics responses (e.g., `TradeAnalyticsDTO`, `PortfolioSummaryDTO`)
3. Create new REST endpoints in controller:
   - `GET /api/analytics/trades?accountId=...&startDate=...&endDate=...` → list fact_trades
   - `GET /api/analytics/holdings?accountId=...&date=...` → historical holdings
   - `GET /api/analytics/summary?accountId=...` → daily account summary
   - `GET /api/analytics/audit?tableId=...&recordId=...` → audit trail
4. Add simple SQL queries or use Spring Data JPA repositories pointing to analytics schema
5. Unit test endpoints

**Files to Create**:
- `src/main/java/com/neueda/leap/service/AnalyticsService.java`
- `src/main/java/com/neueda/leap/controller/AnalyticsController.java`
- `src/main/java/com/neueda/leap/dto/TradeAnalyticsDTO.java`, etc.
- `src/test/java/.../AnalyticsServiceTest.java`

### Phase 4: Frontend Analytics Dashboard (Week 2, Days 3-5)
**Deliverable**: Angular components + charts consuming analytics endpoints

**Steps**:
1. Create Angular service (e.g., `analytics.service.ts`) to call Java endpoints
2. Create dashboard components:
   - Trade history table component
   - P&L chart (line chart: cumulative P&L over time)
   - Holdings breakdown (pie chart: % by instrument)
   - Account summary card (balance, daily P&L, trade count)
   - Audit trail table (for compliance)
3. Integrate into existing Angular app routing (new `/analytics` route)
4. Add charts library (e.g., Chart.js, ng2-charts)
5. Test end-to-end: data → backend → frontend UI

**Files to Create**:
- `frontend/src/app/features/analytics/` (new feature module)
- `frontend/src/app/features/analytics/analytics.service.ts`
- `frontend/src/app/features/analytics/dashboard.component.ts`, `.html`, `.css`
- `frontend/src/app/features/analytics/trades-history.component.*`
- `frontend/src/app/features/analytics/holdings-breakdown.component.*`
- `frontend/src/app/features/analytics/audit-trail.component.*`

### Phase 5 (Future): Incremental Loads & Scale (Post-MVP)
**When to do this**: Once nightly full-refresh becomes slow (data grows to >1M trades)

**Steps**:
1. Add last_modified timestamp tracking to OLTP tables
2. Implement incremental extract: `WHERE last_modified > last_run_time`
3. Switch to UPSERT logic in load (vs DELETE + INSERT)
4. Consider Change Data Capture (Debezium) for event-driven updates

---

## Data Retention & Deletion Strategy

**Recommendation** (since "Unsure" was selected): 
- Start with **3-year rolling window** (good balance: enough history for trends, manageable storage)
- Archive older data annually to cold storage or separate archive schema
- Audit trail: retain indefinitely (compliance record)

**Implementation**:
- Add `retention_until_date` column to fact tables
- Quarterly cleanup job: `DELETE FROM fact_* WHERE retention_until_date < NOW()`

---

## Verification & Testing

### Unit Tests
1. **Transformation Tests** (Python pytest)
   - P&L calculation correctness
   - Date dimension generation
   - Aggregation logic

2. **Reconciliation Tests** (SQL queries)
   - Row count match (OLTP vs OLAP)
   - P&L sum validation
   - No orphaned foreign keys

### Integration Tests
1. Full ETL pipeline in non-prod environment
2. Data volume tests (10x current data, ensure latency < 30 min)
3. Recovery tests (restart from failure, data integrity)

### Manual Validation
1. Dashboard displays: verify P&L calculations match backend expectations
2. Audit trail: manually spot-check 5 trades from UI → OLAP → OLTP
3. Time-series: verify daily/cumulative metrics look reasonable

---

## Modular Extensibility

**Design Patterns** (Phase 1 focuses here):
- Separate `transform.py` functions per fact table (easy to add new facts)
- Configuration-driven dimension loading (easy to add fields to existing dims)
- Generic validation framework (reusable checks across tables)
- Pluggable alerting (easy to add Slack, PagerDuty later)

**Future Analytics Modules** (add without touching core ETL):
- Risk analytics: add `fact_daily_risk`, `dim_risk_categories` (new Airflow task)
- User behavior: add `fact_user_actions`, `dim_user_segments` (new transformation)
- Market data: add `fact_market_snapshots`, `dim_market_instruments` (new extraction)
- Executive KPIs: add materialized views on top of facts (no core schema change)

---

## Key Technical Decisions

| Decision | Reasoning |
|----------|-----------|
| **Full refresh nightly** | Simpler than CDC/incremental; trades are small volume; aligns with market day |
| **Star schema** | Best for BI dashboards; fast queries; clear separation of facts/dimensions |
| **Same PostgreSQL DB** | Simpler ops; same backup/monitoring; can isolate with schema permissions |
| **Python/Pandas ETL** | Team intermediate skill level; no new tools (besides Airflow); fast prototyping |
| **Exclude PII** | Privacy by design; simplifies GDPR; audit trail focuses on trades/accounts |
| **Hourly real-time summary** | Lightweight; avoids recomputing entire day's facts; good UX without full CDC |
| **Intermediate data quality** | Reconciliation covers 80% of issues; anomaly detection catches bad data |
| **Audit trail as immutable log** | Compliance-ready; enables forensics; append-only prevents tampering |

---

## Not in Scope (Phase 1 MVP)

- Real-time streaming (CDC/Kafka) — saves for Phase 5
- Incremental loads — saves for Phase 5 when data grows
- Data lineage & metadata cataloging (dbt, OpenMetadata) — future nice-to-have
- Advanced ML analytics (anomaly detection ML models) — future phase
- Multi-cloud/distributed storage — starts simple, single PostgreSQL
- Tableau/Looker integration — starts with Angular frontend, SQL access for analysts
- Encryption at rest/in transit — scope later if compliance requires
- Historical dimension tracking (Type 2 SCD) — simplified for Phase 1, upgrade later

---

## Timeline & Dependencies

```
Week 1:
  Day 1-3: Phase 1 (Schema + Core ETL + Airflow DAG)
  Day 4-5: Phase 2 (Holdings + Summaries + Hourly Task)
  
Week 2:
  Day 1-2: Phase 3 (Analytics Endpoints in Java backend)
  Day 3-5: Phase 4 (Frontend Dashboard Components)
  
End of Week 2: MVP Complete & Live
```

**Parallel Work**:
- Schema creation + Python scripts can run in parallel (no dependencies)
- Angular dashboard can start development after Phase 1 (mock API data)
- Airflow DAG testing happens once ETL scripts finalized

---

## Summary of Deliverables

1. ✅ OLAP star schema (dims + facts + audit) in PostgreSQL `analytics` schema
2. ✅ Airflow DAGs (daily + hourly) with full orchestration
3. ✅ Python ETL scripts (extract, transform, load, validate)
4. ✅ Java backend analytics API endpoints
5. ✅ Angular dashboard components (trades, holdings, P&L, audit)
6. ✅ Data quality validation & reconciliation checks
7. ✅ Complete audit trail for compliance

**By end of Week 2**: End-to-end working analytics platform with nightly + hourly updates, REST API, and dashboards.

---

## Clarification Questions for Refinement

1. **Data Retention**: Recommend **3 years** rolling window (enough history for trends, manageable storage). Sound good, or would you prefer 1 year / 5+ years?

2. **Slowly-Changing Dimensions (Type 2)**: Should dimension history be tracked? E.g., if an instrument sector changes, track the change with effective dates, or just keep current data? (Start simple = current only, add versioning later)

3. **Trade P&L Calculation**: Should we calculate **realized P&L at trade time** (exit_price = execution_price on SELL) or **unrealized P&L daily** (current_market_price for open positions)? Or both?

4. **Frontend Location**: Should analytics dashboards live in a new route (e.g., `/analytics`) or integrated into existing portfolio page?

5. **Ready to proceed?**: Once you clarify the above, proceed with Phase 1 implementation?

 Linux VM (Docker-Compose)
┌────────────────────────────┐
│  paysprint (OLTP)          │
│  └─ public schema (app)    │
│                            │
│  paysprint_analytics       │
│  ├─ analytics schema ←──┐  │
│  └─ staging schema ←──┐ │  │
│                       │ │  │
│ ┌──────────────────┐  │ │  │
│ │  Airflow         │  │ │  │
│ │  ├─ webserver ◄──┼──┘ │  │
│ │  └─ scheduler ◄──┼────┘  │
│ │    daily_etl.py  │       │
│ └──────────────────┘       │
└────────────────────────────┘