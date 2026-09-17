"""
Database connection utilities for the PaySprint ETL pipeline.

Provides SQLAlchemy engine factories for both the OLTP (paysprint) and
analytics (paysprint_analytics) databases. Connection parameters are read
from environment variables, with defaults matching docker-compose.yml.
"""

import os
from sqlalchemy import create_engine


def _build_url(dbname: str) -> str:
    """Build a PostgreSQL connection URL from environment variables."""
    user = os.getenv("DB_USER", "paysprint")
    password = os.getenv("DB_PASSWORD", "changeme")
    host = os.getenv("DB_HOST", "db")
    port = os.getenv("DB_PORT", "5432")
    try:
        import psycopg2  # noqa: F401
        driver = "psycopg2"
    except ImportError:
        driver = "psycopg"
    return f"postgresql+{driver}://{user}:{password}@{host}:{port}/{dbname}"


def get_oltp_engine():
    """Return a SQLAlchemy engine connected to the OLTP database (paysprint)."""
    db_name = os.getenv("OLTP_DB_NAME", "paysprint")
    return create_engine(_build_url(db_name))


def get_analytics_engine():
    """Return a SQLAlchemy engine connected to the analytics database (paysprint_analytics)."""
    db_name = os.getenv("ANALYTICS_DB_NAME", "paysprint_analytics")
    return create_engine(_build_url(db_name))

