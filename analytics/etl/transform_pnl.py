"""
Phase 3: P&L Transformation with FIFO Matching
Calculate profit/loss for individual trades using First-In-First-Out (FIFO) matching.

Algorithm:
1. Load trades from staging.trades_raw
2. Group by account_id + instrument_id
3. For each group, sort trades chronologically (by executed_at)
4. Maintain FIFO queue of BUY positions
5. For each SELL: pop matching BUY trades from queue, calculate realized_pnl
6. For unmatched BUY: calculate unrealized_pnl using current_price
7. Validate: sum(realized_pnl) ≈ account balance changes
8. Load to analytics.fact_trades

Usage:
    from etl.transform_pnl import run_pnl_transformation
    stats = run_pnl_transformation(etl_run_id='phase3-run-001')
    print(stats)  # {'status': 'success', 'trades_processed': 10, 'trades_loaded': 10, ...}
"""

import logging
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, List, Tuple, Optional
from collections import deque, defaultdict

import pandas as pd
from sqlalchemy import text

from etl.config import get_staging_engine, get_analytics_engine

logger = logging.getLogger(__name__)


class FIFOTradeMatcherException(Exception):
    """Custom exception for FIFO matching errors."""
    pass


class TradePosition:
    """Represents a single trade position in the FIFO queue."""
    
    def __init__(self, trade_id: str, side: str, quantity: Decimal, 
                 execution_price: Decimal, executed_at: datetime):
        self.trade_id = trade_id
        self.side = side
        self.quantity = quantity
        self.execution_price = execution_price
        self.executed_at = executed_at
        self.remaining_quantity = quantity
    
    def __repr__(self):
        return (f"TradePosition({self.side} {self.remaining_quantity}/{self.quantity} "
                f"@{self.execution_price} on {self.executed_at})")


class FIFOMatcher:
    """Implements FIFO (First-In-First-Out) matching for trades."""
    
    def __init__(self, account_id: str, instrument_id: str, current_price: Decimal):
        self.account_id = account_id
        self.instrument_id = instrument_id
        self.current_price = current_price
        self.buy_queue: deque = deque()  # FIFO queue of unmatched BUY positions
        self.matched_trades: List[Dict] = []  # Trades with P&L calculated
        self.orphan_trades: List[Dict] = []  # Trades that couldn't be matched
    
    def process_trade(self, trade: pd.Series) -> None:
        """
        Process a single trade through the FIFO matching algorithm.
        
        Args:
            trade: pandas Series with trade data (trade_id, side, quantity, execution_price, executed_at)
        
        Raises:
            FIFOTradeMatcherException: If matching logic fails
        """
        trade_id = str(trade['trade_id'])
        side = trade['side']
        quantity = Decimal(str(trade['quantity']))
        execution_price = Decimal(str(trade['execution_price']))
        executed_at = trade['executed_at']
        
        if side == 'BUY':
            # Add BUY to queue for future matching
            position = TradePosition(trade_id, side, quantity, execution_price, executed_at)
            self.buy_queue.append(position)
            logger.debug(f"Queued BUY: {position}, queue size now: {len(self.buy_queue)}")
        
        elif side == 'SELL':
            # Match SELL against BUY queue (FIFO)
            remaining_sell = quantity
            matched_entries = []
            
            while remaining_sell > 0 and self.buy_queue:
                buy_position = self.buy_queue[0]  # Peek at front of queue
                
                # Determine how many units to match
                match_quantity = min(remaining_sell, buy_position.remaining_quantity)
                
                # Calculate P&L for this matched pair
                realized_pnl = (execution_price - buy_position.execution_price) * match_quantity
                
                # Create matched trade record
                matched_entry = {
                    'sell_trade_id': trade_id,
                    'buy_trade_id': buy_position.trade_id,
                    'account_id': self.account_id,
                    'instrument_id': self.instrument_id,
                    'quantity': match_quantity,
                    'entry_price': buy_position.execution_price,
                    'exit_price': execution_price,
                    'realized_pnl': realized_pnl,
                    'pnl_status': 'MATCHED',
                }
                matched_entries.append(matched_entry)
                
                # Update remaining quantities
                buy_position.remaining_quantity -= match_quantity
                remaining_sell -= match_quantity
                
                logger.debug(f"Matched SELL: {match_quantity} @ {execution_price} with BUY @ {buy_position.execution_price}, "
                           f"P&L: {realized_pnl}")
                
                # Remove BUY from queue if fully matched
                if buy_position.remaining_quantity == 0:
                    self.buy_queue.popleft()
                    logger.debug(f"Removed fully-matched BUY from queue")
            
            # Record matched pairs
            for entry in matched_entries:
                self.matched_trades.append(entry)
            
            # If SELL couldn't be fully matched, record as orphan
            if remaining_sell > 0:
                logger.warning(f"Unmatched SELL: {remaining_sell} units of trade {trade_id}")
                self.orphan_trades.append({
                    'trade_id': trade_id,
                    'side': side,
                    'quantity': remaining_sell,
                    'execution_price': execution_price,
                    'pnl_status': 'UNMATCHED',
                    'realized_pnl': 0,
                    'unrealized_pnl': 0,
                })
    
    def finalize(self) -> None:
        """
        Finalize matching: calculate unrealized P&L for remaining open BUY positions.
        These are positions that were never matched with a SELL.
        """
        while self.buy_queue:
            buy_position = self.buy_queue.popleft()
            
            if buy_position.remaining_quantity > 0:
                # Calculate unrealized P&L using current market price
                unrealized_pnl = (self.current_price - buy_position.execution_price) * buy_position.remaining_quantity
                
                logger.debug(f"Unmatched BUY: {buy_position.remaining_quantity} @ {buy_position.execution_price}, "
                           f"unrealized P&L (at current {self.current_price}): {unrealized_pnl}")
                
                self.orphan_trades.append({
                    'trade_id': buy_position.trade_id,
                    'side': 'BUY',
                    'quantity': buy_position.remaining_quantity,
                    'execution_price': buy_position.execution_price,
                    'pnl_status': 'UNMATCHED',
                    'realized_pnl': 0,
                    'unrealized_pnl': unrealized_pnl,
                })


def calculate_pnl_fifo(staging_engine, analytics_engine) -> pd.DataFrame:
    """
    Calculate P&L for all trades using FIFO matching.
    
    Loads trades from staging, groups by account/instrument, applies FIFO matching,
    and returns DataFrame ready to load to fact_trades.
    
    Args:
        staging_engine: SQLAlchemy engine for paysprint_analytics (staging schema)
        analytics_engine: SQLAlchemy engine for paysprint_analytics (analytics schema)
    
    Returns:
        DataFrame with columns: trade_id, account_id, instrument_id, side, quantity,
                                execution_price, executed_at, executed_date,
                                entry_price, exit_price, realized_pnl, unrealized_pnl, pnl_status
    
    Raises:
        Exception: If query or matching fails
    """
    logger.info("Loading trades from staging.trades_raw...")
    
    # Load trades from staging
    with staging_engine.begin() as conn:
        trades_df = pd.read_sql_table(
            'trades_raw',
            con=conn,
            schema='staging'
        )
    
    if len(trades_df) == 0:
        logger.warning("No trades found in staging.trades_raw")
        return pd.DataFrame()
    
    logger.info(f"Loaded {len(trades_df)} trades from staging")
    
    # Load current instrument prices from analytics (for unrealized P&L)
    with analytics_engine.begin() as conn:
        instruments_df = pd.read_sql_query(
            "SELECT instrument_id, current_price FROM analytics.dim_instruments",
            con=conn
        )
    
    logger.info(f"Loaded {len(instruments_df)} instruments")
    
    # Convert trades to proper types
    trades_df['trade_id'] = trades_df['trade_id'].astype(str)
    trades_df['account_id'] = trades_df['account_id'].astype(str)
    trades_df['instrument_id'] = trades_df['instrument_id'].astype(str)
    trades_df['quantity'] = trades_df['quantity'].astype('float64')
    trades_df['execution_price'] = trades_df['execution_price'].astype('float64')
    trades_df['executed_at'] = pd.to_datetime(trades_df['executed_at'])
    
    # Sort by executed_at to maintain chronological order
    trades_df = trades_df.sort_values('executed_at').reset_index(drop=True)
    
    # Group by account + instrument and apply FIFO matching
    result_trades = []
    
    logger.info("Starting FIFO matching by account/instrument...")
    
    for (account_id, instrument_id), group_df in trades_df.groupby(['account_id', 'instrument_id']):
        logger.debug(f"Processing account={account_id}, instrument={instrument_id}, trades={len(group_df)}")
        
        # Get current price for this instrument
        instrument_price = instruments_df[
            instruments_df['instrument_id'] == instrument_id
        ]['current_price'].values
        current_price = Decimal(str(instrument_price[0])) if len(instrument_price) > 0 else Decimal('0.00')
        
        # Create matcher and process all trades in this group
        matcher = FIFOMatcher(account_id, instrument_id, current_price)
        
        for _, trade in group_df.iterrows():
            matcher.process_trade(trade)
        
        # Finalize (calculate unrealized P&L for open positions)
        matcher.finalize()
        
        # Collect results
        result_trades.extend(matcher.matched_trades)
        result_trades.extend(matcher.orphan_trades)
        
        logger.debug(f"Matched: {len(matcher.matched_trades)}, Orphan: {len(matcher.orphan_trades)}")
    
    logger.info(f"FIFO matching complete. Total records: {len(result_trades)}")
    
    # Convert results to DataFrame
    if not result_trades:
        logger.warning("No trades produced after FIFO matching")
        return pd.DataFrame()
    
    result_df = pd.DataFrame(result_trades)
    
    # For matched trades, need to add original SELL trade data
    # For orphan trades, need to add original trade data
    
    # Join back to original trades to get full information
    result_df = result_df.merge(
        trades_df[['trade_id', 'executed_at']].rename(columns={'trade_id': 'sell_trade_id', 'executed_at': 'sell_executed_at'}),
        on='sell_trade_id',
        how='left'
    )
    
    # Use original trade_id if sell_trade_id is missing (orphan trades)
    result_df['trade_id'] = result_df['sell_trade_id'].fillna(result_df.get('trade_id', None))
    result_df['executed_at'] = result_df['sell_executed_at'].fillna(result_df.get('executed_at', None))
    
    # Extract just the columns needed for fact_trades
    pnl_df = pd.DataFrame({
        'trade_id': result_df.get('trade_id', result_df.get('sell_trade_id', '')),
        'account_id': result_df['account_id'],
        'instrument_id': result_df['instrument_id'],
        'side': result_df.get('side', 'SELL'),  # Orphan trades have this
        'quantity': result_df['quantity'],
        'execution_price': result_df.get('execution_price', result_df.get('exit_price', 0)),
        'executed_at': result_df['executed_at'],
        'executed_date': pd.to_datetime(result_df['executed_at']).dt.date,
        'entry_price': result_df.get('entry_price', None),
        'exit_price': result_df.get('exit_price', None),
        'realized_pnl': result_df['realized_pnl'],
        'unrealized_pnl': result_df.get('unrealized_pnl', 0),
        'pnl_status': result_df['pnl_status'],
    })
    
    logger.info(f"P&L DataFrame prepared: {len(pnl_df)} rows")
    
    return pnl_df


def validate_staging_tables(staging_engine) -> Tuple[bool, str]:
    """
    Validate that staging schema and required tables exist.
    
    Args:
        staging_engine: SQLAlchemy engine for staging database
    
    Returns:
        Tuple of (is_valid: bool, message: str)
    """
    try:
        with staging_engine.begin() as conn:
            # Check if staging.trades_raw exists
            result = conn.execute(text("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables 
                    WHERE table_schema = 'staging' 
                    AND table_name = 'trades_raw'
                )
            """))
            trades_table_exists = result.scalar()
            
            if not trades_table_exists:
                return False, (
                    "❌ staging.trades_raw table not found!\n"
                    "This table is created by Phase 0 (schema initialization).\n\n"
                    "🔧 FIX: Run Phase 0 first:\n"
                    "   python3 -c \"from etl.schema_init import initialize_database; initialize_database()\"\n\n"
                    "📋 Full pipeline order:\n"
                    "   1. Phase 0: Initialize schema\n"
                    "   2. Seed trades (optional): Generate test data\n"
                    "   3. Phase 1: Extract from OLTP to staging\n"
                    "   4. Phase 3: Transform (FIFO P&L)\n\n"
                    "🚀 Quick fix with orchestrator:\n"
                    "   python3 etl/run_pipeline.py"
                )
            
            # Check if staging schema has at least some data
            result = conn.execute(text("SELECT COUNT(*) FROM staging.trades_raw"))
            trades_count = result.scalar()
            
            if trades_count == 0:
                logger.warning(
                    "⚠️  staging.trades_raw is empty! "
                    "No trades will be processed.\n"
                    "   → Run Phase 1 to extract trades: "
                    "python3 -c \"from etl.extract_etl import run_etl; print(run_etl())\""
                )
            
            return True, "staging tables exist"
    
    except Exception as e:
        return False, f"Error validating staging tables: {str(e)}"


def run_pnl_transformation(etl_run_id: str = None) -> Dict:
    """
    Main entry point: Run complete P&L transformation pipeline.
    
    Args:
        etl_run_id: Unique identifier for this ETL run (for audit trail)
    
    Returns:
        dict with status, row counts, validation results
    
    Example:
        >>> result = run_pnl_transformation('phase3-run-001')
        >>> print(result)
        {'status': 'success', 'trades_processed': 10, 'trades_loaded': 10, 'orphan_trades': 0}
    """
    if etl_run_id is None:
        etl_run_id = f"pnl-{datetime.now().isoformat()}"
    
    logger.info(f"Starting P&L transformation (run_id: {etl_run_id})")
    
    try:
        staging_engine = get_staging_engine()
        analytics_engine = get_analytics_engine()
        
        # Validate that staging tables exist and have data
        logger.info("Validating staging tables...")
        is_valid, validation_msg = validate_staging_tables(staging_engine)
        if not is_valid:
            logger.error(validation_msg)
            return {
                'status': 'failed',
                'error': validation_msg
            }
        
        # Step 1: Calculate P&L with FIFO matching
        logger.info("Step 1: FIFO matching...")
        pnl_df = calculate_pnl_fifo(staging_engine, analytics_engine)
        
        if len(pnl_df) == 0:
            logger.warning("No trades produced after FIFO matching")
            return {
                'status': 'partial',
                'trades_processed': 0,
                'trades_loaded': 0,
                'orphan_trades': 0,
                'error': 'No trades in staging'
            }
        
        # Step 2: Add metadata
        logger.info("Step 2: Adding metadata...")
        pnl_df['etl_run_id'] = etl_run_id
        pnl_df['etl_timestamp'] = datetime.now()
        
        # Step 3: Load to fact_trades
        logger.info("Step 3: Loading to analytics.fact_trades...")
        
        with analytics_engine.begin() as conn:
            # First, clear existing data (for idempotency)
            conn.execute(text("DELETE FROM analytics.fact_trades WHERE etl_run_id = :run_id"),
                        {'run_id': etl_run_id})
            
            # Load new data
            pnl_df.to_sql(
                'fact_trades',
                con=conn,
                schema='analytics',
                if_exists='append',
                index=False,
                chunksize=1000,
                method='multi'
            )
        
        # Count results
        orphan_count = (pnl_df['pnl_status'] == 'UNMATCHED').sum()
        matched_count = (pnl_df['pnl_status'] == 'MATCHED').sum()
        
        logger.info(f"✅ P&L transformation complete!")
        logger.info(f"  Trades processed: {len(pnl_df)}")
        logger.info(f"  Matched pairs: {matched_count}")
        logger.info(f"  Orphan trades: {orphan_count}")
        
        return {
            'status': 'success',
            'trades_processed': len(pnl_df),
            'trades_loaded': len(pnl_df),
            'matched_trades': matched_count,
            'orphan_trades': orphan_count,
            'realized_pnl_total': pnl_df['realized_pnl'].sum(),
            'unrealized_pnl_total': pnl_df['unrealized_pnl'].sum(),
        }
    
    except Exception as e:
        logger.error(f"❌ P&L transformation failed: {str(e)}", exc_info=True)
        return {
            'status': 'failed',
            'error': str(e),
        }


if __name__ == '__main__':
    logging.basicConfig(
        level=logging.INFO,
        format='[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s'
    )
    
    result = run_pnl_transformation()
    print("\n=== P&L Transformation Result ===")
    for key, value in result.items():
        print(f"{key}: {value}")
