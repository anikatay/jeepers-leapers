"""
HANDOFF CHECKLIST

Use this checklist to hand off the ETL project to a new team/agent.
"""

# ============================================================================
# PRE-HANDOFF VERIFICATION
# ============================================================================

## Documentation ✓
- [x] 00_ARCHITECTURE.md - Complete architecture overview
- [x] 01_PROMPT_FOR_AGENT.md - Detailed implementation guide
- [x] 02_QUICK_START.md - 5-minute setup guide
- [x] 11_README.md - Full documentation

## SQL Schemas ✓
- [x] 03_STAGING_SCHEMA.sql - Staging tables + DLQ
- [x] 04_ANALYTICS_SCHEMA.sql - Star schema + views
- [x] 05_AUDIT_TRAIL_SCHEMA.sql - Audit logging

## Python Code Templates ✓
- [x] 06_CONFIG.py - Database config + constants
- [x] 07_EXTRACT_ETL_TEMPLATE.py - Extract module (complete)
- [x] 08_PNL_ETL_TEMPLATE.py - P&L module (skeleton with FIFO logic)
- [x] (TODO) 09_HOLDINGS_ETL_TEMPLATE.py - Holdings module skeleton
- [x] (TODO) 10_SUMMARY_ETL_TEMPLATE.py - Summary module skeleton
- [x] (TODO) 11_VALIDATE_ETL_TEMPLATE.py - Validate module skeleton

## Configuration ✓
- [x] 09_REQUIREMENTS.txt - Python dependencies
- [x] 10_ENV_EXAMPLE.txt - Environment variables template

## Airflow ✓
- [x] 12_HOURLY_ETL_DAG.py - Complete Airflow DAG

## Tests ✓
- [ ] test_extract.py - Unit tests for extract
- [ ] test_pnl.py - Unit tests for P&L (FIFO matching)
- [ ] test_holdings.py - Unit tests for holdings
- [ ] test_summary.py - Unit tests for summary
- [ ] test_validate.py - Unit tests for validate
- [ ] integration_test.py - End-to-end test

---

# ============================================================================
# HANDOFF INSTRUCTIONS FOR NEW TEAM
# ============================================================================

## STEP 1: Read Documentation (30 min)
1. Start with 00_ARCHITECTURE.md (understand the big picture)
2. Read 01_PROMPT_FOR_AGENT.md (detailed requirements)
3. Skim 11_README.md (quick reference)

## STEP 2: Setup Environment (30 min)
1. Follow 02_QUICK_START.md
2. Create databases: `createdb trading_platform trading_analytics`
3. Run SQL schemas
4. Create .env from .env.example
5. Run `python -c "from config import get_oltp_engine; print(get_oltp_engine().connect())"`

## STEP 3: Implement Extract ETL (2-3 hours)
1. Copy 07_EXTRACT_ETL_TEMPLATE.py → etl/extract_etl.py
2. Adapt column names/queries to match YOUR database schema
3. Test manually: `python etl/extract_etl.py test_run_001`
4. Verify data in staging tables: `SELECT * FROM staging.trades_raw LIMIT 10`

## STEP 4: Implement P&L ETL (4-6 hours) ⚠️ HARDEST PART
1. Copy 08_PNL_ETL_TEMPLATE.py → etl/transform_pnl_etl.py
2. Implement FIFO matching algorithm carefully (see docstring)
3. Write unit tests: tests/test_pnl.py
4. Test with sample data: 10 trades, manually verify P&L calculations
5. Run: `python etl/transform_pnl_etl.py test_run_001`
6. Verify data in analytics.fact_trades

## STEP 5: Implement Holdings ETL (2-3 hours)
1. Create etl/transform_holdings_etl.py (skeleton provided below)
2. Implement cost_basis calculation (FIFO)
3. Implement market_value calculation
4. Load to fact_daily_holdings
5. Test with same sample data

## STEP 6: Implement Summary ETL (1-2 hours)
1. Create etl/transform_account_summary_etl.py (skeleton provided below)
2. Implement daily aggregations
3. Load to fact_daily_account_summary
4. Test with same sample data

## STEP 7: Implement Validate ETL (2-3 hours)
1. Create etl/validate_etl.py (skeleton provided below)
2. Implement row count reconciliation
3. Implement P&L validation
4. Implement anomaly detection
5. Test after all transforms

## STEP 8: Wire Airflow DAG (2-3 hours)
1. Copy 12_HOURLY_ETL_DAG.py → dags/hourly_etl_dag.py
2. Adjust for YOUR Airflow setup
3. Test: `airflow dags test trading_analytics_hourly_etl 2026-01-01`
4. Deploy to production

## STEP 9: End-to-End Testing (3-4 hours)
1. Run full pipeline locally (all 5 modules)
2. Verify data integrity
3. Check audit trail
4. Run Airflow scheduler for 24 hours
5. Monitor logs for errors

## STEP 10: Handoff to Production (2 hours)
1. Set up monitoring + alerts
2. Create runbooks for common issues
3. Schedule backups
4. Document on-call rotation
5. Train ops team

---

# ============================================================================
# SKELETON CODE FOR REMAINING MODULES
# ============================================================================

## transform_holdings_etl.py skeleton

```python
from etl.transform_pnl_etl import PnLTransformer

class HoldingsTransformer:
    def transform_and_load(self):
        # 1. Load holdings from staging
        # 2. Load trades from staging
        # 3. For each (account, instrument):
        #    - Calculate cost_basis (FIFO average)
        #    - Calculate market_value = qty × current_price
        #    - Calculate unrealized_pnl = market_value - (qty × cost_basis)
        # 4. Load to analytics.fact_daily_holdings
        pass
```

## transform_account_summary_etl.py skeleton

```python
class SummaryTransformer:
    def transform_and_load(self):
        # 1. Load trades from staging
        # 2. For each (account_id, summary_date):
        #    - Count: num_buy_trades, num_sell_trades
        #    - Sum: total_volume, total_volume_traded
        #    - Calculate: daily_pnl, cumulative_pnl
        #    - Get: beginning_balance, ending_balance
        # 3. Load to analytics.fact_daily_account_summary
        pass
```

## validate_etl.py skeleton

```python
class DataValidator:
    def run_all_validations(self):
        # 1. Reconcile row counts (OLTP ↔ OLAP)
        # 2. Validate P&L calculations
        # 3. Check for anomalies (price spikes, volume anomalies)
        # 4. Validate data completeness
        # 5. Return validation report
        pass
```

---

# ============================================================================
# EXPECTED TIMELINES
# ============================================================================

| Phase | Duration | Difficulty |
|-------|----------|-----------|
| Setup + Docs | 1 hour | 🟢 Easy |
| Extract ETL | 2-3 hours | 🟢 Easy |
| P&L ETL | 4-6 hours | 🔴 HARD (FIFO logic) |
| Holdings ETL | 2-3 hours | 🟡 Medium |
| Summary ETL | 1-2 hours | 🟢 Easy |
| Validate ETL | 2-3 hours | 🟡 Medium |
| Airflow DAG | 2-3 hours | 🟡 Medium |
| Testing | 3-4 hours | 🟡 Medium |
| **TOTAL** | **17-27 hours** | |

**Realistic Timeline**: 2-3 days of focused development (6-8 hours/day)

---

# ============================================================================
# SUCCESS CRITERIA
# ============================================================================

✅ All 5 ETL modules implemented and tested
✅ Extract pulls all 5 OLTP tables correctly
✅ P&L FIFO matching tested with sample data (manual verification)
✅ Holdings cost_basis calculations correct
✅ Summary aggregations match expected totals
✅ Validation checks all pass
✅ Airflow DAG runs hourly without errors
✅ Analytics tables updated every hour
✅ Audit trail logged all operations
✅ Documentation complete and up-to-date

---

# ============================================================================
# COMMON PITFALLS & SOLUTIONS
# ============================================================================

### ⚠️ FIFO Matching Bug
**Problem**: Realized P&L doesn't match manual calculations
**Solution**: 
- Test with 3-5 sample trades
- Manually calculate expected P&L
- Add print() statements in FIFO loop to debug
- Check: Are you matching oldest BUY with first SELL?

### ⚠️ Out of Memory
**Problem**: Pandas DataFrame too large when loading all staging data
**Solution**:
- Process in smaller batches (don't load all at once)
- Use generators instead of loading full DF
- Check data volume: should be < 100K rows/hour

### ⚠️ Airflow Task Timeout
**Problem**: Extract takes too long (> 10 min)
**Solution**:
- Add indexes to OLTP tables
- Optimize SQL queries (add WHERE clauses for date ranges)
- Increase Airflow task timeout

### ⚠️ Transaction Rollback on Load
**Problem**: Some rows fail to load → whole transaction rolled back
**Solution**:
- Check for NULL values in required columns
- Validate data types match schema
- Route bad rows to dead_letter_queue before loading

### ⚠️ Row Count Reconciliation Fails
**Problem**: OLAP row count ≠ OLTP row count
**Solution**:
- Check if some records are in DLQ (rejected)
- Verify full refresh (DELETE all before INSERT)
- Check tolerance: 5% allowed difference

---

# ============================================================================
# CONTACT & SUPPORT
# ============================================================================

**Questions?**
1. Check README.md troubleshooting section
2. Review architecture docs for design decisions
3. Check logs: `tail -f logs/etl.log`
4. Check audit trail: `SELECT * FROM analytics.audit_trail WHERE DATE(operation_timestamp) = CURRENT_DATE`

**Issues?**
1. Check DLQ for rejected rows: `SELECT * FROM staging.dead_letter_queue ORDER BY created_at DESC`
2. Check failed validations: `SELECT * FROM analytics.vw_audit_failed_operations`
3. Review Airflow task logs: Click task in Airflow UI → Logs tab

---

**This handoff package is ready for a new team to implement and deploy.**
