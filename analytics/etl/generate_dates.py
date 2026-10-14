"""
Generate Date Dimension for Analytics Schema
Phase 2: Populate dim_dates with calendar data (2024-2028)

Purpose: Creates a calendar table for time-based joins and aggregations in analytics queries.
Columns: date, year, month, quarter, week_of_year, day_of_week, day_of_month, is_trading_day

Usage:
    from etl.generate_dates import generate_date_dimension
    generate_date_dimension()  # Loads 1460+ rows to analytics.dim_dates
"""

import logging
from datetime import date, datetime, timedelta
from typing import Optional
import pandas as pd
from sqlalchemy import text
from etl.config import get_staging_engine

logger = logging.getLogger(__name__)


def generate_date_dimension(num_days: int = 60, start_date: Optional[date] = None,
                            end_date: Optional[date] = None) -> None:
    """
    Generate date dimension table for analytics schema.
    
    Creates one row per date, either for an explicit range or for a window
    of num_days centered on today.
    Includes columns for year, month, quarter, week, day_of_week, and is_trading_day flag.
    
    Note: truncates analytics.dim_dates with CASCADE, which also empties the
    fact tables that reference it. Run before the fact loads, not after.
    
    Args:
        num_days: Number of days to generate when no range is given (default: 60 for testing)
        start_date: First date to generate (default: today - num_days // 2)
        end_date: Last date to generate (default: today + num_days // 2)
    
    Returns:
        None (inserts directly into database)
    
    Raises:
        Exception: If database connection fails
    """
    try:
        # Default range: last 30 days + next 30 days (centered on today)
        # This gives a good window for testing without huge tables
        today = datetime.now().date()
        if start_date is None:
            start_date = today - timedelta(days=num_days // 2)
        if end_date is None:
            end_date = today + timedelta(days=num_days // 2)
        
        logger.info(f"Generating date dimension from {start_date} to {end_date}")
        
        date_range = pd.date_range(start=start_date, end=end_date, freq='D')
        
        logger.info(f"Generated {len(date_range)} dates from {start_date} to {end_date}")
        
        # Create DataFrame with all date components
        df = pd.DataFrame({
            'date': date_range.date,
            'year': date_range.year,
            'month': date_range.month,
            'quarter': date_range.quarter,
            'week_of_year': date_range.isocalendar().week,
            'day_of_week': date_range.day_name(),
            'day_of_month': date_range.day,
            # is_trading_day: True for Mon-Fri (0-4), False for Sat-Sun (5-6)
            'is_trading_day': ~date_range.dayofweek.isin([5, 6])
        })
        
        logger.info(f"DataFrame created with {len(df)} rows, {len(df.columns)} columns")
        
        # Load to database
        engine = get_staging_engine()  # Use staging engine (paysprint_analytics)
        
        logger.info("Loading date dimension to analytics.dim_dates...")
        
        # Truncate existing data with CASCADE (needed due to FK constraints from fact tables)
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE analytics.dim_dates CASCADE"))
            logger.info("Truncated existing data from analytics.dim_dates (with CASCADE)")
        
        # Insert new data
        df.to_sql(
            'dim_dates',
            engine,
            schema='analytics',
            if_exists='append',  # Append to already-created table (just truncated)
            index=False,
            chunksize=1000,
            method='multi'
        )
        
        logger.info(f"Successfully loaded {len(df)} rows to analytics.dim_dates")
        
        # Verify
        with engine.begin() as conn:
            result = conn.execute(text("SELECT COUNT(*) as cnt FROM analytics.dim_dates"))
            row_count = result.scalar()
            logger.info(f"Verification: analytics.dim_dates now contains {row_count} rows")
        
    except Exception as e:
        logger.error(f"Failed to generate date dimension: {str(e)}")
        raise


def verify_date_dimension() -> dict:
    """
    Verify date dimension was created correctly.
    
    Returns:
        dict with verification results:
        {
            'total_rows': int,
            'date_range': (min_date, max_date),
            'trading_days': int,
            'weekend_days': int,
            'years_present': list
        }
    """
    try:
        engine = get_staging_engine()
        
        with engine.begin() as conn:
            # Total rows
            result = conn.execute(text("SELECT COUNT(*) as cnt FROM analytics.dim_dates"))
            total_rows = result.scalar()
            
            # Date range
            result = conn.execute(text(
                "SELECT MIN(date) as min_date, MAX(date) as max_date FROM analytics.dim_dates"
            ))
            min_date, max_date = result.fetchone()
            
            # Trading days
            result = conn.execute(text(
                "SELECT COUNT(*) FROM analytics.dim_dates WHERE is_trading_day = TRUE"
            ))
            trading_days = result.scalar()
            
            # Weekend days
            weekend_days = total_rows - trading_days
            
            # Years
            result = conn.execute(text(
                "SELECT DISTINCT year FROM analytics.dim_dates ORDER BY year"
            ))
            years = [row[0] for row in result.fetchall()]
        
        return {
            'total_rows': total_rows,
            'date_range': (min_date, max_date),
            'trading_days': trading_days,
            'weekend_days': weekend_days,
            'years_present': years
        }
    
    except Exception as e:
        logger.error(f"Date dimension verification failed: {str(e)}")
        raise


if __name__ == '__main__':
    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format='[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s'
    )
    
    # Generate date dimension
    generate_date_dimension()
    
    # Verify
    stats = verify_date_dimension()
    print("\n=== Date Dimension Verification ===")
    print(f"Total rows: {stats['total_rows']}")
    print(f"Date range: {stats['date_range'][0]} to {stats['date_range'][1]}")
    print(f"Trading days (Mon-Fri): {stats['trading_days']}")
    print(f"Weekend days (Sat-Sun): {stats['weekend_days']}")
    print(f"Years: {stats['years_present']}")
