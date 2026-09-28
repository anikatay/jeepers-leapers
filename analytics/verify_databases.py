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
    
    db_password = os.getenv('DB_PASSWORD')
    db_host = os.getenv('DB_HOST')
    
    if not db_password or not db_host:
        print("[ERROR] DB_PASSWORD and DB_HOST environment variables required")
        return False
    
    # Step 1: Verify paysprint database exists
    print("=" * 60)
    print("Step 1: Verifying paysprint database (source OLTP)")
    print("=" * 60)
    
    try:
        conn_str = f'postgresql://paysprint:{db_password}@{db_host}:5432/paysprint'
        conn = psycopg2.connect(conn_str)
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
        # Connect to 'postgres' database to check/create paysprint_analytics
        postgres_str = f'postgresql://paysprint:{db_password}@{db_host}:5432/postgres'
        conn = psycopg2.connect(postgres_str)
        conn.autocommit = True
        cursor = conn.cursor()
        
        # Check if paysprint_analytics exists
        cursor.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            ('paysprint_analytics',)
        )
        exists = cursor.fetchone()
        
        if exists:
            print("[OK] Database paysprint_analytics already exists")
        else:
            print("[INFO] Database paysprint_analytics does not exist")
            print("[INFO] Creating database paysprint_analytics...")
            cursor.execute("CREATE DATABASE paysprint_analytics")
            print("[OK] Created database paysprint_analytics")
        
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"[ERROR] Failed to check/create paysprint_analytics:")
        print(f"    {e}")
        return False
    
    # Step 3: Verify connection to paysprint_analytics
    print()
    print("=" * 60)
    print("Step 3: Verifying connection to paysprint_analytics")
    print("=" * 60)
    
    try:
        analytics_str = f'postgresql://paysprint:{db_password}@{db_host}:5432/paysprint_analytics'
        conn = psycopg2.connect(analytics_str)
        cursor = conn.cursor()
        cursor.execute("SELECT 1")
        cursor.close()
        conn.close()
        print("[OK] Successfully connected to paysprint_analytics")
    except Exception as e:
        print(f"[ERROR] Failed to connect to paysprint_analytics:")
        print(f"    {e}")
        return False
    
    # Success
    print()
    print("=" * 60)
    print("SUCCESS: All databases ready for ETL")
    print("=" * 60)
    print()
    print("Database URLs configured in .env:")
    print(f"  Source (OLTP):     postgresql://paysprint:***@{db_host}:5432/paysprint")
    print(f"  Analytics (Staging): postgresql://paysprint:***@{db_host}:5432/paysprint_analytics")
    print()
    print("Ready to run ETL script:")
    print("  python analytics/etl/simple_etl.py")
    print()
    
    return True

if __name__ == '__main__':
    success = main()
    sys.exit(0 if success else 1)
