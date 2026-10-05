"""
ETL Configuration

Database connections, thresholds, and settings for all ETL modules.

Environment Variables:
  OLTP_HOST: OLTP database hostname (default: 'localhost')
  OLTP_PORT: OLTP database port (default: 5432)
  OLTP_DB: OLTP database name (default: 'trading_platform')
  OLTP_USER: OLTP database user
  OLTP_PASSWORD: OLTP database password
  
  OLAP_HOST: OLAP database hostname (default: 'localhost')
  OLAP_PORT: OLAP database port (default: 5432)
  OLAP_DB: OLAP database name (default: 'trading_analytics')
  OLAP_USER: OLAP database user
  OLAP_PASSWORD: OLAP database password
  
  LOG_LEVEL: Logging level (default: 'INFO')
  LOG_FILE: Log file path (default: 'logs/etl.log')
"""

import os
from datetime import datetime

# ============================================================================
# ENVIRONMENT DETECTION
# ============================================================================

ENVIRONMENT = os.getenv("ENVIRONMENT", "local")  # local, docker, cloud

# ============================================================================
# DATABASE CONNECTIONS
# ============================================================================

# OLTP Database (source of truth - production database)
OLTP_DB_CONFIG = {
    "host": os.getenv("OLTP_HOST", "localhost"),
    "port": int(os.getenv("OLTP_PORT", "5432")),
    "database": os.getenv("OLTP_DB", "trading_platform"),
    "user": os.getenv("OLTP_USER", "postgres"),
    "password": os.getenv("OLTP_PASSWORD", "password"),
    "schema": "public",  # OLTP schema
}

# OLAP Database (analytics target - separate database or separate schema)
OLAP_DB_CONFIG = {
    "host": os.getenv("OLAP_HOST", "localhost"),
    "port": int(os.getenv("OLAP_PORT", "5432")),
    "database": os.getenv("OLAP_DB", "trading_analytics"),
    "user": os.getenv("OLAP_USER", "postgres"),
    "password": os.getenv("OLAP_PASSWORD", "password"),
    "schema": "analytics",  # Analytics schema
}

# Staging Database (temporary working area - can be SAME as OLAP DB, different schema)
STAGING_DB_CONFIG = {
    "host": os.getenv("OLAP_HOST", "localhost"),
    "port": int(os.getenv("OLAP_PORT", "5432")),
    "database": os.getenv("OLAP_DB", "trading_analytics"),
    "user": os.getenv("OLAP_USER", "postgres"),
    "password": os.getenv("OLAP_PASSWORD", "password"),
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
ETL_START_DATE = os.getenv("ETL_START_DATE", "2025-01-01")  # YYYY-MM-DD
ETL_LOOKBACK_DAYS = int(os.getenv("ETL_LOOKBACK_DAYS", "90"))  # Days to look back for data

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
LOG_FILE = os.getenv("LOG_FILE", "logs/etl.log")

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def get_oltp_engine():
    """Create SQLAlchemy engine for OLTP database"""
    from sqlalchemy import create_engine
    return create_engine(OLTP_CONNECTION_STRING, echo=False, pool_pre_ping=True)

def get_olap_engine():
    """Create SQLAlchemy engine for OLAP database"""
    from sqlalchemy import create_engine
    return create_engine(OLAP_CONNECTION_STRING, echo=False, pool_pre_ping=True)

def get_staging_engine():
    """Create SQLAlchemy engine for Staging database"""
    from sqlalchemy import create_engine
    return create_engine(STAGING_CONNECTION_STRING, echo=False, pool_pre_ping=True)
