"""
Pytest configuration and shared fixtures for ETL tests
"""

import pytest
import pandas as pd
from datetime import datetime
import uuid


@pytest.fixture(scope="session")
def test_db_url():
    """Test database URL - can be overridden via environment variable"""
    import os
    return os.getenv('TEST_DB_URL', 'postgresql://paysprint:j33p3rs!@localhost:8100/paysprint_analytics_test')


@pytest.fixture
def mock_exchanges_data():
    """Mock exchanges table data"""
    return pd.DataFrame({
        'exchange_id': ['NYSE', 'NASDAQ'],
        'name': ['New York Stock Exchange', 'NASDAQ'],
        'region': ['North America', 'North America'],
        'timezone': ['America/New_York', 'America/New_York'],
        'currency': ['USD', 'USD']
    })


@pytest.fixture
def mock_accounts_data():
    """Mock accounts table data"""
    return pd.DataFrame({
        'account_id': [uuid.uuid4(), uuid.uuid4(), uuid.uuid4()],
        'user_id': [uuid.uuid4(), uuid.uuid4(), uuid.uuid4()],
        'currency': ['USD', 'USD', 'EUR'],
        'balance': [50000.0, 75000.0, 100000.0],
        'created_at': [datetime.utcnow(), datetime.utcnow(), datetime.utcnow()]
    })


@pytest.fixture
def mock_instruments_data():
    """Mock instruments table data"""
    return pd.DataFrame({
        'instrument_id': [uuid.uuid4(), uuid.uuid4(), uuid.uuid4()],
        'ticker': ['AAPL', 'GOOGL', 'MSFT'],
        'name': ['Apple Inc.', 'Alphabet Inc.', 'Microsoft Corp.'],
        'exchange_id': ['NASDAQ', 'NASDAQ', 'NASDAQ'],
        'current_price': [229.0, 176.5, 430.25],
        'updated_at': [datetime.utcnow(), datetime.utcnow(), datetime.utcnow()]
    })


@pytest.fixture
def mock_trades_data():
    """Mock trades table data (empty - no trades in last 90 days)"""
    return pd.DataFrame({
        'trade_id': pd.Series([], dtype=object),
        'account_id': pd.Series([], dtype=object),
        'instrument_id': pd.Series([], dtype=object),
        'side': pd.Series([], dtype=str),
        'quantity': pd.Series([], dtype=float),
        'execution_price': pd.Series([], dtype=float),
        'trade_value': pd.Series([], dtype=float),
        'executed_at': pd.Series([], dtype='datetime64[ns]')
    })
