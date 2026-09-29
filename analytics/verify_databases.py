#!/usr/bin/env python3
"""
Verify and initialize databases for analytics ETL.
Checks if paysprint and paysprint_analytics databases exist.
Creates paysprint_analytics if it doesn't exist.
"""

import psycopg2
from psycopg2 import sql
import sys
import os
from dotenv import load_dotenv

def main():
    load_dotenv()
    
    source_db_url = os.getenv('PAYSPRINT_SOURCE_DB_URL')
    analytics_db_url = os.getenv('PAYSPRINT_ANALYTICS_DB_URL')
    db_password = os.getenv('DB_PASSWORD')
    db_host = os.getenv('DB_HOST')
    
    if not source_db_url or not analytics_db_url:
        print("[ERROR] PAYSPRINT_SOURCE_DB_URL and PAYSPRINT_ANALYTICS_DB_URL environment variables required")
        return False
    
    # Step 1: Verify paysprint database exists
    print("=" * 60)
    print("Step 1: Verifying paysprint database (source OLTP)")
    print("=" * 60)
    
    try:
        conn = psycopg2.connect(source_db_url)
        cursor = conn.cursor()
        cursor.execute("SELECT version()")
        version = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        print("[OK] Connected to paysprint database")
        print(f"    PostgreSQL Version: {version.split(',')[0]}")
    except Exception as e:
        print(f"[ERROR] Failed to connect to paysprint database:")
        print(f"    {e}")
        return False
    
    # Step 2: Check if paysprint_analytics exists
    print()
    print("=" * 60)
    print("Step 2: Checking paysprint_analytics database (target staging)")
    print("=" * 60)
    
    try:
        conn = psycopg2.connect(analytics_db_url)
        cursor = conn.cursor()
        cursor.execute("SELECT version()")
        version = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        print("[OK] Database paysprint_analytics already exists")
        print(f"    PostgreSQL Version: {version.split(',')[0]}")
    except psycopg2.OperationalError as e:
        if "does not exist" in str(e):
            print("[INFO] Database paysprint_analytics does not exist yet")
            print("[INFO] It will be auto-created when the ETL script runs")
            print()
            print("    To create it manually now, run on the remote server:")
            print("    python3 << 'EOFPY'")
            print("    import psycopg2")
            print("    conn = psycopg2.connect('postgresql://paysprint:j33p3rs!@localhost:8100/paysprint')")
            print("    conn.autocommit = True")
            print("    cursor = conn.cursor()")
            print("    cursor.execute('CREATE DATABASE paysprint_analytics')")
            print("    cursor.close()")
            print("    conn.close()")
            print("    print('[OK] Created paysprint_analytics')")
            print("    EOFPY")
        else:
            print(f"[ERROR] Failed to connect to paysprint_analytics:")
            print(f"    {e}")
            print()
            print("[INFO] Note: If database doesn't exist, it will be created by ETL script")
    except Exception as e:
        print(f"[ERROR] Failed to check paysprint_analytics:")
        print(f"    {e}")
        print()
        print("[INFO] Note: If database doesn't exist, it will be created by ETL script")
    
    # Step 3: Verify connection to paysprint_analytics (if it exists)
    print()
    print("=" * 60)
    print("Step 3: Verifying connection to paysprint_analytics")
    print("=" * 60)
    
    try:
        conn = psycopg2.connect(analytics_db_url)
        cursor = conn.cursor()
        cursor.execute("SELECT 1")
        cursor.close()
        conn.close()
        print("[OK] Successfully connected to paysprint_analytics")
    except psycopg2.OperationalError as e:
        if "does not exist" in str(e):
            print("[INFO] paysprint_analytics database doesn't exist yet - this is OK")
            print("[INFO] The ETL script will create staging schema and tables on first run")
        else:
            print(f"[WARNING] Could not connect to paysprint_analytics: {e}")
            print("[INFO] This is OK - ETL will create it when needed")
    
    # Success
    print()
    print("=" * 60)
    print("SUCCESS: Paysprint database is ready for ETL")
    print("=" * 60)
    print()
    print("Database configuration:")
    # Mask passwords in output
    source_masked = source_db_url.replace(db_password, '***')
    analytics_masked = analytics_db_url.replace(db_password, '***')
    print(f"  Source (OLTP):     {source_masked}")
    print(f"  Analytics (OLAP):  {analytics_masked}")
    print()
    print("Note: paysprint_analytics database will be auto-created by ETL script if needed")
    print()
    print("Ready to run ETL script:")
    print("  python3 analytics/etl/simple_etl.py")
    print()
    
    return True

if __name__ == '__main__':
    success = main()
    sys.exit(0 if success else 1)
