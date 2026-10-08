import re
from fastapi import FastAPI, HTTPException, Query
import yfinance as yf

app = FastAPI(title="market-sidecar")

TICKER_RE = re.compile(r"^[A-Z.\-]{1,10}$")
ALLOWED_PERIODS = {"1mo", "3mo", "6mo", "1y", "2y", "5y", "max"}


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