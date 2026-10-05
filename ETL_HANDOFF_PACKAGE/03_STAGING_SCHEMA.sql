-- ============================================================================
-- STAGING SCHEMA DDL
-- ============================================================================
-- Temporary working area for ETL pipeline
-- Raw staging tables for extract step
-- Dead-letter queue for data quality issues

-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- ============================================================================
-- RAW STAGING TABLES (for extract step)
-- ============================================================================

-- staging.exchanges_raw: Raw exchange data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.exchanges_raw (
    exchange_id VARCHAR(10),
    exchange_name VARCHAR(100),
    region VARCHAR(50),
    timezone VARCHAR(50),
    currency VARCHAR(3),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    etl_run_id VARCHAR(100)  -- Airflow DAG run ID for tracking
);

-- staging.accounts_raw: Raw account data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.accounts_raw (
    account_id UUID,
    user_id UUID,
    status VARCHAR(20),
    currency VARCHAR(3),
    balance NUMERIC(18, 4),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    etl_run_id VARCHAR(100)
);

-- staging.instruments_raw: Raw instrument data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.instruments_raw (
    instrument_id UUID,
    ticker VARCHAR(20),
    instrument_name VARCHAR(200),
    exchange_id VARCHAR(10),
    current_price NUMERIC(18, 4),
    price_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    etl_run_id VARCHAR(100)
);

-- staging.holdings_raw: Raw holdings data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.holdings_raw (
    account_id UUID,
    instrument_id UUID,
    quantity NUMERIC(18, 8),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    etl_run_id VARCHAR(100)
);

-- staging.trades_raw: Raw trade data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.trades_raw (
    trade_id UUID,
    account_id UUID,
    instrument_id UUID,
    side VARCHAR(10),
    quantity NUMERIC(18, 8),
    execution_price NUMERIC(18, 4),
    trade_value NUMERIC(18, 4),
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    etl_run_id VARCHAR(100)
);

-- ============================================================================
-- DEAD-LETTER QUEUE (for rejected rows)
-- ============================================================================

-- staging.dead_letter_queue: Rejected rows with error details (for manual investigation)
CREATE TABLE IF NOT EXISTS staging.dead_letter_queue (
    dlq_id BIGSERIAL PRIMARY KEY,
    etl_run_id VARCHAR(100) NOT NULL,
    operation VARCHAR(50) NOT NULL,  -- extract, transform, load
    source_table VARCHAR(100) NOT NULL,
    source_row_id VARCHAR(100),  -- Original row ID from source
    rejection_reason VARCHAR(500) NOT NULL,  -- Why was this row rejected?
    rejection_details TEXT,  -- Full error message/stacktrace
    raw_data JSONB NOT NULL,  -- Store the rejected row as JSON
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,  -- When was this investigated?
    reviewed_by VARCHAR(100),  -- Who reviewed it?
    resolution VARCHAR(50),  -- FIXED, IGNORED, ESCALATED
    resolution_notes TEXT
);

-- Indexes for DLQ queries
CREATE INDEX IF NOT EXISTS idx_dlq_etl_run_id ON staging.dead_letter_queue(etl_run_id);
CREATE INDEX IF NOT EXISTS idx_dlq_created_at ON staging.dead_letter_queue(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dlq_source_table ON staging.dead_letter_queue(source_table);
CREATE INDEX IF NOT EXISTS idx_dlq_rejection_reason ON staging.dead_letter_queue(rejection_reason);
CREATE INDEX IF NOT EXISTS idx_dlq_resolved ON staging.dead_letter_queue(resolution);

-- ============================================================================
-- MONITORING VIEWS
-- ============================================================================

-- View: DLQ Summary
CREATE OR REPLACE VIEW staging.vw_dlq_summary AS
SELECT
    etl_run_id,
    operation,
    source_table,
    rejection_reason,
    COUNT(*) AS num_rejected_rows,
    MAX(created_at) AS latest_rejection
FROM staging.dead_letter_queue
WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY etl_run_id, operation, source_table, rejection_reason
ORDER BY num_rejected_rows DESC;

-- View: Staging Data Quality Summary
CREATE OR REPLACE VIEW staging.vw_staging_summary AS
SELECT
    'exchanges_raw' as table_name,
    (SELECT COUNT(*) FROM staging.exchanges_raw) as total_rows,
    (SELECT COUNT(DISTINCT etl_run_id) FROM staging.exchanges_raw) as num_runs
UNION ALL
SELECT
    'accounts_raw',
    (SELECT COUNT(*) FROM staging.accounts_raw),
    (SELECT COUNT(DISTINCT etl_run_id) FROM staging.accounts_raw)
UNION ALL
SELECT
    'instruments_raw',
    (SELECT COUNT(*) FROM staging.instruments_raw),
    (SELECT COUNT(DISTINCT etl_run_id) FROM staging.instruments_raw)
UNION ALL
SELECT
    'holdings_raw',
    (SELECT COUNT(*) FROM staging.holdings_raw),
    (SELECT COUNT(DISTINCT etl_run_id) FROM staging.holdings_raw)
UNION ALL
SELECT
    'trades_raw',
    (SELECT COUNT(*) FROM staging.trades_raw),
    (SELECT COUNT(DISTINCT etl_run_id) FROM staging.trades_raw);
