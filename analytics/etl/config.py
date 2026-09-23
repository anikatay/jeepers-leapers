"""
Configuration for Analytics ETL Pipeline

Database connections, thresholds, and settings

Environment Variables:
  DB_HOST: Database hostname (default: 'db' for container, 'localhost' for local)
  DB_PORT: Database port (default: 5432)
  DB_NAME: OLTP database name (default: 'paysprint')
  DB_ANALYTICS_NAME: Analytics database name (default: 'paysprint_analytics')
  DB_USER: Database user (default: 'paysprint')
  DB_PASSWORD: Database password (required)
  ENVIRONMENT: 'docker' or 'local' (default: auto-detect)
"""

import os
from datetime import datetime

# ============================================================================
# ENVIRONMENT DETECTION
# ============================================================================

ENVIRONMENT = os.getenv("ENVIRONMENT", "docker")  # docker or local

# Default hosts based on environment
DEFAULT_HOST = "db" if ENVIRONMENT == "docker" else "localhost"

# ============================================================================
# DATABASE CONNECTIONS
# ============================================================================

# OLTP Database (source of truth)
OLTP_DB_CONFIG = {
    "host": os.getenv("DB_HOST", DEFAULT_HOST),
    "port": int(os.getenv("DB_PORT", "5432")),
    "database": os.getenv("DB_NAME", "paysprint"),
    "user": os.getenv("DB_USER", "paysprint"),
    "password": os.getenv("DB_PASSWORD", "paysprint"),
    "schema": "public",  # OLTP schema
}

# OLAP Database (analytics target) - SEPARATE DATABASE
OLAP_DB_CONFIG = {
    "host": os.getenv("DB_HOST", DEFAULT_HOST),
    "port": int(os.getenv("DB_PORT", "5432")),
    "database": os.getenv("DB_ANALYTICS_NAME", "paysprint_analytics"),
    "user": os.getenv("DB_USER", "paysprint"),
    "password": os.getenv("DB_PASSWORD", "paysprint"),
    "schema": "analytics",  # Analytics schema
}

# Staging Database (temporary working area) - SAME AS ANALYTICS DB
STAGING_DB_CONFIG = {
    "host": os.getenv("DB_HOST", DEFAULT_HOST),
    "port": int(os.getenv("DB_PORT", "5432")),
    "database": os.getenv("DB_ANALYTICS_NAME", "paysprint_analytics"),
    "user": os.getenv("DB_USER", "paysprint"),
    "password": os.getenv("DB_PASSWORD", "paysprint"),
    "schema": "staging",  # Staging schema
}

# ============================================================================
# SQLALCHEMY CONNECTION STRINGS
# ============================================================================

def build_connection_string(config):
    """Build SQLAlchemy PostgreSQL connection string"""
    return f"postgresql://{config['user']}:{config['password']}@{config['host']}:{config['port']}/{config['database']}"

OLTP_CONNECTION_STRING = build_connection_string(OLTP_DB_CONFIG)
OLAP_CONNECTION_STRING = build_connection_string(OLAP_DB_CONFIG)
STAGING_CONNECTION_STRING = build_connection_string(STAGING_DB_CONFIG)

# ============================================================================
# ETL CONFIGURATION
# ============================================================================

# Date range for ETL processing
ETL_START_DATE = os.getenv("ETL_START_DATE", "2025-08-01")  # YYYY-MM-DD
ETL_LOOKBACK_DAYS = int(os.getenv("ETL_LOOKBACK_DAYS", "90"))  # Days to look back for data

# Data retention policy
DATA_RETENTION_DAYS = int(os.getenv("DATA_RETENTION_DAYS", 0))  # 0 = keep indefinitely

# ============================================================================
# DATA QUALITY THRESHOLDS
# ============================================================================

# Row count reconciliation tolerance (%)
ROW_COUNT_TOLERANCE = 5.0

# Price validation
PRICE_MIN = 0.01  # Minimum valid price
PRICE_MAX = 1_000_000  # Maximum valid price

# Quantity validation
QUANTITY_MIN = 0.0001  # Minimum valid quantity (fractional shares)
QUANTITY_MAX = 1_000_000  # Maximum valid quantity

# Date validation
DATE_FUTURE_TOLERANCE_DAYS = 1  # Allow trades up to 1 day in future

# Anomaly detection: Price deviation (standard deviations)
PRICE_ANOMALY_THRESHOLD_SIGMA = 2.0

# Anomaly detection: Volume spike multiplier
VOLUME_SPIKE_MULTIPLIER = 5.0

# ============================================================================
# BATCH PROCESSING
# ============================================================================

# Batch size for loading data (rows per INSERT)
BATCH_SIZE = 10000

# Number of retries on database errors
MAX_RETRIES = 3

# Retry delay in seconds
RETRY_DELAY_SECONDS = 5

# ============================================================================
# LOGGING
# ============================================================================

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
LOG_FILE = os.getenv("LOG_FILE", "/var/log/airflow/analytics_etl.log")

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def get_oltp_engine():
    """Create SQLAlchemy engine for OLTP database"""
    from sqlalchemy import create_engine
    return create_engine(OLTP_CONNECTION_STRING, echo=False)

def get_olap_engine():
    """Create SQLAlchemy engine for OLAP database"""
    from sqlalchemy import create_engine
    return create_engine(OLAP_CONNECTION_STRING, echo=False)

def get_staging_engine():
    """Create SQLAlchemy engine for Staging database"""
    from sqlalchemy import create_engine
    return create_engine(STAGING_CONNECTION_STRING, echo=False)
