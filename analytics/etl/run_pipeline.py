"""
Complete ETL Pipeline Orchestrator

Runs all phases in correct order:
  Phase 0: Schema initialization
  Phase 1: Data extraction
  Phase 2: Analytics schema + dimensions
  Phase 3: P&L transformation

Usage:
  python3 run_pipeline.py                          # Run all phases on the existing OLTP data
  python3 run_pipeline.py --json                   # Same, and print the result as JSON on stdout
  python3 run_pipeline.py --seed                   # Insert 50 test trades into OLTP first
  python3 run_pipeline.py --seed --trades 100      # Custom number of test trades
  python3 run_pipeline.py --phase 1                # Run only Phase 1
  python3 run_pipeline.py --phase 3                # Run only Phase 3 (assumes Phase 0+1+2 complete)

From Python (e.g. an API endpoint behind a "refresh analytics" button):
  from etl.run_pipeline import run_pipeline
  result = run_pipeline()   # {'status': 'success'|'failed'|'busy', 'phases': [...], 'summary': {...}}

Exit codes: 0 = success, 1 = failed, 2 = busy (another run holds the lock)
"""

import sys
import os
import argparse
import json
import logging
import time
from contextlib import contextmanager
from datetime import datetime, timedelta
from typing import Dict, Any, Optional

# Add parent directory to path so we can import etl module
# This allows running as: python3 etl/run_pipeline.py
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | %(levelname)-8s | %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# OLTP tables Phase 1 extracts; the Spring app creates and seeds them on startup
OLTP_TABLES = ['exchanges', 'accounts', 'instruments', 'holdings', 'trades']

# Postgres advisory lock key: one pipeline run at a time per analytics database
PIPELINE_LOCK_KEY = 7283001

EXIT_CODES = {'success': 0, 'busy': 2}  # anything else exits 1


@contextmanager
def pipeline_lock():
    """
    Hold a Postgres advisory lock on the analytics database for the whole run.
    
    Phase 2 drops and recreates the analytics tables, so two runs at once
    (e.g. two clicks on a refresh button) would break each other.
    
    Yields:
        bool: True if this run holds the lock, False if another run already does
    
    Raises:
        RuntimeError: If the analytics database can't be reached or created
    """
    from sqlalchemy import text
    from etl.config import get_analytics_engine
    from etl.schema_init import ensure_analytics_database
    
    # The lock lives in the analytics database, so it has to exist first
    if not ensure_analytics_database():
        raise RuntimeError("Analytics database is not reachable (see errors above)")
    
    engine = get_analytics_engine()
    conn = engine.connect()
    try:
        acquired = conn.execute(
            text("SELECT pg_try_advisory_lock(:key)"), {"key": PIPELINE_LOCK_KEY}
        ).scalar()
        conn.commit()  # Session-level lock: survives the commit, released on unlock or disconnect
        try:
            yield acquired
        finally:
            if acquired:
                conn.execute(text("SELECT pg_advisory_unlock(:key)"), {"key": PIPELINE_LOCK_KEY})
                conn.commit()
    finally:
        conn.close()
        engine.dispose()


def wait_for_oltp() -> None:
    """
    Wait until the OLTP database has the tables Phase 1 reads.
    
    The app container creates them a few seconds after `docker compose up`,
    so retry (MAX_RETRIES x RETRY_DELAY_SECONDS) before giving up.
    
    Raises:
        RuntimeError: If the tables are still missing after the last retry
    """
    from sqlalchemy import text
    from etl.config import get_oltp_engine, MAX_RETRIES, RETRY_DELAY_SECONDS
    
    engine = get_oltp_engine()
    problem = None
    try:
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                with engine.connect() as conn:
                    missing = [
                        table for table in OLTP_TABLES
                        if conn.execute(
                            text("SELECT to_regclass(:name)"), {"name": f"public.{table}"}
                        ).scalar() is None
                    ]
                    if missing:
                        problem = f"missing tables: {', '.join(missing)}"
                    elif conn.execute(text("SELECT COUNT(*) FROM public.instruments")).scalar() == 0:
                        problem = "public.instruments is empty"
                    else:
                        return
            except Exception as e:
                problem = str(e).splitlines()[0]
            
            if attempt < MAX_RETRIES:
                logger.info(f"OLTP not ready ({problem}); "
                            f"check {attempt}/{MAX_RETRIES}, retrying in {RETRY_DELAY_SECONDS}s...")
                time.sleep(RETRY_DELAY_SECONDS)
    finally:
        engine.dispose()
    
    raise RuntimeError(
        f"OLTP tables not found in paysprint ({problem}) - "
        f"is the app container up? (docker compose ps)"
    )


def run_phase_0() -> Dict[str, Any]:
    """
    Phase 0: Initialize staging schema and tables.
    
    Creates:
      - analytics database (if missing)
      - staging schema
      - 5 raw tables (exchanges, accounts, instruments, holdings, trades)
    """
    logger.info("="*80)
    logger.info("PHASE 0: SCHEMA INITIALIZATION")
    logger.info("="*80)
    
    try:
        from etl.schema_init import initialize_database
        
        logger.info("Creating staging schema and tables...")
        if not initialize_database():
            raise RuntimeError("Schema initialization failed (see errors above)")
        logger.info("✅ Phase 0 complete: Staging schema initialized")
        
        return {
            'status': 'success',
            'phase': 0,
            'message': 'Staging schema created'
        }
    except Exception as e:
        logger.error(f"❌ Phase 0 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 0,
            'error': str(e)
        }


def run_phase_seed(num_trades: int = 50, start_date: str = "2025-08-25") -> Dict[str, Any]:
    """
    Pre-Phase 1: Seed test trade data into OLTP.
    
    Generates realistic trades for testing.
    """
    logger.info("="*80)
    logger.info("SEEDING: TEST TRADE DATA")
    logger.info("="*80)
    
    try:
        from etl.seed_trades import seed_trades_main
        
        wait_for_oltp()
        logger.info(f"Generating {num_trades} test trades...")
        result = seed_trades_main(num_trades=num_trades, start_date=start_date)
        
        if result['failed']:
            raise RuntimeError(f"{result['failed']} of {result['total_trades']} trades failed to insert")
        
        logger.info(f"✅ Seed complete: {result['inserted']} trades inserted")
        return {
            'status': 'success',
            'phase': 'seed',
            'trades_inserted': result['inserted'],
            'message': f"Seeded {result['inserted']} trades"
        }
    except Exception as e:
        logger.error(f"❌ Seed failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 'seed',
            'error': str(e)
        }


def run_phase_1() -> Dict[str, Any]:
    """
    Phase 1: Extract data from OLTP to staging.
    
    Extracts:
      - exchanges (2 rows)
      - accounts (5 rows)
      - instruments (7 rows)
      - holdings (8 rows)
      - trades (full history, unless ETL_LOOKBACK_DAYS is set)
    """
    logger.info("="*80)
    logger.info("PHASE 1: DATA EXTRACTION")
    logger.info("="*80)
    
    try:
        from etl.extract_etl import run_etl
        
        wait_for_oltp()
        logger.info("Extracting data from OLTP to staging...")
        result = run_etl()
        
        # Extract the stats from the result dict
        extraction_stats = result.get('extraction_stats', result)  # Handle both old and new formats
        
        logger.info("Extraction results:")
        for table, (extracted, loaded) in extraction_stats.items():
            logger.info(f"  {table:15} | extracted: {extracted:>3} | loaded: {loaded:>3}")
        
        total_rows = sum(loaded for _, (_, loaded) in extraction_stats.items())
        
        # 'partial' (rows lost on load) is not good enough for the phases downstream
        status = result.get('status')
        if status != 'success':
            failed_tables = ', '.join(result.get('failed_tables') or []) or 'none reported'
            raise RuntimeError(f"Extraction status: {status} (failed tables: {failed_tables})")
        
        logger.info(f"✅ Phase 1 complete: {total_rows} total rows loaded")
        
        return {
            'status': 'success',
            'phase': 1,
            'extraction_results': extraction_stats,
            'total_rows': total_rows
        }
    except Exception as e:
        logger.error(f"❌ Phase 1 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 1,
            'error': str(e)
        }


def run_phase_2() -> Dict[str, Any]:
    """
    Phase 2: Create analytics star schema and load dimensions.
    
    Creates:
      - analytics schema and tables (dimensions, facts, audit)
      - dimension rows from staging (exchanges, instruments, accounts)
      - date dimension covering every extracted trade date
    
    Full refresh: the dimension and fact tables are dropped and recreated,
    so the fact tables are empty until Phase 3 reloads them.
    """
    logger.info("="*80)
    logger.info("PHASE 2: ANALYTICS SCHEMA + DIMENSIONS")
    logger.info("="*80)
    
    try:
        from sqlalchemy import text
        from etl.config import get_analytics_engine
        from etl.generate_dates import generate_date_dimension
        
        schema_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            'schema', '01_ANALYTICS_SCHEMA.sql'
        )
        with open(schema_path, 'r') as f:
            schema_sql = f.read()
        
        logger.info("Creating analytics schema and loading dimensions from staging...")
        engine = get_analytics_engine()
        with engine.begin() as conn:
            conn.execute(text(schema_sql))
            first_trade, last_trade = conn.execute(text(
                "SELECT MIN(executed_at), MAX(executed_at) FROM staging.trades_raw"
            )).fetchone()
        
        # fact_trades.executed_date references dim_dates, so cover every trade date
        # (padded a day each side for timezone differences) as well as the default window
        today = datetime.now().date()
        start_date = today - timedelta(days=30)
        end_date = today + timedelta(days=30)
        if first_trade is not None:
            start_date = min(start_date, first_trade.date() - timedelta(days=1))
            end_date = max(end_date, last_trade.date() + timedelta(days=1))
        
        generate_date_dimension(start_date=start_date, end_date=end_date)
        logger.info(f"✅ Phase 2 complete: analytics schema ready, dates {start_date} to {end_date}")
        logger.info("   Fact tables were rebuilt empty; Phase 3 loads analytics.fact_trades")
        
        return {
            'status': 'success',
            'phase': 2,
            'message': 'Analytics schema and dimensions loaded'
        }
    except Exception as e:
        logger.error(f"❌ Phase 2 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 2,
            'error': str(e)
        }


def run_phase_3() -> Dict[str, Any]:
    """
    Phase 3: Calculate P&L using FIFO matching.
    
    Reads trades from staging, applies FIFO algorithm,
    and loads results to fact_trades table.
    """
    logger.info("="*80)
    logger.info("PHASE 3: P&L TRANSFORMATION (FIFO)")
    logger.info("="*80)
    
    try:
        from etl.transform_pnl import run_pnl_transformation
        
        run_id = f"pipeline-run-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        logger.info(f"Running P&L transformation (run_id: {run_id})...")
        result = run_pnl_transformation(run_id)
        
        logger.info("P&L transformation results:")
        logger.info(f"  Status:           {result.get('status', 'N/A')}")
        logger.info(f"  Trades processed: {result.get('trades_processed', 0)}")
        logger.info(f"  Trades loaded:    {result.get('trades_loaded', 0)}")
        logger.info(f"  Matched trades:   {result.get('matched_trades', 0)}")
        logger.info(f"  Orphan trades:    {result.get('orphan_trades', 0)}")
        
        status = result.get('status', 'unknown')
        if status == 'success':
            logger.info(f"✅ Phase 3 complete: {result.get('trades_loaded', 0)} P&L records loaded")
        else:
            logger.error(f"❌ Phase 3 {status}: {result.get('error', 'no error reported')}")
        
        phase_result = {
            'status': status,
            'phase': 3,
            'transformation_results': result
        }
        if result.get('error'):
            phase_result['error'] = result['error']
        return phase_result
    except Exception as e:
        logger.error(f"❌ Phase 3 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 3,
            'error': str(e)
        }


def _run_phases(phase: Optional[int], seed: bool, trades: int, start_date: str) -> list:
    """Run the requested phases in order, stopping at the first one that fails."""
    steps = []
    if phase is None or phase == 0:
        steps.append(run_phase_0)
    if seed:
        # Only when asked: this writes test data into OLTP
        steps.append(lambda: run_phase_seed(num_trades=trades, start_date=start_date))
    if phase is None or phase == 1:
        steps.append(run_phase_1)
    if phase is None or phase == 2:
        steps.append(run_phase_2)
    if phase is None or phase == 3:
        steps.append(run_phase_3)
    
    results = []
    for step in steps:
        result = step()
        results.append(result)
        if result['status'] != 'success':
            logger.error(f"Phase {result.get('phase')} did not succeed. Stopping.")
            break
    return results


def run_pipeline(phase: Optional[int] = None, seed: bool = False, trades: int = 50,
                 start_date: str = "2025-08-25") -> Dict[str, Any]:
    """
    Run the ETL pipeline and return the outcome. Never raises and never exits,
    so it is safe to call from a long-running process such as an API.
    
    Args:
        phase: Run only this phase (0-3). Default: all phases in order
        seed: Insert generated test trades into OLTP first (default: False)
        trades: Number of test trades to generate, only with seed
        start_date: Start date for generated trades (YYYY-MM-DD), only with seed
    
    Returns:
        dict:
        {
            'status': 'success' | 'failed' | 'busy' | 'interrupted',
            'phases': [one result dict per phase that ran],
            'summary': Phase 3 statistics (trades_loaded, matched_trades, ...) or None,
            'error': message of the phase that failed, or None,
            'duration_seconds': float
        }
        'busy' means another run holds the lock; nothing was changed by this call.
    """
    started = time.monotonic()
    results = []
    error = None
    
    try:
        with pipeline_lock() as acquired:
            if not acquired:
                status = 'busy'
                error = "Another pipeline run is already in progress"
                logger.warning(f"⚠️  {error}")
            else:
                results = _run_phases(phase, seed, trades, start_date)
                failed = [r for r in results if r['status'] != 'success']
                status = 'failed' if failed else 'success'
                if failed:
                    error = failed[0].get('error') or f"Phase {failed[0].get('phase')} {failed[0]['status']}"
    except KeyboardInterrupt:
        logger.warning("\n⚠️  Pipeline interrupted by user")
        status = 'interrupted'
        error = "Interrupted by user"
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")
        status = 'failed'
        error = str(e)
    
    summary = next((r.get('transformation_results') for r in results
                    if r.get('phase') == 3 and r['status'] == 'success'), None)
    
    return {
        'status': status,
        'phases': results,
        'summary': summary,
        'error': error,
        'duration_seconds': round(time.monotonic() - started, 3),
    }


def main():
    """Command-line entry point: parse arguments, run the pipeline, print the summary."""
    parser = argparse.ArgumentParser(
        description='ETL Pipeline Orchestrator - Run all phases in order',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 run_pipeline.py                    # Run all phases on the existing OLTP data
  python3 run_pipeline.py --json             # Same, and print the result as JSON on stdout
  python3 run_pipeline.py --seed             # Insert 50 test trades into OLTP first
  python3 run_pipeline.py --seed --trades 100  # Insert 100 test trades first
  python3 run_pipeline.py --phase 1          # Run only Phase 1
  python3 run_pipeline.py --phase 3          # Run only Phase 3 (assumes Phase 0+1+2 done)

Exit codes: 0 = success, 1 = failed, 2 = busy (another run holds the lock)
        """
    )
    
    parser.add_argument(
        '--phase',
        type=int,
        choices=[0, 1, 2, 3],
        default=None,
        help='Run only a specific phase (0, 1, 2, or 3). Default: run all phases'
    )
    
    parser.add_argument(
        '--trades',
        type=int,
        default=50,
        help='Number of test trades to generate, only with --seed (default: 50)'
    )
    
    parser.add_argument(
        '--seed',
        action='store_true',
        help='Insert generated test trades into OLTP before running (off by default)'
    )
    
    parser.add_argument(
        '--skip-seed',
        action='store_true',
        help='Deprecated: seeding is now off unless --seed is given'
    )
    
    parser.add_argument(
        '--start-date',
        type=str,
        default='2025-08-25',
        help='Start date for generated trades, only with --seed (YYYY-MM-DD, default: 2025-08-25)'
    )
    
    parser.add_argument(
        '--json',
        action='store_true',
        help='Print the run result as one JSON object on stdout (logs stay on stderr)'
    )
    
    args = parser.parse_args()
    
    outcome = run_pipeline(
        phase=args.phase,
        seed=args.seed,
        trades=args.trades,
        start_date=args.start_date,
    )
    pipeline_status = outcome['status']
    
    # Final summary
    logger.info("="*80)
    logger.info("PIPELINE SUMMARY")
    logger.info("="*80)
    
    for result in outcome['phases']:
        phase = result.get('phase', 'unknown')
        status = result.get('status', 'unknown').upper()
        symbol = '✅' if status == 'SUCCESS' else '❌'
        logger.info(f"{symbol} Phase {phase}: {status}")
        if result.get('error'):
            logger.info(f"   Error: {result['error']}")
    
    logger.info("="*80)
    if pipeline_status == 'success':
        logger.info(f"✅ PIPELINE COMPLETE - Status: {pipeline_status.upper()} "
                    f"({outcome['duration_seconds']}s)")
        logger.info("\n📊 Next steps:")
        logger.info("   1. Query the admin P&L views:")
        logger.info("      SELECT * FROM analytics.v_pnl_summary;")
        logger.info("   2. Run unit tests:")
        logger.info("      pytest tests/test_transform_pnl.py -v")
    elif pipeline_status == 'busy':
        logger.info("⏳ PIPELINE NOT RUN - another run is already in progress")
    else:
        logger.info(f"❌ PIPELINE FAILED - Status: {pipeline_status.upper()}")
        if outcome['error'] and not outcome['phases']:
            logger.info(f"   Error: {outcome['error']}")
    logger.info("="*80)
    
    if args.json:
        # default=str covers Decimal totals and datetimes
        print(json.dumps(outcome, default=str))
    
    return EXIT_CODES.get(pipeline_status, 1)


if __name__ == "__main__":
    sys.exit(main())
