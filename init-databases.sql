-- ============================================================================
-- init-databases.sql
-- Mounted into the Postgres container at /docker-entrypoint-initdb.d/
-- Runs once on first startup (when the data volume is empty).
-- The default database (paysprint) is already created by POSTGRES_DB env var.
-- ============================================================================

-- Analytics data warehouse
CREATE DATABASE paysprint_analytics;

-- Airflow metadata store
CREATE DATABASE airflow;

