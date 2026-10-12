import re
from fastapi import FastAPI, HTTPException, Query
import yfinance as yf
import pandas as pd
from typing import Dict, List

app = FastAPI(title="market-sidecar")

TICKER_RE = re.compile(r"^[A-Z.\-]{1,10}$")
ALLOWED_PERIODS = {"1mo", "3mo", "6mo", "1y", "2y", "5y", "max"}

def _validate_tickers(raw: str) -> List[str]:
    tickers = sorted(set([t.strip().upper() for t in raw.split(",") if t.strip()]))
    if not tickers:
        raise HTTPException(status_code=400, detail="tickers is required")
    bad = [t for t in tickers if not TICKER_RE.match(t)]
    if bad:
        raise HTTPException(status_code=400, detail=f"Invalid tickers: {bad}")
    if len(tickers) > 50:
        raise HTTPException(status_code=400, detail="Too many tickers (max 50)")
    return tickers

@app.get("/history/batch")
def history_batch(
    tickers: str = Query(..., description="comma-separated tickers"),
    period: str = Query("1y")
) -> Dict[str, dict]:
    if period not in ALLOWED_PERIODS:
        raise HTTPException(status_code=400, detail="Invalid period")

    symbols = _validate_tickers(tickers)

    try:
        # Multi-ticker fetch in one upstream request flow
        df = yf.download(
            tickers=" ".join(symbols),
            period=period,
            interval="1d",
            group_by="ticker",
            threads=False,
            progress=False,
            timeout=10
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Upstream error: {exc}")

    result: Dict[str, dict] = {}
    for sym in symbols:
        bars = []
        try:
            sdf = df[sym] if isinstance(df.columns, pd.MultiIndex) else df
            sdf = sdf.dropna(subset=["Open", "High", "Low", "Close"])
            for idx, row in sdf.iterrows():
                bars.append({
                    "date": idx.strftime("%Y-%m-%d"),
                    "open": round(float(row["Open"]), 4),
                    "high": round(float(row["High"]), 4),
                    "low": round(float(row["Low"]), 4),
                    "close": round(float(row["Close"]), 4),
                    "volume": int(row.get("Volume", 0) or 0),
                })
        except Exception:
            bars = []

        result[sym] = {"ticker": sym, "period": period, "bars": bars}

    return result

@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/history")
def history(ticker: str = Query(...), period: str = Query("1y")):
    ticker = ticker.strip().upper()
    if not TICKER_RE.match(ticker):
        raise HTTPException(status_code=400, detail="Invalid ticker format")
    if period not in ALLOWED_PERIODS:
        raise HTTPException(status_code=400, detail="Invalid period")

    try:
        df = yf.Ticker(ticker).history(period=period, interval="1d", timeout=10)
    except Exception as exc:
        # Yahoo down, rate limited, or network problem: not the same as "ticker doesn't exist"
        raise HTTPException(status_code=502, detail=f"Upstream error: {exc}")

    if df is None or df.empty:
        raise HTTPException(status_code=404, detail=f"No data for {ticker}")

    bars = [
        {
            "date": idx.strftime("%Y-%m-%d"),
            "open": round(float(row["Open"]), 4),
            "high": round(float(row["High"]), 4),
            "low": round(float(row["Low"]), 4),
            "close": round(float(row["Close"]), 4),
            "volume": int(row["Volume"]),
        }
        for idx, row in df.iterrows()
    ]
    return {"ticker": ticker, "period": period, "bars": bars}