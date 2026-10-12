"""
Phase 3: P&L Transformation with FIFO Matching
Calculate profit/loss for individual trades using First-In-First-Out (FIFO) matching.

Algorithm:
1. Load trades from staging.trades_raw
2. Group by account_id + instrument_id
3. For each group, sort trades chronologically (by executed_at)
4. Maintain FIFO queue of BUY positions
5. For each SELL: consume BUY quantity from the front of the queue, calculate realized_pnl
6. For each BUY with quantity still open: calculate unrealized_pnl using current_price
7. Validate: one row per trade, unique trade_id, required columns filled
8. Replace the contents of analytics.fact_trades

Output is one row per trade:
- BUY:  entry_price = own price, realized_pnl = 0,
        unrealized_pnl = (current_price - own price) * quantity not yet sold
- SELL: exit_price = own price, entry_price = weighted average of the BUYs consumed,
        realized_pnl = sum of (own price - BUY price) * matched quantity, unrealized_pnl = 0
- pnl_status: MATCHED (fully paired), PARTIAL (partly paired), UNMATCHED (not paired)

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

# P&L columns the matcher adds to each trade
PNL_COLUMNS = ['entry_price', 'exit_price', 'realized_pnl', 'unrealized_pnl', 'pnl_status']

# Columns that must be filled on every row before loading to fact_trades
REQUIRED_COLUMNS = [
    'trade_id', 'account_id', 'instrument_id', 'side', 'quantity',
    'execution_price', 'executed_at', 'executed_date', 'realized_pnl', 'pnl_status'
]

# fact_trades stores prices and P&L as NUMERIC(18, 4)
MONEY_PLACES = Decimal('0.0001')


class FIFOTradeMatcherException(Exception):
    """Custom exception for FIFO matching errors."""
    pass


def _round_money(value: Decimal) -> Decimal:
    """Round to the 4 decimal places fact_trades stores."""
    return value.quantize(MONEY_PLACES, rounding=ROUND_HALF_UP)


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
    
    def __init__(self, account_id: str, instrument_id: str, current_price: Optional[Decimal]):
        self.account_id = account_id
        self.instrument_id = instrument_id
        self.current_price = current_price  # None if the instrument has no known price
        self.buy_queue: deque = deque()  # FIFO queue of BUY positions with quantity still open
        self.buy_positions: List[TradePosition] = []  # Every BUY seen, for finalize()
        self.results: Dict[str, Dict] = {}  # One P&L record per trade, keyed by trade_id
    
    def process_trade(self, trade: pd.Series) -> None:
        """
        Process a single trade through the FIFO matching algorithm.
        
        Args:
            trade: pandas Series with trade data (trade_id, side, quantity, execution_price, executed_at)
        
        Raises:
            FIFOTradeMatcherException: If the trade has an unknown side
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
            self.buy_positions.append(position)
            logger.debug(f"Queued BUY: {position}, queue size now: {len(self.buy_queue)}")
            
            # unrealized_pnl and pnl_status depend on later SELLs, so finalize() sets them
            self.results[trade_id] = {
                'trade_id': trade_id,
                'entry_price': _round_money(execution_price),
                'exit_price': None,
                'realized_pnl': Decimal('0'),
                'unrealized_pnl': None,
                'pnl_status': None,
            }
        
        elif side == 'SELL':
            # Match SELL against BUY queue (FIFO)
            remaining_sell = quantity
            matched_quantity = Decimal('0')
            matched_cost = Decimal('0')
            realized_pnl = Decimal('0')
            
            while remaining_sell > 0 and self.buy_queue:
                buy_position = self.buy_queue[0]  # Peek at front of queue
                
                # Determine how many units to match
                match_quantity = min(remaining_sell, buy_position.remaining_quantity)
                
                # Calculate P&L for this matched lot
                lot_pnl = (execution_price - buy_position.execution_price) * match_quantity
                realized_pnl += lot_pnl
                matched_quantity += match_quantity
                matched_cost += buy_position.execution_price * match_quantity
                
                # Update remaining quantities
                buy_position.remaining_quantity -= match_quantity
                remaining_sell -= match_quantity
                
                logger.debug(f"Matched SELL: {match_quantity} @ {execution_price} with BUY @ {buy_position.execution_price}, "
                           f"P&L: {lot_pnl}")
                
                # Remove BUY from queue if fully matched
                if buy_position.remaining_quantity == 0:
                    self.buy_queue.popleft()
                    logger.debug(f"Removed fully-matched BUY from queue")
            
            if remaining_sell == 0:
                pnl_status = 'MATCHED'
            elif matched_quantity > 0:
                pnl_status = 'PARTIAL'
            else:
                pnl_status = 'UNMATCHED'
            
            if remaining_sell > 0:
                logger.warning(f"Unmatched SELL: {remaining_sell} units of trade {trade_id}")
            
            self.results[trade_id] = {
                'trade_id': trade_id,
                # Quantity-weighted average price of the BUYs this SELL consumed
                'entry_price': _round_money(matched_cost / matched_quantity) if matched_quantity > 0 else None,
                'exit_price': _round_money(execution_price),
                'realized_pnl': _round_money(realized_pnl),
                'unrealized_pnl': Decimal('0'),
                'pnl_status': pnl_status,
            }
        
        else:
            raise FIFOTradeMatcherException(f"Trade {trade_id} has unknown side: {side!r}")
    
    def finalize(self) -> None:
        """
        Finalize matching: set unrealized P&L and status for every BUY,
        based on the quantity no SELL has consumed.
        """
        for buy_position in self.buy_positions:
            remaining = buy_position.remaining_quantity
            
            if remaining == 0:
                pnl_status = 'MATCHED'
                unrealized_pnl = Decimal('0')
            else:
                pnl_status = 'UNMATCHED' if remaining == buy_position.quantity else 'PARTIAL'
                
                if self.current_price is None:
                    # No market price to value the open quantity against
                    unrealized_pnl = None
                else:
                    unrealized_pnl = _round_money(
                        (self.current_price - buy_position.execution_price) * remaining
                    )
                
                logger.debug(f"Open BUY: {remaining} @ {buy_position.execution_price}, "
                           f"unrealized P&L (at current {self.current_price}): {unrealized_pnl}")
            
            record = self.results[buy_position.trade_id]
            record['unrealized_pnl'] = unrealized_pnl
            record['pnl_status'] = pnl_status
        
        self.buy_queue.clear()


def _validate_pnl_output(pnl_df: pd.DataFrame, expected_rows: int) -> None:
    """
    Check the P&L DataFrame is loadable into analytics.fact_trades.
    
    Raises:
        FIFOTradeMatcherException: If the row count, trade_id uniqueness or
                                   a required column is wrong
    """
    problems = []
    
    if len(pnl_df) != expected_rows:
        problems.append(f"expected {expected_rows} rows (one per trade), got {len(pnl_df)}")
    
    duplicate_ids = int(pnl_df['trade_id'].duplicated().sum())
    if duplicate_ids:
        problems.append(f"{duplicate_ids} duplicate trade_id values")
    
    for column in REQUIRED_COLUMNS:
        null_count = int(pnl_df[column].isna().sum())
        if null_count:
            problems.append(f"{null_count} null values in {column}")
    
    if problems:
        raise FIFOTradeMatcherException("P&L output failed validation: " + "; ".join(problems))


def calculate_pnl_fifo(staging_engine, analytics_engine) -> pd.DataFrame:
    """
    Calculate P&L for all trades using FIFO matching.
    
    Loads trades from staging, groups by account/instrument, applies FIFO matching,
    and returns DataFrame ready to load to fact_trades (one row per trade).
    
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
    
    # IDs can arrive as uuid.UUID or str depending on column type; compare as str
    trades_df['trade_id'] = trades_df['trade_id'].astype(str)
    trades_df['account_id'] = trades_df['account_id'].astype(str)
    trades_df['instrument_id'] = trades_df['instrument_id'].astype(str)
    trades_df['executed_at'] = pd.to_datetime(trades_df['executed_at'])
    
    if trades_df['trade_id'].duplicated().any():
        raise FIFOTradeMatcherException("staging.trades_raw contains duplicate trade_id values")
    
    current_prices = {
        str(instrument_id): Decimal(str(price))
        for instrument_id, price in zip(instruments_df['instrument_id'], instruments_df['current_price'])
        if pd.notna(price)
    }
    
    # Sort chronologically; trade_id breaks ties so runs are deterministic
    trades_df = trades_df.sort_values(['executed_at', 'trade_id']).reset_index(drop=True)
    
    # Group by account + instrument and apply FIFO matching
    pnl_records = []
    
    logger.info("Starting FIFO matching by account/instrument...")
    
    for (account_id, instrument_id), group_df in trades_df.groupby(['account_id', 'instrument_id']):
        logger.debug(f"Processing account={account_id}, instrument={instrument_id}, trades={len(group_df)}")
        
        # Get current price for this instrument
        current_price = current_prices.get(instrument_id)
        if current_price is None:
            logger.warning(f"No current price for instrument {instrument_id}; "
                         f"unrealized P&L will be NULL for its open positions")
        
        # Create matcher and process all trades in this group
        matcher = FIFOMatcher(account_id, instrument_id, current_price)
        
        for _, trade in group_df.iterrows():
            matcher.process_trade(trade)
        
        # Finalize (calculate unrealized P&L for open positions)
        matcher.finalize()
        
        # Collect results
        pnl_records.extend(matcher.results.values())
    
    logger.info(f"FIFO matching complete. Total records: {len(pnl_records)}")
    
    # Attach P&L to the source trades, so every trade keeps its own columns
    pnl_df = trades_df[[
        'trade_id', 'account_id', 'instrument_id', 'side',
        'quantity', 'execution_price', 'executed_at'
    ]].copy()
    pnl_df['executed_date'] = pnl_df['executed_at'].dt.date
    pnl_df = pnl_df.merge(
        pd.DataFrame(pnl_records, columns=['trade_id'] + PNL_COLUMNS),
        on='trade_id',
        how='left'
    )
    
    _validate_pnl_output(pnl_df, expected_rows=len(trades_df))
    
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
            # Every run recalculates all trades, so replace the table contents
            # (same transaction as the insert, for idempotency)
            conn.execute(text("DELETE FROM analytics.fact_trades"))

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
        orphan_count = int((pnl_df['pnl_status'] == 'UNMATCHED').sum())
        partial_count = int((pnl_df['pnl_status'] == 'PARTIAL').sum())
        matched_count = int((pnl_df['pnl_status'] == 'MATCHED').sum())

        logger.info(f"✅ P&L transformation complete!")
        logger.info(f"  Trades processed: {len(pnl_df)}")
        logger.info(f"  Matched trades: {matched_count}")
        logger.info(f"  Partial trades: {partial_count}")
        logger.info(f"  Orphan trades: {orphan_count}")

        return {
            'status': 'success',
            'trades_processed': len(pnl_df),
            'trades_loaded': len(pnl_df),
            'matched_trades': matched_count,
            'partial_trades': partial_count,
            'orphan_trades': orphan_count,
            'realized_pnl_total': sum(pnl_df['realized_pnl'].dropna(), Decimal('0')),
            'unrealized_pnl_total': sum(pnl_df['unrealized_pnl'].dropna(), Decimal('0')),
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
