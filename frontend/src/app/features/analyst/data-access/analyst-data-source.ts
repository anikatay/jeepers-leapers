import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

export interface AnalystInstrument {
  instrumentId: string;
  ticker: string;
  name: string;
  currentPrice: number;
}

export interface PriceBar {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface AnalystTrade {
  tradeId: string;
  accountId: string;
  instrumentId: string;
  ticker: string;
  name: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  executionPrice: number;
  tradeValue: number;
  executedAt: string;
  pnl: number;
}

export interface AnalystSnapshot {
  asOf: string;
  instruments: AnalystInstrument[];
  history: Record<string, PriceBar[]>;
  intraday: Record<string, PriceBar[]>;
  trades: AnalystTrade[];
}

export abstract class AnalystDataSource {
  abstract load(): Observable<AnalystSnapshot>;
}

@Injectable()
export class AnalystApiDataSource implements AnalystDataSource {
  private readonly snapshotUrl = '/api/analytics/dashboard/snapshot';

  constructor(private readonly http: HttpClient) {}

  load(): Observable<AnalystSnapshot> {
    return this.http.get<AnalystSnapshot>(this.snapshotUrl);
  }
}

const SESSIONS = 520;
const ACCOUNTS = 36;

const PROFILES = [
  { instrumentId: 'mock-aapl', ticker: 'AAPL', name: 'Apple Inc.', currentPrice: 226.4, vol: 0.015, drift: 0.0006, volume: 55e6 },
  { instrumentId: 'mock-nvda', ticker: 'NVDA', name: 'NVIDIA Corp.', currentPrice: 142.62, vol: 0.028, drift: 0.0012, volume: 210e6 },
  { instrumentId: 'mock-msft', ticker: 'MSFT', name: 'Microsoft Corp.', currentPrice: 510.2, vol: 0.013, drift: 0.0005, volume: 22e6 },
  { instrumentId: 'mock-tsla', ticker: 'TSLA', name: 'Tesla Inc.', currentPrice: 248.98, vol: 0.032, drift: 0.0003, volume: 95e6 },
  { instrumentId: 'mock-amzn', ticker: 'AMZN', name: 'Amazon.com Inc.', currentPrice: 231.15, vol: 0.018, drift: 0.0004, volume: 40e6 },
  { instrumentId: 'mock-goog', ticker: 'GOOGL', name: 'Alphabet Inc.', currentPrice: 248.16, vol: 0.016, drift: 0.0005, volume: 28e6 }
];

type Profile = (typeof PROFILES)[number];

@Injectable()
export class AnalystMockDataSource implements AnalystDataSource {
  load(): Observable<AnalystSnapshot> {
    return of(buildSnapshot(new Date()));
  }
}

function buildSnapshot(today: Date): AnalystSnapshot {
  const random = seeded(20261019);
  const gauss = () => Math.sqrt(-2 * Math.log(1 - random())) * Math.cos(2 * Math.PI * random());
  const dates = sessionDates(today, SESSIONS);
  const market = dates.map(() => gauss());
  const history = Object.fromEntries(PROFILES.map(profile => [profile.ticker, priceBars(dates, market, profile, gauss)]));
  const intraday = Object.fromEntries(PROFILES.map(profile => [profile.ticker, sessionBars(history[profile.ticker], profile.vol, gauss)]));
  const trades: AnalystTrade[] = [];

  dates.forEach((date, day) => {
    const count = 5 + Math.floor(random() * 8);
    for (let i = 0; i < count; i++) {
      const profile = PROFILES[Math.floor(random() * PROFILES.length)];
      const bars = history[profile.ticker];
      const bar = bars[day];
      const side = random() < 0.56 ? 'BUY' : 'SELL';
      const executionPrice = round(bar.low + (bar.high - bar.low) * random());
      const quantity = Math.round(Math.exp(2 + random() * 3));
      // Buys are marked to the latest close; sells are realized against an earlier entry.
      const reference = side === 'BUY'
        ? bars[bars.length - 1].close
        : bars[Math.max(0, day - 5 - Math.floor(random() * 55))].close;
      // 13:30–20:00 UTC regular session.
      const minutes = 810 + Math.floor(random() * 390);
      const hour = String(Math.floor(minutes / 60)).padStart(2, '0');
      const minute = String(minutes % 60).padStart(2, '0');

      trades.push({
        tradeId: `T-${100000 + trades.length}`,
        accountId: `BX-${1001 + Math.floor(random() * ACCOUNTS)}`,
        instrumentId: profile.instrumentId,
        ticker: profile.ticker,
        name: profile.name,
        side,
        quantity,
        executionPrice,
        tradeValue: round(executionPrice * quantity),
        executedAt: `${date}T${hour}:${minute}:00Z`,
        pnl: round((side === 'BUY' ? reference - executionPrice : executionPrice - reference) * quantity)
      });
    }
  });

  return {
    asOf: dates[dates.length - 1],
    instruments: PROFILES.map(({ instrumentId, ticker, name, currentPrice }) => ({ instrumentId, ticker, name, currentPrice })),
    history,
    intraday,
    trades: trades.sort((left, right) => right.executedAt.localeCompare(left.executedAt))
  };
}

// Daily returns blend a shared market factor with stock-specific noise under clustered volatility.
function priceBars(dates: string[], market: number[], profile: Profile, gauss: () => number): PriceBar[] {
  let close = 100;
  let regime = 1;
  const raw = dates.map((date, day) => {
    regime = 0.94 * regime + 0.06 * Math.abs(gauss()) * 1.25;
    const vol = profile.vol * (0.6 + 0.5 * regime);
    const open = close * (1 + gauss() * vol * 0.12);
    close = open * (1 + profile.drift + (0.55 * market[day] + 0.83 * gauss()) * vol);
    const move = Math.abs(close / open - 1) / profile.vol;
    return {
      date,
      open,
      close,
      high: Math.max(open, close) * (1 + Math.abs(gauss()) * vol * 0.55),
      low: Math.min(open, close) * (1 - Math.abs(gauss()) * vol * 0.55),
      volume: Math.round(profile.volume * (0.7 + 0.3 * Math.abs(gauss()) + 0.35 * move))
    };
  });
  const scale = profile.currentPrice / close;
  return raw.map(bar => ({
    ...bar,
    open: round(bar.open * scale),
    high: round(bar.high * scale),
    low: round(bar.low * scale),
    close: round(bar.close * scale)
  }));
}

// Five-minute bars for the latest session; the daily bar's range is widened to contain them.
function sessionBars(history: PriceBar[], vol: number, gauss: () => number): PriceBar[] {
  const day = history[history.length - 1];
  const steps = 78;
  const walk = [0];
  for (let i = 1; i <= steps; i++) walk.push(walk[i - 1] + (gauss() * vol * day.open) / Math.sqrt(steps));
  const path = walk.map((value, i) => day.open + ((day.close - day.open) * i) / steps + value - (walk[steps] * i) / steps);
  const start = Date.parse(`${day.date}T13:30:00Z`);
  const bars = path.slice(1).map((close, i) => {
    const open = path[i];
    const wiggle = Math.abs(gauss()) * vol * 0.12 * day.open;
    const curve = 0.55 + 6.4 * (i / steps - 0.5) ** 2 + 0.3 * Math.abs(gauss());
    return {
      date: new Date(start + i * 300000).toISOString(),
      open: round(open),
      close: round(close),
      high: round(Math.max(open, close) + wiggle),
      low: round(Math.min(open, close) - wiggle),
      volume: Math.round((day.volume / steps) * curve)
    };
  });
  history[history.length - 1] = {
    ...day,
    high: Math.max(day.high, ...bars.map(bar => bar.high)),
    low: Math.min(day.low, ...bars.map(bar => bar.low))
  };
  return bars;
}

function sessionDates(today: Date, count: number): string[] {
  const cursor = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()));
  const dates: string[] = [];
  while (dates.length < count) {
    const weekday = cursor.getUTCDay();
    if (weekday !== 0 && weekday !== 6) dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return dates.reverse();
}

function seeded(seed: number): () => number {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
