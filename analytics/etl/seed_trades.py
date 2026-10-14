"""
Seed Trade Data Script
Generates realistic trade data for testing the P&L pipeline.

Accounts:
  - Alice    (d4000000-0000-0000-0000-000000000001)
  - Bob      (d4000000-0000-0000-0000-000000000002)
  - Charlie  (d4000000-0000-0000-0000-000000000003)

Instruments:
  - AAPL     (b2000000-0000-0000-0000-000000000001)
  - GOOGL    (b2000000-0000-0000-0000-000000000002)
  - MSFT     (b2000000-0000-0000-0000-000000000003)
  - TSLA     (b2000000-0000-0000-0000-000000000004)
  - AMZN     (b2000000-0000-0000-0000-000000000005)
  - NVDA     (b2000000-0000-0000-0000-000000000006)
  - META     (b2000000-0000-0000-0000-000000000007)
"""

import uuid
from datetime import datetime, timedelta, timezone
import json
from decimal import Decimal
from sqlalchemy import text
from etl.config import get_staging_engine, get_oltp_engine


def generate_trade_data(num_trades: int = 50, start_date: str = "2025-08-25") -> list:
    """
    Generate realistic trade data for testing.
    
    Args:
        num_trades: Number of trades to generate
        start_date: Start date for trades (YYYY-MM-DD)
        
    Returns:
        List of trade dictionaries
    """
    # Fixed UUIDs for accounts and instruments
    accounts = [
        'd4000000-0000-0000-0000-000000000001',  # Alice
        'd4000000-0000-0000-0000-000000000002',  # Bob
        'd4000000-0000-0000-0000-000000000003',  # Charlie
    ]
    
    instruments = [
        ('b2000000-0000-0000-0000-000000000001', 'AAPL', 220.00, 235.00),
        ('b2000000-0000-0000-0000-000000000002', 'GOOGL', 170.00, 180.00),
        ('b2000000-0000-0000-0000-000000000003', 'MSFT', 420.00, 440.00),
        ('b2000000-0000-0000-0000-000000000004', 'TSLA', 240.00, 270.00),
        ('b2000000-0000-0000-0000-000000000005', 'AMZN', 185.00, 200.00),
        ('b2000000-0000-0000-0000-000000000006', 'NVDA', 130.00, 145.00),
        ('b2000000-0000-0000-0000-000000000007', 'META', 560.00, 590.00),
    ]
    
    trades = []
    start = datetime.strptime(start_date, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    
    for i in range(num_trades):
        account = accounts[i % len(accounts)]
        instrument_id, ticker, price_low, price_high = instruments[i % len(instruments)]
        
        # Alternate between BUY and SELL
        side = 'BUY' if (i % 2 == 0) else 'SELL'
        
        # Generate realistic quantities (whole shares, 1-100)
        quantity = 1 + (i * 7) % 100
        
        # Generate prices with some variance
        base_price = price_low + (price_high - price_low) * ((i % 10) / 10.0)
        execution_price = round(base_price + (i % 5 - 2) * 0.5, 4)
        
        # Spread trades over days (4 trades per day)
        days_offset = i // 4
        trade_time = start + timedelta(days=days_offset, hours=9 + (i % 4) * 2, minutes=(i % 60))
        
        trade = {
            'trade_id': str(uuid.uuid4()),
            'account_id': account,
            'instrument_id': instrument_id,
            'side': side,
            'quantity': str(quantity),
            'execution_price': str(execution_price),
            'executed_at': trade_time.isoformat(),
            'ticker': ticker,
            'account_name': ['Alice', 'Bob', 'Charlie'][accounts.index(account)],
        }
        
        trades.append(trade)
    
    return trades


def insert_trades_to_oltp(trades: list) -> dict:
    """
    Insert trades into the OLTP database.
    
    Args:
        trades: List of trade dictionaries
        
    Returns:
        Dictionary with insertion statistics
    """
    engine = get_oltp_engine()
    results = {
        'total_trades': len(trades),
        'inserted': 0,
        'failed': 0,
        'errors': [],
        'trades_by_account': {},
        'trades_by_side': {'BUY': 0, 'SELL': 0},
    }
    
    with engine.connect() as conn:
        for trade in trades:
            try:
                account_name = trade.pop('account_name', 'Unknown')
                ticker = trade.pop('ticker', 'Unknown')
                
                # Insert into trades table
                insert_sql = text("""
                    INSERT INTO trades (
                        trade_id, account_id, instrument_id, side, 
                        quantity, execution_price, executed_at
                    ) VALUES (
                        :trade_id, :account_id, :instrument_id, :side,
                        :quantity, :execution_price, :executed_at
                    )
                """)
                
                conn.execute(insert_sql, trade)
                results['inserted'] += 1
                
                # Track by account
                if account_name not in results['trades_by_account']:
                    results['trades_by_account'][account_name] = 0
                results['trades_by_account'][account_name] += 1
                
                # Track by side
                results['trades_by_side'][trade['side']] += 1
                
                print(f"✓ {trade['side']:4} {trade['quantity']:>3} shares of {ticker} @ ${float(trade['execution_price']):>7.2f} | {account_name}")
                
            except Exception as e:
                results['failed'] += 1
                error_msg = f"Failed to insert trade {trade.get('trade_id', 'unknown')}: {str(e)}"
                results['errors'].append(error_msg)
                print(f"✗ {error_msg}")
        
        conn.commit()
    
    return results


def seed_trades_main(num_trades: int = 50, start_date: str = "2025-08-25"):
    """
    Main entry point for seeding trades.
    
    Args:
        num_trades: Number of trades to generate
        start_date: Start date for trades (YYYY-MM-DD)
    """
    print("\n" + "="*80)
    print("TRADE DATA SEEDER")
    print("="*80)
    print(f"\nGenerating {num_trades} trade records starting from {start_date}...\n")
    
    # Generate trades
    trades = generate_trade_data(num_trades=num_trades, start_date=start_date)
    
    print(f"Generated {len(trades)} trades. Inserting into OLTP database...\n")
    
    # Insert into OLTP
    results = insert_trades_to_oltp(trades)
    
    # Print summary
    print("\n" + "="*80)
    print("INSERTION SUMMARY")
    print("="*80)
    print(f"Total trades generated:  {results['total_trades']}")
    print(f"Successfully inserted:   {results['inserted']}")
    print(f"Failed:                  {results['failed']}")
    
    if results['trades_by_account']:
        print(f"\nTrades by Account:")
        for account, count in sorted(results['trades_by_account'].items()):
            print(f"  {account:12} {count:>3} trades")
    
    print(f"\nTrades by Side:")
    for side, count in sorted(results['trades_by_side'].items()):
        print(f"  {side:12} {count:>3} trades")
    
    if results['errors']:
        print(f"\nErrors ({len(results['errors'])}):")
        for error in results['errors'][:5]:  # Show first 5 errors
            print(f"  - {error}")
    
    print("\n" + "="*80)
    print("✅ NEXT STEPS:")
    print("="*80)
    print("1. Run Phase 1 (Extraction):")
    print("   python3 -c \"from etl.extract_etl import run_etl; print(run_etl())\"")
    print("\n2. Run Phase 3 (P&L Transformation):")
    print("   python3 -c \"from etl.transform_pnl import run_pnl_transformation; print(run_pnl_transformation('prod-run-with-trades'))\"")
    print("\n3. Verify P&L data in analytics database:")
    print("   SELECT account_id, instrument_id, side, quantity, realized_pnl, unrealized_pnl FROM analytics.fact_trades;")
    print("="*80 + "\n")
    
    return results


if __name__ == "__main__":
    import sys
    
    # Parse command line arguments
    num_trades = 50
    start_date = "2025-08-25"
    
    if len(sys.argv) > 1:
        try:
            num_trades = int(sys.argv[1])
        except ValueError:
            print(f"Invalid number of trades: {sys.argv[1]}")
            sys.exit(1)
    
    if len(sys.argv) > 2:
        start_date = sys.argv[2]
    
    seed_trades_main(num_trades=num_trades, start_date=start_date)
