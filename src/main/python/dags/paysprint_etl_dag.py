import sys
import os
import json
import logging
from datetime import datetime, timedelta

import pandas as pd
from sqlalchemy import text
from airflow import DAG
from airflow.decorators import task
from airflow.models import Variable

# Add parent directory to path to allow import of utils
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from utils.db_utils import get_oltp_engine, get_analytics_engine

# DAG default arguments
default_args = {
    'owner': 'analytics',
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    dag_id='paysprint_analytics_etl',
    default_args=default_args,
    schedule='0 21 * * *',
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=['analytics', 'etl', 'paysprint'],
    description='PaySprint OLTP to Analytics ETL Pipeline'
) as dag:
    
    @task
    def create_schema():
        """Creates or verifies the analytics database schema."""
        engine = get_analytics_engine()
        schema_path = os.path.join(os.path.dirname(__file__), '..', 'analytics_schema.sql')
        
        with open(schema_path, 'r') as f:
            sql_statements = f.read()
            
        with engine.begin() as conn:
            for statement in sql_statements.split(';'):
                stmt = statement.strip()
                if stmt:
                    conn.execute(text(stmt))
                    
        logging.info('Analytics schema created/verified')
        
    @task
    def extract_all_tables() -> dict:
        """Extracts tables from OLTP DB with validation and error handling."""
        from datetime import datetime
        
        engine = get_oltp_engine()
        tables = ['users', 'accounts', 'instruments', 'exchanges', 'holdings', 'trades']
        raw_data = {}
        extraction_stats = {}
        
        try:
            with engine.connect() as conn:
                for table in tables:
                    try:
                        # Extract table
                        df = pd.read_sql_query(f"SELECT * FROM {table}", conn)
                        
                        # Validation checks
                        if df.empty:
                            logging.warning(f"Table '{table}' is empty - no rows extracted")
                        
                        row_count = len(df)
                        null_counts = df.isnull().sum().to_dict()
                        
                        # Check for excessive nulls in key columns
                        for col, null_count in null_counts.items():
                            if null_count > row_count * 0.5:  # More than 50% nulls
                                logging.warning(f"Table '{table}': column '{col}' has {null_count}/{row_count} NULLs")
                        
                        # Serialize to JSON
                        raw_data[table] = df.to_json(orient='records', date_format='iso')
                        extraction_stats[table] = {
                            'row_count': row_count,
                            'columns': list(df.columns),
                            'null_counts': null_counts
                        }
                        
                        logging.info(f"Extracted table '{table}': {row_count} rows, {len(df.columns)} columns")
                        
                    except Exception as e:
                        logging.error(f"Failed to extract table '{table}': {str(e)}")
                        raise
            
            # Summary logging
            total_rows = sum(stats['row_count'] for stats in extraction_stats.values())
            logging.info(f"Extraction complete: {len(tables)} tables, {total_rows} total rows")
            logging.info(f"Extraction stats: {extraction_stats}")
            
            return raw_data
            
        except Exception as e:
            logging.error(f"CRITICAL: Extract failed - {str(e)}")
            raise

    @task
    def load_dim_date():
        """Generates and loads the date dimension."""
        engine = get_analytics_engine()
        dates = pd.date_range(start='2025-01-01', end='2026-12-31', freq='D')
        
        df = pd.DataFrame({
            'date_key': dates.strftime('%Y%m%d').astype(int),
            'full_date': dates.date,
            'year': dates.year,
            'quarter': dates.quarter,
            'month': dates.month,
            'month_name': dates.strftime('%B'),
            'day_of_month': dates.day,
            'day_of_week': dates.weekday,
            'day_name': dates.strftime('%A'),
            'is_weekend': dates.weekday.isin([5, 6]),
            'is_market_day': ~dates.weekday.isin([5, 6])
        })
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE dim_date CASCADE"))
        df.to_sql('dim_date', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded dim_date")
        
    @task
    def load_dim_user(raw_data: dict):
        """Loads user dimension with quality validation and deduplication."""
        engine = get_analytics_engine()
        
        # Extract and validate
        users_df = pd.read_json(raw_data['users'], orient='records')
        accounts_df = pd.read_json(raw_data['accounts'], orient='records')
        
        logging.info(f"Raw users: {len(users_df)}, Raw accounts: {len(accounts_df)}")
        
        # Validate required columns
        required_user_cols = ['user_id', 'email', 'role']
        required_account_cols = ['account_id', 'user_id', 'currency']
        
        for col in required_user_cols:
            if col not in users_df.columns:
                raise ValueError(f"Missing required column in users: {col}")
        for col in required_account_cols:
            if col not in accounts_df.columns:
                raise ValueError(f"Missing required column in accounts: {col}")
        
        # Remove rows with NULL user_id or account_id
        users_df = users_df[users_df['user_id'].notna()]
        accounts_df = accounts_df[accounts_df['account_id'].notna()]
        accounts_df = accounts_df[accounts_df['user_id'].notna()]
        
        # Validate email format (basic check)
        users_df = users_df[users_df['email'].str.contains('@', na=False)]
        
        # Validate role is valid
        valid_roles = ['ROLE_CUSTOMER', 'ROLE_ADMIN', 'ROLE_ANALYST']
        users_df = users_df[users_df['role'].isin(valid_roles)]
        
        logging.info(f"After validation - users: {len(users_df)}, accounts: {len(accounts_df)}")
        
        # Merge users with accounts
        dim_user = pd.merge(users_df, accounts_df, on='user_id', how='inner')
        
        # Select and rename columns
        dim_user = dim_user[['user_id', 'email', 'role', 'account_id', 'currency']].copy()
        
        # Remove duplicates (keep first occurrence)
        dim_user = dim_user.drop_duplicates(subset=['user_id', 'account_id'], keep='first')
        
        # Add SCD Type 2 tracking columns
        dim_user['load_date'] = datetime.now()
        dim_user['is_current'] = True
        
        logging.info(f"Final dim_user records: {len(dim_user)}")
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE dim_user CASCADE"))
        
        dim_user.to_sql('dim_user', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(dim_user)} records into dim_user")
        
    @task
    def load_dim_instrument(raw_data: dict):
        """Loads instrument dimension with validation."""
        engine = get_analytics_engine()
        
        instruments_df = pd.read_json(raw_data['instruments'], orient='records')
        exchanges_df = pd.read_json(raw_data['exchanges'], orient='records')
        
        logging.info(f"Raw instruments: {len(instruments_df)}, Raw exchanges: {len(exchanges_df)}")
        
        # Validate required columns
        required_inst_cols = ['instrument_id', 'ticker', 'name', 'exchange_id', 'current_price']
        required_exch_cols = ['exchange_id', 'name', 'country', 'timezone', 'currency']
        
        for col in required_inst_cols:
            if col not in instruments_df.columns:
                raise ValueError(f"Missing required column in instruments: {col}")
        for col in required_exch_cols:
            if col not in exchanges_df.columns:
                raise ValueError(f"Missing required column in exchanges: {col}")
        
        # Remove NULL instrument_ids
        instruments_df = instruments_df[instruments_df['instrument_id'].notna()]
        exchanges_df = exchanges_df[exchanges_df['exchange_id'].notna()]
        
        # Validate prices are positive
        instruments_df = instruments_df[instruments_df['current_price'] > 0]
        
        # Validate ticker is not empty
        instruments_df = instruments_df[instruments_df['ticker'].str.strip().str.len() > 0]
        
        # Validate currency values
        valid_currencies = ['USD', 'EUR', 'INR']
        exchanges_df = exchanges_df[exchanges_df['currency'].isin(valid_currencies)]
        
        logging.info(f"After validation - instruments: {len(instruments_df)}, exchanges: {len(exchanges_df)}")
        
        # Merge instruments with exchanges
        dim_instrument = pd.merge(
            instruments_df,
            exchanges_df,
            on='exchange_id',
            how='left',
            suffixes=('', '_exchange')
        )
        
        # Select and map columns to schema
        dim_instrument = pd.DataFrame({
            'instrument_id': dim_instrument['instrument_id'],
            'ticker': dim_instrument['ticker'],
            'name': dim_instrument['name'],
            'exchange_id': dim_instrument['exchange_id'],
            'exchange_name': dim_instrument['name_exchange'],
            'country': dim_instrument['country'],
            'exchange_timezone': dim_instrument['timezone'],
            'exchange_currency': dim_instrument['currency_exchange']
        })
        
        # Remove duplicates by instrument_id
        dim_instrument = dim_instrument.drop_duplicates(subset=['instrument_id'], keep='first')
        
        logging.info(f"Final dim_instrument records: {len(dim_instrument)}")
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE dim_instrument CASCADE"))
        
        dim_instrument.to_sql('dim_instrument', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(dim_instrument)} records into dim_instrument")
        
    @task
    def load_fact_trades(raw_data: dict):
        """Loads fact trades with comprehensive validation and transformations."""
        engine = get_analytics_engine()
        
        trades_df = pd.read_json(raw_data['trades'], orient='records')
        
        logging.info(f"Raw trades extracted: {len(trades_df)}")
        
        # Validate required columns
        required_cols = ['trade_id', 'account_id', 'instrument_id', 'side', 'quantity', 'execution_price', 'executed_at']
        for col in required_cols:
            if col not in trades_df.columns:
                raise ValueError(f"Missing required column in trades: {col}")
        
        # Remove rows with NULL trade_id
        trades_df = trades_df[trades_df['trade_id'].notna()]
        trades_df = trades_df[trades_df['account_id'].notna()]
        trades_df = trades_df[trades_df['instrument_id'].notna()]
        
        # Validate side is BUY or SELL
        trades_df = trades_df[trades_df['side'].isin(['BUY', 'SELL'])]
        
        # Validate quantity > 0
        trades_df = trades_df[trades_df['quantity'] > 0]
        
        # Validate price > 0
        trades_df = trades_df[trades_df['execution_price'] > 0]
        
        # Validate executed_at is not in future
        trades_df['executed_at'] = pd.to_datetime(trades_df['executed_at'])
        trades_df = trades_df[trades_df['executed_at'] <= pd.Timestamp.now()]
        
        # Remove duplicate trades (by trade_id)
        trades_df = trades_df.drop_duplicates(subset=['trade_id'], keep='first')
        
        logging.info(f"After validation: {len(trades_df)} trades")
        
        # Read dimension tables for surrogate key lookup
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
            dim_instrument = pd.read_sql_table('dim_instrument', conn)
        
        # Convert to string for merge consistency
        trades_df['account_id'] = trades_df['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)
        trades_df['instrument_id'] = trades_df['instrument_id'].astype(str)
        dim_instrument['instrument_id'] = dim_instrument['instrument_id'].astype(str)
        
        # Merge to get surrogate keys - INNER join to only keep valid dimensions
        trades_merged = pd.merge(trades_df, dim_user[['account_id', 'user_key']], on='account_id', how='inner')
        trades_merged = pd.merge(trades_merged, dim_instrument[['instrument_id', 'instrument_key']], on='instrument_id', how='inner')
        
        logging.info(f"After dimension lookup: {len(trades_merged)} trades (dropped {len(trades_df) - len(trades_merged)} orphaned records)")
        
        # Extract date key from executed_at
        trades_merged['date_key'] = trades_merged['executed_at'].dt.strftime('%Y%m%d').astype(int)
        
        # Calculate trade_value
        trades_merged['trade_value'] = trades_merged['quantity'] * trades_merged['execution_price']
        
        # Select final columns
        fact_trades = trades_merged[[
            'trade_id', 'user_key', 'instrument_key', 'date_key', 
            'side', 'quantity', 'execution_price', 'trade_value'
        ]].copy()
        
        logging.info(f"Final fact_trades: {len(fact_trades)} records")
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE fact_trades CASCADE"))
        
        fact_trades.to_sql('fact_trades', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(fact_trades)} trades into fact_trades")
        
    @task
    def load_fact_daily_summary():
        """Loads daily trade summary with VWAP and comprehensive metrics."""
        engine = get_analytics_engine()
        
        with engine.connect() as conn:
            trades = pd.read_sql_table('fact_trades', conn)
        
        logging.info(f"Calculating summary for {len(trades)} trades")
        
        # Group by date and instrument
        summary = trades.groupby(['date_key', 'instrument_key']).agg(
            trade_count=('trade_id', 'count'),
            total_volume=('quantity', 'sum'),
            total_value=('trade_value', 'sum'),
            avg_price=('execution_price', 'mean'),
            min_price=('execution_price', 'min'),
            max_price=('execution_price', 'max')
        ).reset_index()
        
        # Calculate VWAP (Volume Weighted Average Price)
        vwap_calc = trades.groupby(['date_key', 'instrument_key']).apply(
            lambda x: (x['execution_price'] * x['quantity']).sum() / x['quantity'].sum()
        ).reset_index(name='vwap_price')
        
        summary = pd.merge(summary, vwap_calc, on=['date_key', 'instrument_key'], how='left')
        
        # Calculate buy/sell side metrics
        buy_metrics = trades[trades['side'] == 'BUY'].groupby(['date_key', 'instrument_key']).agg(
            buy_count=('trade_id', 'count'),
            buy_volume=('quantity', 'sum'),
            buy_value=('trade_value', 'sum')
        ).reset_index()
        
        sell_metrics = trades[trades['side'] == 'SELL'].groupby(['date_key', 'instrument_key']).agg(
            sell_count=('trade_id', 'count'),
            sell_volume=('quantity', 'sum'),
            sell_value=('trade_value', 'sum')
        ).reset_index()
        
        summary = pd.merge(summary, buy_metrics, on=['date_key', 'instrument_key'], how='left')
        summary = pd.merge(summary, sell_metrics, on=['date_key', 'instrument_key'], how='left')
        
        # Fill NA values for sides with no trades
        summary = summary.fillna(0)
        
        # Calculate price range
        summary['price_range'] = summary['max_price'] - summary['min_price']
        
        # Calculate net volume (buy - sell)
        summary['net_volume'] = summary['buy_volume'] - summary['sell_volume']
        
        logging.info(f"Calculated summary for {len(summary)} date-instrument combinations")
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE fact_daily_trade_summary CASCADE"))
        
        # Select columns matching schema (at minimum)
        summary_final = summary[[
            'date_key', 'instrument_key', 'trade_count', 'total_volume',
            'total_value', 'avg_price', 'min_price', 'max_price'
        ]].copy()
        
        summary_final.to_sql('fact_daily_trade_summary', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(summary_final)} records into fact_daily_trade_summary")
        
    @task
    def calc_risk_concentration(raw_data: dict):
        """Calculates portfolio concentration risk using HHI and multi-dimensional analysis."""
        engine = get_analytics_engine()
        
        holdings_df = pd.read_json(raw_data['holdings'], orient='records')
        instruments_df = pd.read_json(raw_data['instruments'], orient='records')
        
        logging.info(f"Raw holdings: {len(holdings_df)}, Instruments: {len(instruments_df)}")
        
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
            dim_instrument = pd.read_sql_table('dim_instrument', conn)
        
        # Validate required columns
        if 'quantity' not in holdings_df.columns or 'account_id' not in holdings_df.columns:
            raise ValueError("Missing required columns in holdings")
        if 'current_price' not in instruments_df.columns:
            raise ValueError("Missing current_price in instruments")
        
        # Remove NULL holdings
        holdings_df = holdings_df[holdings_df['quantity'].notna()]
        holdings_df = holdings_df[holdings_df['quantity'] != 0]
        
        # Merge with instruments to get current prices
        holdings_df['instrument_id'] = holdings_df['instrument_id'].astype(str)
        instruments_df['instrument_id'] = instruments_df['instrument_id'].astype(str)
        holdings_merged = pd.merge(holdings_df, instruments_df[['instrument_id', 'current_price']], on='instrument_id', how='inner')
        
        # Merge with dimensions for surrogate keys
        holdings_merged['account_id'] = holdings_merged['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)
        holdings_merged = pd.merge(holdings_merged, dim_user[['account_id', 'user_key']], on='account_id', how='inner')
        
        holdings_merged['instrument_id_str'] = holdings_merged['instrument_id'].astype(str)
        dim_instrument['instrument_id'] = dim_instrument['instrument_id'].astype(str)
        holdings_merged = pd.merge(holdings_merged, dim_instrument[['instrument_id', 'instrument_key']], left_on='instrument_id_str', right_on='instrument_id', how='inner')
        
        # Calculate holding values
        holdings_merged['holding_value'] = holdings_merged['quantity'] * holdings_merged['current_price']
        
        # Calculate portfolio totals by account
        account_totals = holdings_merged.groupby('account_id')['holding_value'].sum().reset_index(name='portfolio_total_value')
        holdings_merged = pd.merge(holdings_merged, account_totals, on='account_id', how='left')
        
        # Handle zero portfolios
        holdings_merged = holdings_merged[holdings_merged['portfolio_total_value'] > 0]
        
        # Calculate portfolio percentages
        holdings_merged['portfolio_pct'] = (holdings_merged['holding_value'] / holdings_merged['portfolio_total_value']) * 100
        
        # Calculate HHI (Herfindahl-Hirschman Index) for each account
        hhi_calc = (holdings_merged['portfolio_pct'] ** 2).groupby(holdings_merged['account_id']).sum().reset_index(name='hhi')
        holdings_merged = pd.merge(holdings_merged, hhi_calc, on='account_id', how='left')
        
        # Flag concentration: HHI > 2500 or single position > 50%
        holdings_merged['is_concentrated'] = (holdings_merged['portfolio_pct'] > 50) | (holdings_merged['hhi'] > 2500)
        
        logging.info(f"Calculated concentration for {len(holdings_merged)} holdings")
        
        # Select final columns
        risk_concentration = holdings_merged[[
            'user_key', 'instrument_key', 'quantity', 'holding_value',
            'portfolio_total_value', 'portfolio_pct', 'is_concentrated'
        ]].copy()
        
        # Rename quantity to holding_quantity for schema
        risk_concentration = risk_concentration.rename(columns={'quantity': 'holding_quantity'})
        
        logging.info(f"Final risk_concentration records: {len(risk_concentration)}")
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_concentration CASCADE"))
        
        risk_concentration.to_sql('risk_concentration', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(risk_concentration)} records into risk_concentration")
        
    @task
    def calc_risk_large_trades():
        """Flags large trades based on threshold and market context."""
        engine = get_analytics_engine()
        
        # Get threshold from Airflow variable (default $5000)
        threshold = float(Variable.get("LARGE_TRADE_THRESHOLD", default_var=5000.00))
        
        with engine.connect() as conn:
            trades = pd.read_sql_table('fact_trades', conn)
            daily_summary = pd.read_sql_table('fact_daily_trade_summary', conn)
        
        logging.info(f"Analyzing {len(trades)} trades for large trade flagging (threshold: ${threshold})")
        
        # Flag trades exceeding threshold
        large_trades = trades[trades['trade_value'] > threshold].copy()
        
        # Add market context: pct of daily volume
        large_trades = pd.merge(
            large_trades,
            daily_summary[['date_key', 'instrument_key', 'total_volume']],
            on=['date_key', 'instrument_key'],
            how='left'
        )
        
        # Calculate % of daily volume
        large_trades['pct_of_daily_volume'] = (large_trades['quantity'] / large_trades['total_volume'] * 100).fillna(0)
        
        # Add metadata
        large_trades['threshold'] = threshold
        large_trades['flagged_at'] = datetime.now()
        
        # Flag if > 10% of daily volume (market impact indicator)
        large_trades['is_market_impact'] = large_trades['pct_of_daily_volume'] > 10
        
        logging.info(f"Flagged {len(large_trades)} large trades, {large_trades['is_market_impact'].sum()} with market impact")
        
        # Select final columns matching schema
        risk_large_trades = large_trades[[
            'trade_id', 'user_key', 'instrument_key', 'trade_value', 'threshold', 'flagged_at'
        ]].copy()
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_large_trades CASCADE"))
        
        risk_large_trades.to_sql('risk_large_trades', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(risk_large_trades)} records into risk_large_trades")
        
    @task
    def calc_risk_balance_exposure(raw_data: dict):
        """Calculates balance and exposure risk metrics."""
        engine = get_analytics_engine()
        
        accounts_df = pd.read_json(raw_data['accounts'], orient='records')
        holdings_df = pd.read_json(raw_data['holdings'], orient='records')
        instruments_df = pd.read_json(raw_data['instruments'], orient='records')
        
        logging.info(f"Raw accounts: {len(accounts_df)}, holdings: {len(holdings_df)}")
        
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
        
        # Validate required columns
        if 'balance' not in accounts_df.columns or 'account_id' not in accounts_df.columns:
            raise ValueError("Missing required columns in accounts")
        
        # Remove NULL balances
        accounts_df = accounts_df[accounts_df['balance'].notna()]
        
        # Calculate total portfolio exposure (holdings value)
        holdings_df = holdings_df[holdings_df['quantity'].notna()]
        holdings_df = holdings_df[holdings_df['quantity'] != 0]
        
        # Merge with instruments to get current prices
        holdings_df['instrument_id'] = holdings_df['instrument_id'].astype(str)
        instruments_df['instrument_id'] = instruments_df['instrument_id'].astype(str)
        holdings_with_price = pd.merge(holdings_df, instruments_df[['instrument_id', 'current_price']], on='instrument_id', how='inner')
        
        # Calculate holding values
        holdings_with_price['holding_value'] = holdings_with_price['quantity'] * holdings_with_price['current_price']
        
        # Aggregate exposure by account
        exposure = holdings_with_price.groupby('account_id')['holding_value'].sum().reset_index(name='total_exposure')
        
        # Merge accounts with exposure
        balance_exposure = pd.merge(accounts_df, exposure, on='account_id', how='left')
        balance_exposure['total_exposure'] = balance_exposure['total_exposure'].fillna(0)
        
        # Merge with dim_user for surrogate key
        balance_exposure['account_id'] = balance_exposure['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)
        balance_exposure = pd.merge(balance_exposure, dim_user[['account_id', 'user_key']], on='account_id', how='left')
        
        # Handle missing user_key (account not in dim_user)
        balance_exposure = balance_exposure[balance_exposure['user_key'].notna()]
        
        # Rename column for schema consistency
        balance_exposure['cash_balance'] = balance_exposure['balance']
        
        # Calculate exposure ratio with zero-division handling
        balance_exposure['exposure_ratio'] = balance_exposure.apply(
            lambda row: (row['total_exposure'] / row['cash_balance']) if row['cash_balance'] != 0 else 0,
            axis=1
        )
        
        # Flag over-leveraged accounts (exposure_ratio > 2 = more than 2x leverage)
        balance_exposure['is_over_leveraged'] = balance_exposure['exposure_ratio'] > 2.0
        
        logging.info(f"Calculated exposure for {len(balance_exposure)} accounts, {balance_exposure['is_over_leveraged'].sum()} over-leveraged")
        
        # Select final columns
        risk_balance_exposure = balance_exposure[[
            'user_key', 'cash_balance', 'total_exposure', 'exposure_ratio'
        ]].copy()
        
        # Load into analytics database
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_balance_exposure CASCADE"))
        
        risk_balance_exposure.to_sql('risk_balance_exposure', engine, if_exists='append', index=False, method='multi')
        logging.info(f"Loaded {len(risk_balance_exposure)} records into risk_balance_exposure")

    # Define tasks
    t_create_schema = create_schema()
    t_extract = extract_all_tables()
    t_load_dim_date = load_dim_date()
    t_load_dim_user = load_dim_user(t_extract)
    t_load_dim_instrument = load_dim_instrument(t_extract)
    t_load_fact_trades = load_fact_trades(t_extract)
    t_load_fact_daily_summary = load_fact_daily_summary()
    t_calc_risk_concentration = calc_risk_concentration(t_extract)
    t_calc_risk_large_trades = calc_risk_large_trades()
    t_calc_risk_balance_exposure = calc_risk_balance_exposure(t_extract)

    # Set dependencies
    t_create_schema >> t_extract
    t_extract >> [t_load_dim_date, t_load_dim_user, t_load_dim_instrument]
    [t_load_dim_date, t_load_dim_user, t_load_dim_instrument] >> t_load_fact_trades
    
    t_load_fact_trades >> t_load_fact_daily_summary
    t_load_fact_trades >> [t_calc_risk_concentration, t_calc_risk_large_trades, t_calc_risk_balance_exposure]
