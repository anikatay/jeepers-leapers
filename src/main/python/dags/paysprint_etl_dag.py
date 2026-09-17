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
        """Extracts tables from OLTP DB and serializes them to JSON."""
        engine = get_oltp_engine()
        tables = ['users', 'accounts', 'instruments', 'exchanges', 'holdings', 'trades']
        raw_data = {}
        
        with engine.connect() as conn:
            for table in tables:
                df = pd.read_sql_query(f"SELECT * FROM {table}", conn)
                raw_data[table] = df.to_json(orient='records', date_format='iso')
                
        logging.info(f"Extracted {len(tables)} tables from OLTP DB.")
        return raw_data

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
        """Loads user dimension."""
        engine = get_analytics_engine()
        
        users = pd.read_json(raw_data['users'], orient='records')
        accounts = pd.read_json(raw_data['accounts'], orient='records')
        
        df = pd.merge(users, accounts, on='user_id', how='inner')
        
        df = df[['user_id', 'email', 'role', 'account_id', 'currency']]
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE dim_user CASCADE"))
        df.to_sql('dim_user', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded dim_user")
        
    @task
    def load_dim_instrument(raw_data: dict):
        """Loads instrument dimension."""
        engine = get_analytics_engine()
        
        instruments = pd.read_json(raw_data['instruments'], orient='records')
        exchanges = pd.read_json(raw_data['exchanges'], orient='records')
        
        df = pd.merge(instruments, exchanges, on='exchange_id', how='left', suffixes=('', '_exchange'))
        
        out_df = pd.DataFrame({
            'instrument_id': df['instrument_id'],
            'ticker': df['ticker'],
            'name': df['name'],
            'exchange_id': df['exchange_id'],
            'exchange_name': df['name_exchange'],
            'country': df['country'],
            'exchange_timezone': df['timezone'],
            'exchange_currency': df['currency']
        })
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE dim_instrument CASCADE"))
        out_df.to_sql('dim_instrument', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded dim_instrument")
        
    @task
    def load_fact_trades(raw_data: dict):
        """Loads fact trades."""
        engine = get_analytics_engine()
        
        trades = pd.read_json(raw_data['trades'], orient='records')
        
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
            dim_instrument = pd.read_sql_table('dim_instrument', conn)
        
        # Merge to get surrogate keys
        trades['account_id'] = trades['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)
        trades['instrument_id'] = trades['instrument_id'].astype(str)
        dim_instrument['instrument_id'] = dim_instrument['instrument_id'].astype(str)

        trades = pd.merge(trades, dim_user[['account_id', 'user_key']], on='account_id', how='inner')
        trades = pd.merge(trades, dim_instrument[['instrument_id', 'instrument_key']], on='instrument_id', how='inner')
        
        trades['executed_at'] = pd.to_datetime(trades['executed_at'])
        trades['date_key'] = trades['executed_at'].dt.strftime('%Y%m%d').astype(int)
        
        trades['trade_value'] = trades['quantity'] * trades['execution_price']
        
        out_df = trades[['trade_id', 'user_key', 'instrument_key', 'date_key', 'side', 'quantity', 'execution_price', 'trade_value']]
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE fact_trades CASCADE"))
        out_df.to_sql('fact_trades', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded fact_trades")
        
    @task
    def load_fact_daily_summary():
        """Loads daily trade summary."""
        engine = get_analytics_engine()
        
        with engine.connect() as conn:
            trades = pd.read_sql_table('fact_trades', conn)
        
        summary = trades.groupby(['date_key', 'instrument_key']).agg(
            trade_count=('trade_id', 'count'),
            total_volume=('quantity', 'sum'),
            total_value=('trade_value', 'sum'),
            avg_price=('execution_price', 'mean'),
            min_price=('execution_price', 'min'),
            max_price=('execution_price', 'max')
        ).reset_index()
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE fact_daily_trade_summary CASCADE"))
        summary.to_sql('fact_daily_trade_summary', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded fact_daily_trade_summary")
        
    @task
    def calc_risk_concentration(raw_data: dict):
        """Calculates risk concentration."""
        engine = get_analytics_engine()
        
        holdings = pd.read_json(raw_data['holdings'], orient='records')
        instruments = pd.read_json(raw_data['instruments'], orient='records')
        
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
            dim_instrument = pd.read_sql_table('dim_instrument', conn)
            
        holdings['instrument_id'] = holdings['instrument_id'].astype(str)
        instruments['instrument_id'] = instruments['instrument_id'].astype(str)
        dim_instrument['instrument_id'] = dim_instrument['instrument_id'].astype(str)
        holdings['account_id'] = holdings['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)

        holdings = pd.merge(holdings, instruments[['instrument_id', 'current_price']], on='instrument_id', how='inner')
        holdings = pd.merge(holdings, dim_user[['account_id', 'user_key']], on='account_id', how='inner')
        holdings = pd.merge(holdings, dim_instrument[['instrument_id', 'instrument_key']], on='instrument_id', how='inner')
        
        holdings['holding_value'] = holdings['quantity'] * holdings['current_price']
        
        account_totals = holdings.groupby('account_id')['holding_value'].sum().reset_index(name='portfolio_total_value')
        holdings = pd.merge(holdings, account_totals, on='account_id', how='inner')
        
        holdings['portfolio_pct'] = (holdings['holding_value'] / holdings['portfolio_total_value']) * 100
        holdings['is_concentrated'] = holdings['portfolio_pct'] > 50
        
        out_df = holdings[['user_key', 'instrument_key', 'holding_value', 'portfolio_pct', 'is_concentrated']]
        out_df = pd.DataFrame({
            'user_key': holdings['user_key'],
            'instrument_key': holdings['instrument_key'],
            'holding_quantity': holdings['quantity'],
            'holding_value': holdings['holding_value'],
            'portfolio_total_value': holdings['portfolio_total_value'],
            'portfolio_pct': holdings['portfolio_pct'],
            'is_concentrated': holdings['is_concentrated'],
        })
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_concentration CASCADE"))
        out_df.to_sql('risk_concentration', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded risk_concentration")
        
    @task
    def calc_risk_large_trades():
        """Calculates large trades risk."""
        engine = get_analytics_engine()
        
        threshold = float(Variable.get("LARGE_TRADE_THRESHOLD", default_var=5000.00))
        
        with engine.connect() as conn:
            trades = pd.read_sql_table('fact_trades', conn)
            
        large_trades = trades[trades['trade_value'] > threshold].copy()
        large_trades['threshold'] = threshold
        
        out_df = large_trades[['trade_id', 'user_key', 'instrument_key', 'trade_value', 'threshold']]
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_large_trades CASCADE"))
        out_df.to_sql('risk_large_trades', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded risk_large_trades")
        
    @task
    def calc_risk_balance_exposure(raw_data: dict):
        """Calculates balance exposure risk."""
        engine = get_analytics_engine()
        
        accounts = pd.read_json(raw_data['accounts'], orient='records')
        holdings = pd.read_json(raw_data['holdings'], orient='records')
        instruments = pd.read_json(raw_data['instruments'], orient='records')
        
        with engine.connect() as conn:
            dim_user = pd.read_sql_table('dim_user', conn)
            
        holdings = pd.merge(holdings, instruments[['instrument_id', 'current_price']], on='instrument_id', how='inner')
        holdings['holding_value'] = holdings['quantity'] * holdings['current_price']
        
        exposure = holdings.groupby('account_id')['holding_value'].sum().reset_index(name='total_exposure')
        
        df = pd.merge(accounts, exposure, on='account_id', how='left')
        df['total_exposure'] = df['total_exposure'].fillna(0)
        
        df['account_id'] = df['account_id'].astype(str)
        dim_user['account_id'] = dim_user['account_id'].astype(str)

        df = pd.merge(df, dim_user[['account_id', 'user_key']], on='account_id', how='inner')
        df['cash_balance'] = df['balance']
        
        # Handle division by zero
        df['exposure_ratio'] = df.apply(lambda row: row['total_exposure'] / row['cash_balance'] if row['cash_balance'] != 0 else 0, axis=1)
        
        out_df = df[['user_key', 'cash_balance', 'total_exposure', 'exposure_ratio']]
        
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE risk_balance_exposure CASCADE"))
        out_df.to_sql('risk_balance_exposure', engine, if_exists='append', index=False, method='multi')
        logging.info("Loaded risk_balance_exposure")

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
