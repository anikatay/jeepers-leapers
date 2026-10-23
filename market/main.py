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

TICKER_RE = re.compile(r"^[A-Z0-9][A-Z0-9.\-_=]{0,31}$")
CURRENCY_RE = re.compile(r"^[A-Z]{3}$")
ALLOWED_PERIODS = {"1mo", "3mo", "6mo", "1y", "2y", "5y", "max"}
SUPPORTED_QUOTE_TYPES = {"EQUITY", "ETF", "CURRENCY", "CRYPTOCURRENCY"}
SUPPORTED_CURRENCIES = {"USD", "EUR", "INR", "GBP"}
EXCHANGE_ALIASES = {
    "NMS": "NASDAQ",
    "NASDAQ": "NASDAQ",
    "NASDAQGS": "NASDAQ",
    "NASDAQCM": "NASDAQ",
    "NASDAQGM": "NASDAQ",
    "NCM": "NASDAQ",
    "NGM": "NASDAQ",
    "NYQ": "NYSE",
    "NYSE": "NYSE",
    "LSE": "LSE",
    "NSI": "NSE",
    "NSE": "NSE",
    "BSE": "BSE",
}
PRICE_SCALES: Dict[str, float] = {}
BACKEND_QUOTE_URL = os.getenv(
    "BACKEND_QUOTE_URL",
    "http://app:8080/internal/market/quotes",
)
SECTOR_STREAM_ENABLED = os.getenv("SECTOR_STREAM_ENABLED", "true").lower() in {
    "1",
    "true",
    "yes",
    "on",
}
SECTOR_UNIVERSE_VERSION = "us-large-cap-v1"
SECTOR_SELECTION_BASIS = "configured_large_cap_order"

# The sidecar owns the initial universe. The order is the configured rank within
# each sector; replacing this list is an explicit universe update.
SECTOR_UNIVERSE = {
    "communication-services": {
        "name": "Communication Services",
        "tickers": ["GOOGL", "META", "NFLX", "DIS", "CMCSA", "VZ", "T", "TMUS", "CHTR", "EA"],
    },
    "consumer-discretionary": {
        "name": "Consumer Discretionary",
        "tickers": ["AMZN", "TSLA", "HD", "MCD", "TJX", "LOW", "BKNG", "NKE", "SBUX", "ORLY"],
    },
    "consumer-staples": {
        "name": "Consumer Staples",
        "tickers": ["WMT", "COST", "PG", "KO", "PEP", "PM", "MO", "MDLZ", "CL", "KMB"],
    },
    "energy": {
        "name": "Energy",
        "tickers": ["XOM", "CVX", "COP", "EOG", "SLB", "OXY", "MPC", "PSX", "VLO", "WMB"],
    },
    "financials": {
        "name": "Financials",
        "tickers": ["JPM", "V", "MA", "BAC", "WFC", "GS", "MS", "BLK", "C", "AXP"],
    },
    "health-care": {
        "name": "Health Care",
        "tickers": ["LLY", "UNH", "JNJ", "ABBV", "MRK", "TMO", "ABT", "AMGN", "PFE", "ISRG"],
    },
    "industrials": {
        "name": "Industrials",
        "tickers": ["GE", "CAT", "RTX", "HON", "UNP", "DE", "BA", "ADP", "ETN", "LMT"],
    },
    "information-technology": {
        "name": "Information Technology",
        "tickers": ["NVDA", "MSFT", "AAPL", "AVGO", "ORCL", "CRM", "AMD", "CSCO", "IBM", "ADBE"],
    },
    "materials": {
        "name": "Materials",
        "tickers": ["LIN", "SHW", "APD", "ECL", "NEM", "FCX", "NUE", "DOW", "DD", "CTVA"],
    },
    "real-estate": {
        "name": "Real Estate",
        "tickers": ["PLD", "AMT", "EQIX", "WELL", "SPG", "O", "PSA", "CCI", "DLR", "VICI"],
    },
    "utilities": {
        "name": "Utilities",
        "tickers": ["NEE", "SO", "DUK", "CEG", "SRE", "AEP", "EXC", "XEL", "ED", "PCG"],
    },
}
SECTOR_TICKERS = tuple(
    ticker
    for sector in SECTOR_UNIVERSE.values()
    for ticker in sector["tickers"]
)
SECTOR_TICKER_SET = set(SECTOR_TICKERS)


def sector_catalog_payload() -> dict:
    return {
        "market": "US",
        "selectionBasis": SECTOR_SELECTION_BASIS,
        "universeVersion": SECTOR_UNIVERSE_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sectors": [
            {
                "id": sector_id,
                "name": sector["name"],
                "tickers": [
                    {"ticker": ticker, "rank": rank}
                    for rank, ticker in enumerate(sector["tickers"], start=1)
                ],
            }
            for sector_id, sector in SECTOR_UNIVERSE.items()
        ],
    }


def sector_or_404(raw_sector_id: str) -> tuple[str, dict]:
    sector_id = raw_sector_id.strip().lower()
    sector = SECTOR_UNIVERSE.get(sector_id)
    if sector is None:
        raise HTTPException(status_code=404, detail=f"Unknown sector: {raw_sector_id}")
    return sector_id, sector


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


def normalize_currency(raw: object) -> tuple[str, float]:
    code = str(raw or "").strip()
    if code in {"GBp", "GBX"}:
        return "GBP", 0.01
    return code.upper(), 1.0


def price_scale_for_ticker(ticker: str) -> float:
    return PRICE_SCALES.get(ticker, 0.01 if ticker.endswith(".L") else 1.0)


def scale_price(value: object, ticker: str) -> float:
    return float(value) * price_scale_for_ticker(ticker)


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
    currency, price_scale = normalize_currency(info.get("currency"))
    if not info or not info.get("symbol") or quote_type not in SUPPORTED_QUOTE_TYPES:
        raise HTTPException(status_code=404, detail=f"No tradable ticker found for {ticker}")
    if not exchange_code:
        raise HTTPException(status_code=404, detail=f"No exchange found for {ticker}")
    if currency not in SUPPORTED_CURRENCIES:
        raise HTTPException(
            status_code=422,
            detail=f"Currency {currency or 'unknown'} is not supported",
        )

    if quote_type == "CRYPTOCURRENCY":
        exchange_id = "CRYPTO"
        exchange_name = "Cryptocurrency Market"
        region = "GLOBAL"
        timezone = "UTC"
    elif quote_type == "CURRENCY":
        exchange_id = "FOREX"
        exchange_name = "Foreign Exchange Market"
        region = "GLOBAL"
        timezone = "UTC"
    else:
        exchange_id = EXCHANGE_ALIASES.get(exchange_code, exchange_code)
        exchange_defaults = {
            "LSE": ("London Stock Exchange", "UK", "Europe/London"),
            "NSE": ("National Stock Exchange of India", "INDIA", "Asia/Kolkata"),
            "BSE": ("BSE India", "INDIA", "Asia/Kolkata"),
            "NASDAQ": ("NASDAQ", "USA", "America/New_York"),
            "NYSE": ("New York Stock Exchange", "USA", "America/New_York"),
        }
        exchange_default = exchange_defaults.get(exchange_id)
        if exchange_default is not None:
            exchange_name, region, timezone = exchange_default
        else:
            exchange_name = (
                info.get("fullExchangeName")
                or info.get("exchange")
                or exchange_id
            )
            raw_region = info.get("region") or info.get("market") or currency
            region_code = str(raw_region).upper()
            region = (
                "USA" if region_code in {"US", "USA"} else
                "UK" if region_code in {"GB", "GBR", "UK"} else
                "INDIA" if region_code in {"IN", "IND", "INDIA"} else
                raw_region
            )
            timezone = info.get("exchangeTimezoneName") or "UTC"

    if not re.fullmatch(r"[A-Z0-9.\-]{1,64}", exchange_id):
        raise HTTPException(status_code=422, detail=f"Unsupported exchange for {ticker}")

    price = info.get("regularMarketPrice") or info.get("currentPrice")
    if price is None and history_frame is not None and not history_frame.empty:
        closes = history_frame["Close"].dropna()
        if not closes.empty:
            price = float(closes.iloc[-1])

    try:
        normalized_price = float(price) * price_scale
    except (TypeError, ValueError):
        normalized_price = math.nan
    if not math.isfinite(normalized_price) or normalized_price <= 0:
        raise HTTPException(status_code=422, detail=f"No usable price found for {ticker}")

    PRICE_SCALES[ticker] = price_scale
    return {
        "ticker": ticker,
        "name": info.get("longName") or info.get("shortName") or ticker,
        "exchangeId": exchange_id,
        "exchangeName": exchange_name,
        "region": region,
        "timezone": timezone,
        "currency": currency,
        "priceScale": price_scale,
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
            if websocket is None or ticker in self.active:
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

        normalized_ticker = str(ticker).upper()
        try:
            normalized_price = scale_price(price, normalized_ticker)
            timestamp_seconds = float(timestamp)
            if timestamp_seconds > 100_000_000_000:
                timestamp_seconds /= 1000
            quote_time = datetime.fromtimestamp(timestamp_seconds, timezone.utc)
        except (TypeError, ValueError, OverflowError, OSError):
            log.warning("Ignoring quote with invalid timestamp for %s: %r", ticker, timestamp)
            return

        payload = {
            "type": "QUOTE",
            "ticker": normalized_ticker,
            "price": normalized_price,
            "quoteTime": quote_time.isoformat(),
            "receivedTime": datetime.now(timezone.utc).isoformat(),
            "source": "yfinance-websocket",
            "status": "LIVE",
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
    if SECTOR_STREAM_ENABLED:
        stream.desired.update(SECTOR_TICKERS)
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
        "sectorStreamEnabled": SECTOR_STREAM_ENABLED,
        "sectorTickerCount": len(SECTOR_TICKERS),
    }


@app.post("/stream/subscribe/{ticker}")
async def subscribe_stream(ticker: str):
    normalized = validate_ticker(ticker)
    if SECTOR_STREAM_ENABLED and normalized in SECTOR_TICKER_SET:
        return {
            "ticker": normalized,
            "subscribed": True,
            "persistent": True,
        }
    await stream.add(normalized)
    return {"ticker": normalized, "subscribed": True}


@app.delete("/stream/subscribe/{ticker}")
async def unsubscribe_stream(ticker: str):
    normalized = validate_ticker(ticker)
    if SECTOR_STREAM_ENABLED and normalized in SECTOR_TICKER_SET:
        return {
            "ticker": normalized,
            "subscribed": True,
            "persistent": True,
        }
    await stream.remove(normalized)
    return {"ticker": normalized, "subscribed": False}


@app.get("/stream/subscriptions")
async def stream_subscriptions():
    return {"tickers": sorted(stream.desired)}


@app.get("/sectors")
def list_sectors():
    return sector_catalog_payload()


@app.get("/sectors/{sector_id}")
def get_sector(sector_id: str):
    normalized_id, sector = sector_or_404(sector_id)
    catalog = sector_catalog_payload()
    return {
        "market": catalog["market"],
        "selectionBasis": catalog["selectionBasis"],
        "universeVersion": catalog["universeVersion"],
        "generatedAt": catalog["generatedAt"],
        "sector": {
            "id": normalized_id,
            "name": sector["name"],
            "tickers": [
                {"ticker": ticker, "rank": rank}
                for rank, ticker in enumerate(sector["tickers"], start=1)
            ],
        },
    }


@app.get("/lookup/{ticker}")
def lookup_ticker(ticker: str):
    return market_instrument_metadata(validate_ticker(ticker))


def validate_currency(raw: str) -> str:
    currency = raw.strip().upper()
    if not CURRENCY_RE.fullmatch(currency) or currency not in SUPPORTED_CURRENCIES:
        raise HTTPException(status_code=400, detail="Unsupported currency")
    return currency


def fetch_fx_pair(base: str, quote: str) -> float | None:
    symbol = f"{base}{quote}=X"
    try:
        frame = yf.Ticker(symbol).history(
            period="5d",
            interval="1d",
            auto_adjust=False,
            timeout=10,
        )
    except Exception:
        log.warning("FX lookup failed for %s", symbol, exc_info=True)
        return None

    if frame is None or frame.empty:
        return None
    closes = frame["Close"].dropna()
    if closes.empty:
        return None
    rate = float(closes.iloc[-1])
    return rate if math.isfinite(rate) and rate > 0 else None


def resolve_fx_rate(base: str, quote: str) -> float:
    if base == quote:
        return 1.0

    direct = fetch_fx_pair(base, quote)
    if direct is not None:
        return direct

    inverse = fetch_fx_pair(quote, base)
    if inverse is not None:
        return 1.0 / inverse

    if base != "USD" and quote != "USD":
        base_usd = resolve_fx_rate(base, "USD")
        quote_usd = resolve_fx_rate(quote, "USD")
        return base_usd / quote_usd

    raise HTTPException(
        status_code=502,
        detail=f"Unable to obtain FX rate for {base}/{quote}",
    )


@app.get("/fx/rate")
def fx_rate(
    base: str = Query(...),
    quote: str = Query(...),
):
    base_currency = validate_currency(base)
    quote_currency = validate_currency(quote)
    return {
        "baseCurrency": base_currency,
        "quoteCurrency": quote_currency,
        "rate": resolve_fx_rate(base_currency, quote_currency),
        "quoteTime": datetime.now(timezone.utc).isoformat(),
    }


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
                "open": round(scale_price(row["Open"], ticker), 4),
                "high": round(scale_price(row["High"], ticker), 4),
                "low": round(scale_price(row["Low"], ticker), 4),
                "close": round(scale_price(row["Close"], ticker), 4),
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
                    "open": round(scale_price(row["Open"], symbol), 4),
                    "high": round(scale_price(row["High"], symbol), 4),
                    "low": round(scale_price(row["Low"], symbol), 4),
                    "close": round(scale_price(row["Close"], symbol), 4),
                    "volume": int(row.get("Volume", 0) or 0),
                }
                for idx, row in frame.iterrows()
            ]
        except (KeyError, TypeError, ValueError):
            log.warning("No batch history returned for %s", symbol)
        result[symbol] = {"ticker": symbol, "period": period, "bars": bars}
    return result
