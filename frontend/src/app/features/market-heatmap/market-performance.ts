import { Injectable } from '@angular/core';
import { Observable, catchError, from, map, mergeMap, of, retry, shareReplay, tap, timer } from 'rxjs';
import { MarketDataService } from '../../core/services/market-data.service';
import { PriceBar, QuoteStatus } from '../../core/models/market.model';

export type PerformancePeriod = '1D' | '1W' | '1M';

// Largest calendar gap between a reference session and the date it stands in for
// (a weekend plus one market holiday). Larger gaps mean missing history.
const MAX_SESSION_GAP_DAYS = 4;
const HISTORY_PERIOD = '3mo';
const HISTORY_TTL_MS = 30 * 60 * 1000;
const HISTORY_CONCURRENCY = 4;

export interface QuoteInput {
  price: number | null;
  quoteTime: string | null;
  status: QuoteStatus;
}

/** Exchange-local (New York) calendar date of an ISO timestamp, as YYYY-MM-DD. */
export function marketDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-CA', { timeZone: 'America/New_York' });
}

/**
 * Percent change of the quote against a genuine daily close, or null when the
 * reference cannot be established reliably.
 *
 * Daily bars are dated in exchange-local time. The newest bar may be the current,
 * unfinished session, so 1D always compares against the last bar dated strictly
 * before the quote's session; 1W/1M use the last close on or before the target date.
 */
export function percentChange(quote: QuoteInput, bars: PriceBar[] | null | undefined, period: PerformancePeriod): number | null {
  if (quote.status === 'UNAVAILABLE' || quote.price === null || !quote.quoteTime || !bars?.length) return null;
  if (!Number.isFinite(quote.price) || quote.price <= 0) return null;

  const session = marketDate(quote.quoteTime);
  // A quote older than the newest daily bar is not the current price.
  if (session < bars[bars.length - 1].date) return null;

  const target = period === '1D' ? session : shiftDate(session, period === '1W' ? { days: -7 } : { months: -1 });
  if (period !== '1D' && bars[0].date > target) return null;

  const reference = [...bars].reverse().find(bar => (period === '1D' ? bar.date < target : bar.date <= target));
  if (!reference || !Number.isFinite(reference.close) || reference.close <= 0) return null;
  if (daysBetween(reference.date, target) > MAX_SESSION_GAP_DAYS) return null;

  return (quote.price / reference.close - 1) * 100;
}

/** Daily closes over the month before the quote's session, ending at the current price. */
export function sparkline(quote: QuoteInput, bars: PriceBar[] | null | undefined): number[] | null {
  if (quote.price === null || !quote.quoteTime || !bars?.length || quote.status === 'UNAVAILABLE') return null;
  const session = marketDate(quote.quoteTime);
  const from = shiftDate(session, { months: -1 });
  const closes = bars.filter(bar => bar.date >= from && bar.date < session).map(bar => bar.close);
  return closes.length >= 5 ? [...closes, quote.price] : null;
}

function shiftDate(date: string, by: { days?: number; months?: number }): string {
  const value = new Date(`${date}T00:00:00Z`);
  if (by.months) value.setUTCMonth(value.getUTCMonth() + by.months);
  if (by.days) value.setUTCDate(value.getUTCDate() + by.days);
  return value.toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000;
}

/** Shared, short-lived cache of daily history so revisits and refreshes do not refetch every ticker. */
@Injectable({ providedIn: 'root' })
export class HeatmapHistoryCache {
  private readonly entries = new Map<string, { loadedAt: number; bars$: Observable<PriceBar[] | null> }>();

  constructor(private market: MarketDataService) {}

  /** Emits [ticker, bars] as each history arrives, with at most HISTORY_CONCURRENCY requests in flight. */
  load(tickers: string[]): Observable<[string, PriceBar[] | null]> {
    return from(tickers).pipe(
      mergeMap(ticker => this.history(ticker).pipe(map(bars => [ticker, bars] as [string, PriceBar[] | null])), HISTORY_CONCURRENCY)
    );
  }

  private history(ticker: string): Observable<PriceBar[] | null> {
    const cached = this.entries.get(ticker);
    if (cached && Date.now() - cached.loadedAt < HISTORY_TTL_MS) return cached.bars$;

    const bars$ = this.market.getHistory(ticker, HISTORY_PERIOD).pipe(
      map(response => response.bars ?? []),
      retry({ count: 1, delay: () => timer(1500) }),
      catchError(() => of(null)),
      tap(bars => {
        if (bars === null) this.entries.delete(ticker);
      }),
      shareReplay(1)
    );
    this.entries.set(ticker, { loadedAt: Date.now(), bars$ });
    return bars$;
  }
}
