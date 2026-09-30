# Analytics ETL Pipeline - Phase 1: Extraction

Complete, tested extraction module for the trading analytics ETL pipeline.

## What Phase 1 Does

### Extract
Queries 5 tables from the source database (`paysprint.public`):
- **exchanges** — all rows (2 seed records)
- **accounts** — all rows (5 seed records)
- **instruments** — all rows (7 seed records)
- **holdings** — all rows (8 seed records)
- **trades** — last 90 days only (using `executed_at >= NOW() - INTERVAL '90 days'`)

Returns data as Pandas DataFrames with exact schema from source database.

### Transform
Adds **exactly 3 metadata columns** to each DataFrame:
1. `etl_run_id` — unique identifier for this ETL run
2. `etl_timestamp` — UTC datetime when extraction started
3. `source_table` — name of the source table

**No other transformations applied** — data preserved as-is from source.

### Load
Connects to `paysprint_analytics` database and:
1. Creates `staging` schema (if missing)
2. Clears existing staging tables (DROP TABLE IF EXISTS)
3. Loads each DataFrame to corresponding `staging.*_raw` table using bulk insert
4. Returns row counts: `{table_name: (rows_extracted, rows_loaded)}`

### Result
```python
{
  'exchanges': (2, 2),
  'accounts': (5, 5),
  'instruments': (7, 7),
  'holdings': (8, 8),
  'trades': (0, 0)
}