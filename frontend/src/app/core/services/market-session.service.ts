import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, switchMap, timer } from 'rxjs';
import { QuoteStatus } from '../models/market.model';
import { MarketDataService } from './market-data.service';

export type MarketState = 'open' | 'closed' | 'unavailable';

export interface MarketSession {
  state: MarketState;
  /** Shown under the indicator and used as the trade panel's blocking reason when closed. */
  detail: string;
}

// Exchanges the backend MarketSessionService has a trading schedule for (including its aliases).
const US_EXCHANGES = new Set(['NYSE', 'NASDAQ', 'NMS', 'NCM', 'NGM', 'NASDAQGS', 'NASDAQGM', 'NASDAQCM']);
const SCHEDULED_EXCHANGES = new Set([...US_EXCHANGES, 'LSE', 'NSE', 'NSI', 'BSE', 'CRYPTO', 'FOREX']);

const REFRESH_MS = 60_000;
const US_EARLY_CLOSE_MINUTES = 13 * 60;

const UNAVAILABLE: MarketSession = {
  state: 'unavailable',
  detail: "Trading hours couldn't be confirmed right now."
};

/**
 * The quote snapshot status is computed by the same backend rules that accept or reject trades
 * (exchange timezone, weekends, US holidays). The backend has no early-close calendar, so NYSE/Nasdaq
 * half days are applied here using New York time, never the browser's timezone.
 */
export function resolveMarketSession(status: QuoteStatus | null | undefined, exchangeId: string, now: Date): MarketSession {
  const exchange = (exchangeId || '').trim().toUpperCase();
  if (!SCHEDULED_EXCHANGES.has(exchange)) return UNAVAILABLE;

  const reopen = 'Orders can be placed when the market reopens for regular trading hours.';
  if (status === 'CLOSED') {
    return { state: 'closed', detail: `The ${exchange} market is closed. ${reopen}` };
  }
  if (status !== 'LIVE' && status !== 'STALE') return UNAVAILABLE;

  if (US_EXCHANGES.has(exchange) && isAfterUsEarlyClose(now)) {
    return { state: 'closed', detail: `The ${exchange} market closed early today at 1:00 PM ET. ${reopen}` };
  }
  return { state: 'open', detail: exchange === 'CRYPTO' ? 'Trades 24/7.' : 'Regular trading session.' };
}

function isAfterUsEarlyClose(now: Date): boolean {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/New_York',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      weekday: 'short',
      hour: 'numeric',
      minute: 'numeric',
      hourCycle: 'h23'
    }).formatToParts(now).map(p => [p.type, p.value])
  );
  const month = Number(parts['month']);
  const day = Number(parts['day']);
  const weekday = parts['weekday'];
  const minutes = Number(parts['hour']) * 60 + Number(parts['minute']);
  const monToThu = ['Mon', 'Tue', 'Wed', 'Thu'].includes(weekday);

  // NYSE 1:00 PM closes: July 3 and Christmas Eve when they fall Mon–Thu (on a Friday they are the
  // observed holiday instead), and the Friday after Thanksgiving (the fourth Thursday is the 22nd–28th).
  const earlyClose =
    (month === 7 && day === 3 && monToThu) ||
    (month === 12 && day === 24 && monToThu) ||
    (month === 11 && weekday === 'Fri' && day >= 23 && day <= 29);

  return earlyClose && minutes >= US_EARLY_CLOSE_MINUTES;
}

@Injectable({ providedIn: 'root' })
export class MarketSessionService {
  private readonly marketData = inject(MarketDataService);

  /** Emits the session now and every minute so the indicator flips at the open and close. */
  watch(ticker: string, exchangeId: string): Observable<MarketSession> {
    return timer(0, REFRESH_MS).pipe(
      switchMap(() =>
        this.marketData.getQuote(ticker).pipe(
          map(quote => resolveMarketSession(quote.status, exchangeId, new Date())),
          catchError(() => of(UNAVAILABLE))
        )
      )
    );
  }
}
