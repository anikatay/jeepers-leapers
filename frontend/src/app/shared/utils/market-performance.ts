import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, from, map, mergeMap, of, retry, shareReplay, tap, throwError, timer } from 'rxjs';
import { MarketDataService } from '../../core/services/market-data.service';
import { HistoryResponse, PriceBar, QuoteStatus } from '../../core/models/market.model';

export type PerformancePeriod = '1D' | '1W' | '1M';

// Largest calendar gap between a reference session and the date it stands in for
// (a weekend plus one market holiday). Larger gaps mean missing history.
const MAX_SESSION_GAP_DAYS = 4;
const HISTORY_PERIOD = '1mo';
export const HISTORY_TTL_MS = 30 * 60 * 1000;
const HISTORY_BATCH_SIZE = 50;
const HISTORY_CONCURRENCY = 4;
// Status 0 is a network error; 503 is also what the backend returns while another request holds its fetch lock.
const RETRYABLE_STATUS = new Set([0, 429, 502, 503, 504]);
const RETRY_LIMIT = 3;
const RETRY_BASE_DELAY_MS = 1000;
const RETRY_MAX_DELAY_MS = 8000;
const MAX_RETRY_AFTER_MS = 30_000;
const FAILURE_TTL_MS = 2 * 60 * 1000;

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
  const orderedBars = bars
    .filter(bar => Number.isFinite(bar.close) && bar.close > 0 && Boolean(bar.date))
    .slice()
    .sort((a, b) => a.date.localeCompare(b.date));
  if (orderedBars.length === 0) return null;

  // A stale cached quote must not be presented as a current performance value.
  if (quote.status === 'STALE' && session < orderedBars[orderedBars.length - 1].date) return null;

  const target = period === '1D' ? session : shiftDate(session, period === '1W' ? { days: -7 } : { months: -1 });

  let reference = [...orderedBars].reverse().find(bar =>
    period === '1D' ? bar.date < target : bar.date <= target
  );
  if (!reference && period !== '1D') {
    // A one-week or one-month target can land before the first trading day in
    // the requested history window (for example, on a weekend).
    reference = orderedBars.find(bar => bar.date >= target);
  }
  if (!reference || Math.abs(daysBetween(reference.date, target)) > MAX_SESSION_GAP_DAYS) return null;

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

/**
 * Calendar arithmetic on YYYY-MM-DD dates. Month shifts clamp to the last day of the
 * target month (Mar 31 − 1 month = Feb 28/29) instead of overflowing into the next one.
 */
export function shiftDate(date: string, by: { days?: number; months?: number }): string {
  const [year, month, day] = date.split('-').map(Number);
  const months = year * 12 + (month - 1) + (by.months ?? 0);
  const targetYear = Math.floor(months / 12);
  const targetMonth = months - targetYear * 12;
  const lastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const value = new Date(Date.UTC(targetYear, targetMonth, Math.min(day, lastDay) + (by.days ?? 0)));
  return value.toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000;
}

/**
 * Delay before retrying a failed history request, or null when the failure is not transient
 * (e.g. 404 for a ticker without data) or the server asks for a longer wait than we will hold a slot for.
 * 503 covers the backend's per-ticker fetch lock and upstream Yahoo failures.
 */
export function retryDelay(error: unknown, attempt: number, random: () => number = Math.random): number | null {
  if (!(error instanceof HttpErrorResponse) || !RETRYABLE_STATUS.has(error.status)) return null;
  const retryAfter = parseRetryAfter(error.headers?.get('Retry-After'));
  if (retryAfter !== null) return retryAfter <= MAX_RETRY_AFTER_MS ? retryAfter : null;
  const ceiling = Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS * 2 ** (attempt - 1));
  return Math.round(ceiling / 2 + random() * (ceiling / 2));
}

/** Retry-After as milliseconds; accepts delta-seconds or an HTTP date. */
export function parseRetryAfter(value: string | null | undefined, now = Date.now()): number | null {
  if (!value?.trim()) return null;
  if (/^\d+$/.test(value.trim())) return Number(value.trim()) * 1000;
  const date = Date.parse(value);
  return Number.isNaN(date) ? null : Math.max(0, date - now);
}

/** How long a failed history stays cached before it may be requested again. */
function failureTtl(error: unknown): number {
  if (error instanceof HttpErrorResponse && error.status === 404) return HISTORY_TTL_MS;
  const retryAfter = error instanceof HttpErrorResponse ? parseRetryAfter(error.headers?.get('Retry-After')) : null;
  return Math.max(FAILURE_TTL_MS, retryAfter ?? 0);
}

/** Shared, short-lived cache of daily history so revisits and refreshes do not refetch every ticker. */
@Injectable({ providedIn: 'root' })
export class HeatmapHistoryCache {
  private readonly entries = new Map<string, { expiresAt: number; bars$: Observable<PriceBar[] | null> }>();

  constructor(private market: MarketDataService) {}

  /** Emits [ticker, bars] as each batch history response arrives. */
  load(tickers: string[]): Observable<[string, PriceBar[] | null]> {
    const uniqueTickers = [...new Set(tickers)];
    const missing = uniqueTickers.filter(ticker => {
      const cached = this.entries.get(ticker);
      return !cached || Date.now() >= cached.expiresAt;
    });

    for (let start = 0; start < missing.length; start += HISTORY_BATCH_SIZE) {
      this.createBatch(missing.slice(start, start + HISTORY_BATCH_SIZE));
    }

    return from(uniqueTickers).pipe(
      mergeMap(ticker => {
        const entry = this.entries.get(ticker);
        if (!entry) {
          return of<[string, PriceBar[] | null]>([ticker, null]);
        }
        return entry.bars$.pipe(map(bars => [ticker, bars] as [string, PriceBar[] | null]));
      }, HISTORY_CONCURRENCY)
    );
  }

  private createBatch(tickers: string[]): void {
    const batch$ = this.market.getHistoryBatch(tickers, HISTORY_PERIOD).pipe(
      retry({
        count: RETRY_LIMIT,
        delay: (error, attempt) => {
          const wait = retryDelay(error, attempt);
          return wait === null ? throwError(() => error) : timer(wait);
        }
      }),
      map(histories => ({ histories }) as HistoryBatchResult),
      catchError(error => of({ error } as HistoryBatchResult)),
      shareReplay(1)
    );

    tickers.forEach(ticker => {
      const entry = { expiresAt: Date.now() + HISTORY_TTL_MS, bars$: of<PriceBar[] | null>(null) };
      entry.bars$ = batch$.pipe(
        mergeMap(result => {
          if ('error' in result) {
            return throwError(() => result.error);
          }
          const history = result.histories[ticker];
          return of(history?.bars?.length ? history.bars : null);
        }),
        tap(() => {
          entry.expiresAt = Date.now() + HISTORY_TTL_MS;
        }),
        catchError(error => {
          entry.expiresAt = Date.now() + failureTtl(error);
          return of(null);
        }),
        shareReplay(1)
      );
      this.entries.set(ticker, entry);
    });
  }
}

type HistoryBatchResult =
  | { histories: Record<string, HistoryResponse> }
  | { error: unknown };
