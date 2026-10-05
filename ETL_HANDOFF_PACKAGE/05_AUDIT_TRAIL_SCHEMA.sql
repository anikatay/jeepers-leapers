-- ============================================================================
-- AUDIT TRAIL SCHEMA
-- ============================================================================
-- Track all ETL operations for compliance and debugging

-- Create audit_trail table
CREATE TABLE IF NOT EXISTS analytics.audit_trail (
    audit_id BIGSERIAL PRIMARY KEY,
    etl_run_id VARCHAR(100) NOT NULL,
    operation_timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    module_name VARCHAR(100),  -- extract, pnl, holdings, summary, validate
    operation VARCHAR(50) NOT NULL,  -- EXTRACT, TRANSFORM, LOAD, VALIDATE
    table_name VARCHAR(100),
    rows_affected INTEGER,
    status VARCHAR(20),  -- SUCCESS, FAILED, WARNING
    error_message TEXT,
    execution_time_seconds DECIMAL(10, 2),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit queries
CREATE INDEX IF NOT EXISTS idx_audit_trail_run_id ON analytics.audit_trail(etl_run_id);
CREATE INDEX IF NOT EXISTS idx_audit_trail_timestamp ON analytics.audit_trail(operation_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_trail_module ON analytics.audit_trail(module_name);
CREATE INDEX IF NOT EXISTS idx_audit_trail_status ON analytics.audit_trail(status);

-- View: Daily ETL Summary
CREATE OR REPLACE VIEW analytics.vw_audit_daily_summary AS
SELECT
    DATE(operation_timestamp) as etl_date,
    module_name,
    operation,
    status,
    COUNT(*) as num_operations,
    SUM(rows_affected) as total_rows_affected,
    AVG(execution_time_seconds) as avg_execution_time,
    MAX(operation_timestamp) as latest_run
FROM analytics.audit_trail
WHERE operation_timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(operation_timestamp), module_name, operation, status
ORDER BY etl_date DESC, module_name;

-- View: Failed Operations (Recent)
CREATE OR REPLACE VIEW analytics.vw_audit_failed_operations AS
SELECT
    audit_id,
    etl_run_id,
    operation_timestamp,
    module_name,
    operation,
    table_name,
    error_message
FROM analytics.audit_trail
WHERE status = 'FAILED'
  AND operation_timestamp >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY operation_timestamp DESC;
