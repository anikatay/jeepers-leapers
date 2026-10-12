"""
Tests for Phase 3: P&L Transformation with FIFO Matching

Test scenarios:
- Simple buy-sell pair (basic FIFO)
- Multiple buys, single sell (FIFO queue depletion)
- Buy-only positions (unrealized P&L)
- Sell-only orphans (unmatched sells)
- Mixed portfolio with multiple accounts and instruments
- Output shape: one row per trade, loadable into analytics.fact_trades
"""

import uuid

import pytest
import pandas as pd
from decimal import Decimal
from datetime import datetime, timedelta
from unittest.mock import Mock, patch, MagicMock

from etl.transform_pnl import (
    TradePosition, FIFOMatcher, calculate_pnl_fifo, run_pnl_transformation,
    FIFOTradeMatcherException, REQUIRED_COLUMNS
)


class TestTradePosition:
    """Test TradePosition data class."""
    
    def test_trade_position_creation(self):
        """Test creating a trade position."""
        pos = TradePosition(
            trade_id='trade-123',
            side='BUY',
            quantity=Decimal('100'),
            execution_price=Decimal('150.00'),
            executed_at=datetime(2024, 1, 1, 10, 0, 0)
        )
        
        assert pos.trade_id == 'trade-123'
        assert pos.side == 'BUY'
        assert pos.quantity == Decimal('100')
        assert pos.execution_price == Decimal('150.00')
        assert pos.remaining_quantity == Decimal('100')
    
    def test_trade_position_repr(self):
        """Test string representation of trade position."""
        pos = TradePosition(
            trade_id='trade-123',
            side='BUY',
            quantity=Decimal('100'),
            execution_price=Decimal('150.00'),
            executed_at=datetime(2024, 1, 1)
        )
        
        repr_str = repr(pos)
        assert 'BUY' in repr_str
        assert '100' in repr_str
        assert '150.00' in repr_str


class TestFIFOMatcher:
    """Test FIFO matching algorithm."""
    
    def test_simple_buy_sell_match(self):
        """Test simplest case: one buy, one sell."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        # BUY 100 @ 150
        buy_trade = pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 100,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        })
        matcher.process_trade(buy_trade)
        
        # SELL 100 @ 160
        sell_trade = pd.Series({
            'trade_id': 'sell-1',
            'side': 'SELL',
            'quantity': 100,
            'execution_price': 160.00,
            'executed_at': datetime(2024, 1, 2)
        })
        matcher.process_trade(sell_trade)
        matcher.finalize()
        
        # Verify: one record per trade, SELL carries P&L = (160-150) * 100 = +$1000
        assert set(matcher.results) == {'buy-1', 'sell-1'}
        
        sell = matcher.results['sell-1']
        assert sell['realized_pnl'] == Decimal('100') * (Decimal('160') - Decimal('150'))
        assert sell['entry_price'] == Decimal('150')
        assert sell['exit_price'] == Decimal('160')
        assert sell['pnl_status'] == 'MATCHED'
        
        # BUY is fully sold: nothing realized on it, nothing left open
        buy = matcher.results['buy-1']
        assert buy['realized_pnl'] == 0
        assert buy['unrealized_pnl'] == 0
        assert buy['pnl_status'] == 'MATCHED'
    
    def test_multiple_buys_single_sell(self):
        """Test FIFO: multiple buys, one sell that matches across them."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        # BUY 100 @ 150
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 100,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        
        # BUY 50 @ 155
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-2',
            'side': 'BUY',
            'quantity': 50,
            'execution_price': 155.00,
            'executed_at': datetime(2024, 1, 2)
        }))
        
        # SELL 120 @ 160 (should take 100 from buy-1 + 20 from buy-2)
        matcher.process_trade(pd.Series({
            'trade_id': 'sell-1',
            'side': 'SELL',
            'quantity': 120,
            'execution_price': 160.00,
            'executed_at': datetime(2024, 1, 3)
        }))
        matcher.finalize()
        
        # Should have exactly one record per trade
        assert set(matcher.results) == {'buy-1', 'buy-2', 'sell-1'}
        
        # SELL: 100 * (160-150) + 20 * (160-155) = 1000 + 100 = +1100
        sell = matcher.results['sell-1']
        assert sell['realized_pnl'] == Decimal('1100')
        assert sell['pnl_status'] == 'MATCHED'
        
        # Entry price is the weighted average: (100*150 + 20*155) / 120 = 150.8333
        assert sell['entry_price'] == Decimal('150.8333')
        
        # buy-1 fully consumed
        assert matcher.results['buy-1']['pnl_status'] == 'MATCHED'
        assert matcher.results['buy-1']['unrealized_pnl'] == 0
        
        # buy-2 has 30 left open: (155 - 155) * 30 = 0 at the current price
        assert matcher.results['buy-2']['pnl_status'] == 'PARTIAL'
        assert matcher.results['buy-2']['unrealized_pnl'] == 0
    
    def test_unmatched_buy_orphan(self):
        """Test unrealized P&L for unmatched BUY positions."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('160.00')
        )
        
        # BUY 100 @ 150
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 100,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        
        # Don't sell, just finalize
        matcher.finalize()
        
        # Should have 1 open BUY with unrealized P&L = (160 - 150) * 100 = +1000
        assert len(matcher.results) == 1
        buy = matcher.results['buy-1']
        assert buy['unrealized_pnl'] == Decimal('1000')
        assert buy['realized_pnl'] == 0
        assert buy['entry_price'] == Decimal('150')
        assert buy['exit_price'] is None
        assert buy['pnl_status'] == 'UNMATCHED'
    
    def test_unmatched_sell_orphan(self):
        """Test unmatched SELL (no BUY to match against)."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        # SELL 100 @ 160 with no prior BUY
        matcher.process_trade(pd.Series({
            'trade_id': 'sell-1',
            'side': 'SELL',
            'quantity': 100,
            'execution_price': 160.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        matcher.finalize()
        
        # Should have 1 orphan (unmatched sell) with no entry price and no P&L
        assert len(matcher.results) == 1
        sell = matcher.results['sell-1']
        assert sell['pnl_status'] == 'UNMATCHED'
        assert sell['realized_pnl'] == 0
        assert sell['entry_price'] is None
    
    def test_partially_matched_sell(self):
        """Test SELL larger than the open BUY quantity."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        # BUY 40 @ 150
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 40,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        
        # SELL 100 @ 160 (only 40 can be matched)
        matcher.process_trade(pd.Series({
            'trade_id': 'sell-1',
            'side': 'SELL',
            'quantity': 100,
            'execution_price': 160.00,
            'executed_at': datetime(2024, 1, 2)
        }))
        matcher.finalize()
        
        # P&L only on the 40 matched units: (160 - 150) * 40 = +400
        sell = matcher.results['sell-1']
        assert sell['pnl_status'] == 'PARTIAL'
        assert sell['realized_pnl'] == Decimal('400')
        assert matcher.results['buy-1']['pnl_status'] == 'MATCHED'
    
    def test_profitable_and_loss_trades(self):
        """Test both profitable and loss-making trades in same group."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        # BUY 100 @ 150
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 100,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        
        # SELL 50 @ 160 (PROFIT: (160-150) * 50 = +500)
        matcher.process_trade(pd.Series({
            'trade_id': 'sell-1',
            'side': 'SELL',
            'quantity': 50,
            'execution_price': 160.00,
            'executed_at': datetime(2024, 1, 2)
        }))
        
        # SELL 30 @ 140 (LOSS: (140-150) * 30 = -300)
        matcher.process_trade(pd.Series({
            'trade_id': 'sell-2',
            'side': 'SELL',
            'quantity': 30,
            'execution_price': 140.00,
            'executed_at': datetime(2024, 1, 3)
        }))
        matcher.finalize()
        
        # Formula: (exit_price - entry_price) * qty
        assert matcher.results['sell-1']['realized_pnl'] == Decimal('500')
        assert matcher.results['sell-2']['realized_pnl'] == Decimal('-300')
        
        # 20 units of the BUY remain open: (155 - 150) * 20 = +100 unrealized
        assert matcher.results['buy-1']['pnl_status'] == 'PARTIAL'
        assert matcher.results['buy-1']['unrealized_pnl'] == Decimal('100')
    
    def test_missing_current_price(self):
        """Test open BUY when the instrument has no current price."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=None
        )
        
        matcher.process_trade(pd.Series({
            'trade_id': 'buy-1',
            'side': 'BUY',
            'quantity': 100,
            'execution_price': 150.00,
            'executed_at': datetime(2024, 1, 1)
        }))
        matcher.finalize()
        
        # Unrealized P&L is unknown, not a number computed against a price of 0
        assert matcher.results['buy-1']['unrealized_pnl'] is None
        assert matcher.results['buy-1']['pnl_status'] == 'UNMATCHED'
    
    def test_unknown_side_raises(self):
        """Test that a trade with an invalid side is rejected."""
        matcher = FIFOMatcher(
            account_id='acc-1',
            instrument_id='instr-1',
            current_price=Decimal('155.00')
        )
        
        with pytest.raises(FIFOTradeMatcherException):
            matcher.process_trade(pd.Series({
                'trade_id': 'bad-1',
                'side': 'HOLD',
                'quantity': 100,
                'execution_price': 150.00,
                'executed_at': datetime(2024, 1, 1)
            }))


ACC_1 = 'd4000000-0000-0000-0000-000000000001'
ACC_2 = 'd4000000-0000-0000-0000-000000000002'
INSTR_1 = 'b2000000-0000-0000-0000-000000000001'
INSTR_2 = 'b2000000-0000-0000-0000-000000000002'


def make_trades(rows):
    """Build a staging.trades_raw-shaped DataFrame from (trade_id, account, instrument, side, qty, price, day)."""
    return pd.DataFrame([
        {
            'trade_id': trade_id,
            'account_id': account_id,
            'instrument_id': instrument_id,
            'side': side,
            'quantity': quantity,
            'execution_price': price,
            'executed_at': datetime(2024, 1, day),
            'etl_run_id': 'run-1',
            'etl_timestamp': datetime(2024, 2, 1),
            'source_table': 'trades',
        }
        for trade_id, account_id, instrument_id, side, quantity, price, day in rows
    ])


def run_calculate(trades_df, instruments_df):
    """Run calculate_pnl_fifo with both database reads mocked."""
    with patch('etl.transform_pnl.pd.read_sql_table', return_value=trades_df), \
         patch('etl.transform_pnl.pd.read_sql_query', return_value=instruments_df):
        return calculate_pnl_fifo(MagicMock(), MagicMock())


def assert_loadable(pnl_df, trades_df):
    """Assert the output fits analytics.fact_trades: one row per trade, PK unique, NOT NULLs filled."""
    assert len(pnl_df) == len(trades_df)
    assert pnl_df['trade_id'].is_unique
    assert set(pnl_df['trade_id']) == set(trades_df['trade_id'].astype(str))
    for column in REQUIRED_COLUMNS:
        assert pnl_df[column].notna().all(), f"null values in {column}"
    assert set(pnl_df['side']) <= {'BUY', 'SELL'}
    assert set(pnl_df['pnl_status']) <= {'MATCHED', 'PARTIAL', 'UNMATCHED'}


class TestCalculatePnLFifo:
    """Test the main P&L calculation function."""
    
    @pytest.fixture
    def instruments_df(self):
        """Sample analytics.dim_instruments prices."""
        return pd.DataFrame({
            'instrument_id': [INSTR_1, INSTR_2],
            'current_price': [160.00, 50.00]
        })
    
    def test_two_buys_one_sell(self, instruments_df):
        """Test a SELL that spans two BUYs and leaves one partly open."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
            ('t2', ACC_1, INSTR_1, 'BUY', 50, 155.00, 2),
            ('t3', ACC_1, INSTR_1, 'SELL', 120, 160.00, 3),
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        rows = pnl_df.set_index('trade_id')
        
        # SELL: 100 * (160-150) + 20 * (160-155) = +1100
        assert rows.loc['t3', 'realized_pnl'] == Decimal('1100')
        assert rows.loc['t3', 'pnl_status'] == 'MATCHED'
        assert rows.loc['t3', 'side'] == 'SELL'
        assert rows.loc['t3', 'execution_price'] == 160.00
        
        # First BUY fully sold, second has 30 open: (160 - 155) * 30 = +150
        assert rows.loc['t1', 'pnl_status'] == 'MATCHED'
        assert rows.loc['t2', 'pnl_status'] == 'PARTIAL'
        assert rows.loc['t2', 'unrealized_pnl'] == Decimal('150')
        assert rows.loc['t2', 'account_id'] == ACC_1
        assert rows.loc['t2', 'instrument_id'] == INSTR_1
        
        assert pnl_df['realized_pnl'].sum() == Decimal('1100')
    
    def test_buys_only(self, instruments_df):
        """Test input with no SELL trades at all."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
            ('t2', ACC_1, INSTR_1, 'BUY', 10, 170.00, 2),
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        rows = pnl_df.set_index('trade_id')
        assert (pnl_df['pnl_status'] == 'UNMATCHED').all()
        assert rows.loc['t1', 'unrealized_pnl'] == Decimal('1000')   # (160 - 150) * 100
        assert rows.loc['t2', 'unrealized_pnl'] == Decimal('-100')   # (160 - 170) * 10
    
    def test_sells_only(self, instruments_df):
        """Test input with no BUY trades at all."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'SELL', 100, 160.00, 1),
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        assert pnl_df.loc[0, 'pnl_status'] == 'UNMATCHED'
        assert pnl_df.loc[0, 'realized_pnl'] == 0
    
    def test_full_round_trip(self, instruments_df):
        """Test a position that is opened and fully closed."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
            ('t2', ACC_1, INSTR_1, 'SELL', 100, 145.00, 2),
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        rows = pnl_df.set_index('trade_id')
        assert (pnl_df['pnl_status'] == 'MATCHED').all()
        assert rows.loc['t2', 'realized_pnl'] == Decimal('-500')   # (145 - 150) * 100
        assert rows.loc['t1', 'unrealized_pnl'] == 0
    
    def test_accounts_and_instruments_do_not_cross_match(self, instruments_df):
        """Test that a SELL only consumes BUYs of the same account and instrument."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 10, 150.00, 1),
            ('t2', ACC_2, INSTR_1, 'SELL', 10, 160.00, 2),   # other account: nothing to match
            ('t3', ACC_1, INSTR_2, 'SELL', 10, 60.00, 3),    # other instrument: nothing to match
            ('t4', ACC_1, INSTR_1, 'SELL', 10, 155.00, 4),   # matches t1
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        rows = pnl_df.set_index('trade_id')
        assert rows.loc['t2', 'pnl_status'] == 'UNMATCHED'
        assert rows.loc['t3', 'pnl_status'] == 'UNMATCHED'
        assert rows.loc['t4', 'pnl_status'] == 'MATCHED'
        assert rows.loc['t4', 'realized_pnl'] == Decimal('50')   # (155 - 150) * 10
    
    def test_trades_processed_in_executed_at_order(self, instruments_df):
        """Test that FIFO order follows executed_at, not row order in staging."""
        trades_df = make_trades([
            ('t3', ACC_1, INSTR_1, 'SELL', 10, 160.00, 3),
            ('t2', ACC_1, INSTR_1, 'BUY', 10, 155.00, 2),
            ('t1', ACC_1, INSTR_1, 'BUY', 10, 150.00, 1),
        ])
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        rows = pnl_df.set_index('trade_id')
        # SELL consumes the earliest BUY (t1 @ 150), not the first row
        assert rows.loc['t3', 'entry_price'] == Decimal('150')
        assert rows.loc['t3', 'realized_pnl'] == Decimal('100')
        assert rows.loc['t2', 'pnl_status'] == 'UNMATCHED'
    
    def test_uuid_ids_still_find_current_price(self):
        """Test price lookup when UUID columns come back as uuid.UUID objects."""
        trade_id = uuid.uuid4()
        trades_df = make_trades([
            (trade_id, uuid.UUID(ACC_1), uuid.UUID(INSTR_1), 'BUY', 100, 150.00, 1),
        ])
        instruments_df = pd.DataFrame({
            'instrument_id': [uuid.UUID(INSTR_1)],
            'current_price': [Decimal('160.0000')]
        })
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        assert pnl_df.loc[0, 'trade_id'] == str(trade_id)
        assert pnl_df.loc[0, 'unrealized_pnl'] == Decimal('1000')   # (160 - 150) * 100
    
    def test_instrument_without_price(self):
        """Test open BUY for an instrument missing from dim_instruments."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
        ])
        instruments_df = pd.DataFrame({'instrument_id': [], 'current_price': []})
        
        pnl_df = run_calculate(trades_df, instruments_df)
        
        assert_loadable(pnl_df, trades_df)
        assert pd.isna(pnl_df.loc[0, 'unrealized_pnl'])
    
    def test_no_trades_returns_empty(self, instruments_df):
        """Test empty staging table."""
        pnl_df = run_calculate(make_trades([]), instruments_df)
        
        assert len(pnl_df) == 0
    
    def test_duplicate_trade_ids_rejected(self, instruments_df):
        """Test that duplicate trade_id values in staging fail loudly."""
        trades_df = make_trades([
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
            ('t1', ACC_1, INSTR_1, 'BUY', 100, 150.00, 1),
        ])
        
        with pytest.raises(FIFOTradeMatcherException):
            run_calculate(trades_df, instruments_df)


class TestRunPnLTransformation:
    """Test the main transformation entry point."""
    
    @patch('etl.transform_pnl.calculate_pnl_fifo')
    @patch('etl.transform_pnl.get_analytics_engine')
    @patch('etl.transform_pnl.get_staging_engine')
    def test_run_pnl_transformation_success(self, mock_staging, mock_analytics, mock_calc):
        """Test successful P&L transformation run."""
        # Mock the calculate_pnl_fifo to return sample data
        sample_result = pd.DataFrame({
            'trade_id': ['t1', 't2'],
            'account_id': ['acc-1', 'acc-1'],
            'instrument_id': ['instr-1', 'instr-1'],
            'side': ['BUY', 'SELL'],
            'quantity': [150, 100],
            'execution_price': [150.00, 160.00],
            'executed_at': [datetime(2024, 1, 1), datetime(2024, 1, 2)],
            'executed_date': [datetime(2024, 1, 1).date(), datetime(2024, 1, 2).date()],
            'entry_price': [Decimal('150'), Decimal('150')],
            'exit_price': [None, Decimal('160')],
            'realized_pnl': [Decimal('0'), Decimal('1000')],
            'unrealized_pnl': [Decimal('250'), Decimal('0')],
            'pnl_status': ['PARTIAL', 'MATCHED'],
        })
        mock_calc.return_value = sample_result
        
        # Mock database connection
        mock_conn = MagicMock()
        mock_analytics.return_value.begin.return_value.__enter__.return_value = mock_conn
        
        # Run transformation
        with patch.object(pd.DataFrame, 'to_sql') as mock_to_sql:
            result = run_pnl_transformation(etl_run_id='test-run-1')
        
        # Verify result
        assert result['status'] == 'success'
        assert result['trades_processed'] == 2
        assert result['trades_loaded'] == 2
        assert result['matched_trades'] == 1
        assert result['partial_trades'] == 1
        assert result['orphan_trades'] == 0
        assert result['realized_pnl_total'] == Decimal('1000')
        assert result['unrealized_pnl_total'] == Decimal('250')
        
        # Verify load: table is cleared and refilled on the same connection
        executed_sql = [str(call.args[0]) for call in mock_conn.execute.call_args_list]
        assert "DELETE FROM analytics.fact_trades" in executed_sql
        mock_to_sql.assert_called_once()
        assert mock_to_sql.call_args.args[0] == 'fact_trades'
        assert mock_to_sql.call_args.kwargs['con'] is mock_conn
        assert mock_to_sql.call_args.kwargs['if_exists'] == 'append'
    
    @patch('etl.transform_pnl.calculate_pnl_fifo')
    @patch('etl.transform_pnl.get_analytics_engine')
    @patch('etl.transform_pnl.get_staging_engine')
    def test_run_pnl_transformation_empty_trades(self, mock_staging, mock_analytics, mock_calc):
        """Test transformation with no trades."""
        mock_calc.return_value = pd.DataFrame()
        
        result = run_pnl_transformation()
        
        assert result['status'] == 'partial'
        assert result['trades_processed'] == 0
        assert 'error' in result
    
    @patch('etl.transform_pnl.calculate_pnl_fifo')
    @patch('etl.transform_pnl.get_analytics_engine')
    @patch('etl.transform_pnl.get_staging_engine')
    def test_run_pnl_transformation_error(self, mock_staging, mock_analytics, mock_calc):
        """Test transformation with error."""
        mock_calc.side_effect = Exception("Database error")
        
        result = run_pnl_transformation()
        
        assert result['status'] == 'failed'
        assert 'error' in result
        assert 'Database error' in result['error']


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
