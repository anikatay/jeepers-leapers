import asyncio
import logging
import math
import os
import re
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Dict, List, Set

import httpx
import yfinance as yf
from fastapi import FastAPI, HTTPException, Query
from websockets.exceptions import ConnectionClosed

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("market-sidecar")

TICKER_RE = re.compile(r"^[A-Z.\-]{1,10}$")
ALLOWED_PERIODS = {"1mo", "3mo", "6mo", "1y", "2y", "5y", "max"}
SUPPORTED_QUOTE_TYPES = {"EQUITY", "ETF"}
SUPPORTED_CURRENCIES = {"USD", "EUR", "INR"}
EXCHANGE_ALIASES = {
    "NMS": "NASDAQ",
    "NASDAQ": "NASDAQ",
    "NASDAQGS": "NASDAQ",
    "NASDAQCM": "NASDAQ",
    "NYQ": "NYSE",
    "NYSE": "NYSE",
}
BACKEND_QUOTE_URL = os.getenv(
    "BACKEND_QUOTE_URL",
    "http://app:8080/internal/market/quotes",
)


def validate_ticker(raw: str) -> str:
    ticker = raw.strip().upper()
    if not TICKER_RE.fullmatch(ticker):
        raise HTTPException(status_code=400, detail="Invalid ticker format")
    return ticker


def validate_tickers(raw: str) -> List[str]:
    tickers = sorted({validate_ticker(value) for value in raw.split(",") if value.strip()})
    if not tickers:
        raise HTTPException(status_code=400, detail="tickers is required")
    if len(tickers) > 50:
        raise HTTPException(status_code=400, detail="Too many tickers (max 50)")
    return tickers


def market_instrument_metadata(ticker: str) -> dict:
    yahoo_ticker = yf.Ticker(ticker)
    try:
        info = yahoo_ticker.get_info()
        history_frame = yahoo_ticker.history(
            period="5d",
            interval="1d",
            auto_adjust=False,
            timeout=10,
        )
    except Exception as exc:
        log.warning("Market metadata lookup failed for %s", ticker, exc_info=True)
        raise HTTPException(
            status_code=502,
            detail="Market data provider unavailable",
        ) from exc

    quote_type = info.get("quoteType")
    exchange_code = str(info.get("exchange") or "").strip().upper()
    currency = str(info.get("currency") or "").strip().upper()
    if not info or not info.get("symbol") or quote_type not in SUPPORTED_QUOTE_TYPES:
        raise HTTPException(status_code=404, detail=f"No tradable ticker found for {ticker}")
    if not exchange_code:
        raise HTTPException(status_code=404, detail=f"No exchange found for {ticker}")
    if currency not in SUPPORTED_CURRENCIES:
        raise HTTPException(
            status_code=422,
            detail=f"Currency {currency or 'unknown'} is not supported",
        )

    exchange_id = EXCHANGE_ALIASES.get(exchange_code, exchange_code)
    if not re.fullmatch(r"[A-Z0-9.\-]{1,64}", exchange_id):
        raise HTTPException(status_code=422, detail=f"Unsupported exchange for {ticker}")

    price = info.get("regularMarketPrice") or info.get("currentPrice")
    if price is None and history_frame is not None and not history_frame.empty:
        closes = history_frame["Close"].dropna()
        if not closes.empty:
            price = float(closes.iloc[-1])

    try:
        normalized_price = float(price)
    except (TypeError, ValueError):
        normalized_price = math.nan
    if not math.isfinite(normalized_price) or normalized_price <= 0:
        raise HTTPException(status_code=422, detail=f"No usable price found for {ticker}")

    return {
        "ticker": ticker,
        "name": info.get("longName") or info.get("shortName") or ticker,
        "exchangeId": exchange_id,
        "exchangeName": (
            info.get("fullExchangeName")
            or info.get("exchange")
            or exchange_id
        ),
        "region": info.get("region") or info.get("market") or currency,
        "timezone": info.get("exchangeTimezoneName") or "UTC",
        "currency": currency,
        "currentPrice": normalized_price,
    }


class ResilientYahooWebSocket(yf.AsyncWebSocket):
    def is_connected(self) -> bool:
        connection = getattr(self, "_ws", None)
        if connection is None:
            return False

        state = getattr(connection, "state", None)
        if state is not None:
            return getattr(state, "name", str(state)) == "OPEN"

        return getattr(connection, "closed", None) is False

    def invalidate_connection(self) -> None:
        self._ws = None
        heartbeat_task = self._heartbeat_task
        self._heartbeat_task = None
        if (
            heartbeat_task is not None
            and heartbeat_task is not asyncio.current_task()
            and not heartbeat_task.done()
        ):
            heartbeat_task.cancel()

    async def _connect(self) -> None:
        if self._ws is not None and not self.is_connected():
            self.invalidate_connection()
        await super()._connect()
        if self._message_handler is not None and self._heartbeat_task is None:
            self._heartbeat_task = asyncio.create_task(self._periodic_subscribe())


class YahooStream:
    def __init__(self) -> None:
        self.desired: Set[str] = set()
        self.active: Set[str] = set()
        self.websocket: ResilientYahooWebSocket | None = None
        self.task: asyncio.Task | None = None
        self.lock = asyncio.Lock()

    async def start(self) -> None:
        self.task = asyncio.create_task(self.run())

    async def stop(self) -> None:
        if self.task:
            self.task.cancel()
            await asyncio.gather(self.task, return_exceptions=True)
        if self.websocket:
            await self.websocket.close()

    async def add(self, ticker: str) -> None:
        async with self.lock:
            self.desired.add(ticker)
            websocket = self.websocket
            if websocket is None:
                return
            try:
                await websocket.subscribe(ticker)
            except (ConnectionClosed, OSError, TimeoutError) as exc:
                websocket.invalidate_connection()
                self.active.clear()
                log.warning(
                    "Yahoo stream disconnected while subscribing to %s; "
                    "keeping it queued for reconnect: %s",
                    ticker,
                    exc,
                )
            else:
                self.active.add(ticker)

    async def remove(self, ticker: str) -> None:
        async with self.lock:
            self.desired.discard(ticker)
            websocket = self.websocket
            if websocket is None or ticker not in self.active:
                return
            try:
                await websocket.unsubscribe(ticker)
            except (ConnectionClosed, OSError, TimeoutError) as exc:
                websocket.invalidate_connection()
                self.active.clear()
                log.warning(
                    "Yahoo stream disconnected while unsubscribing from %s; "
                    "the desired subscription was updated: %s",
                    ticker,
                    exc,
                )
            else:
                self.active.discard(ticker)

    async def run(self) -> None:
        while True:
            try:
                async with ResilientYahooWebSocket(verbose=False) as websocket:
                    self.websocket = websocket
                    async with self.lock:
                        initial = list(self.desired)
                        if initial:
                            await websocket.subscribe(initial)
                            self.active = set(initial)
                    log.info("Connected to yfinance stream for %s", sorted(self.active))
                    await websocket.listen(self.handle_message)
            except asyncio.CancelledError:
                raise
            except Exception:
                log.exception("yfinance stream failed; retrying in 5 seconds")
            finally:
                self.websocket = None
                self.active.clear()
            await asyncio.sleep(5)

    async def handle_message(self, message: dict) -> None:
        ticker = message.get("id")
        price = message.get("price")
        timestamp = message.get("time")
        if not ticker or price is None or timestamp is None:
            return

        try:
            timestamp_seconds = float(timestamp)
            if timestamp_seconds > 100_000_000_000:
                timestamp_seconds /= 1000
            quote_time = datetime.fromtimestamp(timestamp_seconds, timezone.utc)
        except (TypeError, ValueError, OverflowError, OSError):
            log.warning("Ignoring quote with invalid timestamp for %s: %r", ticker, timestamp)
            return

        payload = {
            "type": "QUOTE",
            "ticker": str(ticker).upper(),
            "price": price,
            "quoteTime": quote_time.isoformat(),
            "receivedTime": datetime.now(timezone.utc).isoformat(),
            "source": "yfinance-websocket",
        }
        try:
            async with httpx.AsyncClient(timeout=3) as client:
                response = await client.post(BACKEND_QUOTE_URL, json=payload)
                response.raise_for_status()
        except httpx.HTTPError:
            log.warning("Could not deliver quote for %s to backend", ticker, exc_info=True)


stream = YahooStream()


@asynccontextmanager
async def lifespan(_: FastAPI):
    await stream.start()
    yield
    await stream.stop()


app = FastAPI(title="market-sidecar", lifespan=lifespan)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "streamConnected": stream.websocket is not None and stream.websocket.is_connected(),
        "subscribedTickers": sorted(stream.desired),
    }


@app.post("/stream/subscribe/{ticker}")
async def subscribe_stream(ticker: str):
    normalized = validate_ticker(ticker)
    await stream.add(normalized)
    return {"ticker": normalized, "subscribed": True}


@app.delete("/stream/subscribe/{ticker}")
async def unsubscribe_stream(ticker: str):
    normalized = validate_ticker(ticker)
    await stream.remove(normalized)
    return {"ticker": normalized, "subscribed": False}


@app.get("/stream/subscriptions")
async def stream_subscriptions():
    return {"tickers": sorted(stream.desired)}


@app.get("/lookup/{ticker}")
def lookup_ticker(ticker: str):
    return market_instrument_metadata(validate_ticker(ticker))


@app.get("/history")
def history(ticker: str = Query(...), period: str = Query("1y")):
    ticker = validate_ticker(ticker)
    if period not in ALLOWED_PERIODS:
        raise HTTPException(status_code=400, detail="Invalid period")
    try:
        df = yf.Ticker(ticker).history(period=period, interval="1d", timeout=10)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Market data upstream failure") from exc
    if df is None or df.empty:
        raise HTTPException(status_code=404, detail=f"No data for {ticker}")
    return {
        "ticker": ticker,
        "period": period,
        "bars": [
            {
                "date": idx.strftime("%Y-%m-%d"),
                "open": round(float(row["Open"]), 4),
                "high": round(float(row["High"]), 4),
                "low": round(float(row["Low"]), 4),
                "close": round(float(row["Close"]), 4),
                "volume": int(row["Volume"]),
            }
            for idx, row in df.iterrows()
        ],
    }


@app.get("/history/batch")
def history_batch(tickers: str = Query(...), period: str = Query("1y")) -> Dict[str, dict]:
    if period not in ALLOWED_PERIODS:
        raise HTTPException(status_code=400, detail="Invalid period")
    symbols = validate_tickers(tickers)
    try:
        df = yf.download(
            tickers=" ".join(symbols),
            period=period,
            interval="1d",
            group_by="ticker",
            threads=False,
            progress=False,
            timeout=10,
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Market data upstream failure") from exc

    result: Dict[str, dict] = {}
    for symbol in symbols:
        bars = []
        try:
            frame = df[symbol] if len(symbols) > 1 else df
            frame = frame.dropna(subset=["Open", "High", "Low", "Close"])
            bars = [
                {
                    "date": idx.strftime("%Y-%m-%d"),
                    "open": round(float(row["Open"]), 4),
                    "high": round(float(row["High"]), 4),
                    "low": round(float(row["Low"]), 4),
                    "close": round(float(row["Close"]), 4),
                    "volume": int(row.get("Volume", 0) or 0),
                }
                for idx, row in frame.iterrows()
            ]
        except (KeyError, TypeError, ValueError):
            log.warning("No batch history returned for %s", symbol)
        result[symbol] = {"ticker": symbol, "period": period, "bars": bars}
    return result
