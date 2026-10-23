import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, defer, firstValueFrom, of, throwError, toArray } from 'rxjs';
import { HistoryResponse, PriceBar } from '../../core/models/market.model';
import { MarketDataService } from '../../core/services/market-data.service';
import { HeatmapHistoryCache, QuoteInput, parseRetryAfter, percentChange, retryDelay, shiftDate } from './market-performance';

function bar(date: string, close: number): PriceBar {
  return { date, open: close, high: close, low: close, close, volume: 0 };
}

// 11:00 ET on Tuesday 2026-03-31.
const quote: QuoteInput = { price: 150, quoteTime: '2026-03-31T15:00:00Z', status: 'LIVE' };

const bars = [
  bar('2026-02-26', 90),
  bar('2026-02-27', 100),
  bar('2026-03-02', 110),
  bar('2026-03-03', 120),
  bar('2026-03-24', 125),
  bar('2026-03-30', 130),
  bar('2026-03-31', 140)
];

function httpError(status: number, retryAfter?: string): HttpErrorResponse {
  return new HttpErrorResponse({ status, headers: retryAfter ? new HttpHeaders({ 'Retry-After': retryAfter }) : undefined });
}

describe('shiftDate', () => {
  it('clamps month shifts to the last day of a shorter target month', () => {
    expect(shiftDate('2026-03-31', { months: -1 })).toBe('2026-02-28');
    expect(shiftDate('2026-03-30', { months: -1 })).toBe('2026-02-28');
    expect(shiftDate('2026-03-29', { months: -1 })).toBe('2026-02-28');
    expect(shiftDate('2024-03-31', { months: -1 })).toBe('2024-02-29');
    expect(shiftDate('2026-05-31', { months: -1 })).toBe('2026-04-30');
  });

  it('keeps ordinary month and day shifts unchanged', () => {
    expect(shiftDate('2026-03-15', { months: -1 })).toBe('2026-02-15');
    expect(shiftDate('2026-01-31', { months: -1 })).toBe('2025-12-31');
    expect(shiftDate('2026-03-03', { days: -7 })).toBe('2026-02-24');
  });
});

describe('percentChange', () => {
  it('measures 1M from the last close on or before the clamped month-end target', () => {
    // Target is Sat 2026-02-28, so the reference is Fri 2026-02-27, not early March.
    expect(percentChange(quote, bars, '1M')).toBeCloseTo(50);
  });

  it('measures 1D from the previous session, ignoring the current partial bar', () => {
    expect(percentChange(quote, bars, '1D')).toBeCloseTo((150 / 130 - 1) * 100);
  });

  it('measures 1W from the close seven calendar days earlier', () => {
    expect(percentChange(quote, bars, '1W')).toBeCloseTo(20);
  });

  it('returns null when history is missing or empty', () => {
    expect(percentChange(quote, null, '1D')).toBeNull();
    expect(percentChange(quote, undefined, '1M')).toBeNull();
    expect(percentChange(quote, [], '1W')).toBeNull();
  });

  it('returns null when history does not reach back to the target date', () => {
    expect(percentChange(quote, bars.slice(2), '1M')).toBeNull();
  });

  it('returns null when the nearest reference close is too far from the target', () => {
    const sparse = [bar('2026-02-20', 100), bar('2026-03-30', 130)];
    expect(percentChange(quote, sparse, '1M')).toBeNull();
  });

  it('returns null for a quote older than the latest daily bar', () => {
    const stale: QuoteInput = { ...quote, quoteTime: '2026-03-27T15:00:00Z' };
    expect(percentChange(stale, bars, '1D')).toBeNull();
  });

  it('returns null for unavailable or missing prices', () => {
    expect(percentChange({ ...quote, status: 'UNAVAILABLE' }, bars, '1D')).toBeNull();
    expect(percentChange({ ...quote, price: null }, bars, '1D')).toBeNull();
    expect(percentChange({ ...quote, quoteTime: null }, bars, '1D')).toBeNull();
  });
});

describe('retryDelay', () => {
  it('backs off exponentially with jitter for transient failures', () => {
    expect(retryDelay(httpError(503), 1, () => 0)).toBe(500);
    expect(retryDelay(httpError(503), 1, () => 1)).toBe(1000);
    expect(retryDelay(httpError(502), 3, () => 1)).toBe(4000);
    expect(retryDelay(httpError(0), 10, () => 1)).toBe(8000);
  });

  it('honors Retry-After within the limit and gives up beyond it', () => {
    expect(retryDelay(httpError(429, '2'), 1)).toBe(2000);
    expect(retryDelay(httpError(503, '180'), 1)).toBeNull();
  });

  it('does not retry permanent failures', () => {
    expect(retryDelay(httpError(404), 1)).toBeNull();
    expect(retryDelay(httpError(400), 1)).toBeNull();
    expect(retryDelay(new Error('boom'), 1)).toBeNull();
  });

  it('parses delta-seconds and HTTP-date Retry-After values', () => {
    const now = Date.parse('2026-10-23T15:00:00Z');
    expect(parseRetryAfter('5', now)).toBe(5000);
    expect(parseRetryAfter('Fri, 23 Oct 2026 15:00:10 GMT', now)).toBe(10_000);
    expect(parseRetryAfter('soon', now)).toBeNull();
    expect(parseRetryAfter(null, now)).toBeNull();
  });
});

describe('HeatmapHistoryCache', () => {
  afterEach(() => vi.useRealTimers());

  // Like HttpClient, every subscription (including each retry) sends a new request.
  function cacheWith(responses: Array<() => Observable<HistoryResponse>>) {
    const request = vi.fn(() => (responses.shift() ?? (() => throwError(() => httpError(500))))());
    const getHistory = () => defer(request);
    return { cache: new HeatmapHistoryCache({ getHistory } as unknown as MarketDataService), getHistory: request };
  }

  it('retries a fetch-lock conflict and returns the real bars', async () => {
    vi.useFakeTimers();
    const { cache, getHistory } = cacheWith([
      () => throwError(() => httpError(503)),
      () => throwError(() => httpError(503)),
      () => of({ ticker: 'AAPL', period: '3mo', bars })
    ]);

    const result = firstValueFrom(cache.load(['AAPL']).pipe(toArray()));
    await vi.advanceTimersByTimeAsync(10_000);

    expect(await result).toEqual([['AAPL', bars]]);
    expect(getHistory).toHaveBeenCalledTimes(3);
  });

  it('does not keep requesting a ticker without history', async () => {
    const { cache, getHistory } = cacheWith([() => throwError(() => httpError(404))]);

    expect(await firstValueFrom(cache.load(['XYZ']).pipe(toArray()))).toEqual([['XYZ', null]]);
    expect(await firstValueFrom(cache.load(['XYZ']).pipe(toArray()))).toEqual([['XYZ', null]]);
    expect(getHistory).toHaveBeenCalledTimes(1);
  });

  it('stops after the retry limit for repeated transient failures', async () => {
    vi.useFakeTimers();
    const unavailable = () => throwError(() => httpError(503));
    const { cache, getHistory } = cacheWith([unavailable, unavailable, unavailable, unavailable, unavailable]);

    const result = firstValueFrom(cache.load(['MSFT']).pipe(toArray()));
    await vi.advanceTimersByTimeAsync(60_000);

    expect(await result).toEqual([['MSFT', null]]);
    expect(getHistory).toHaveBeenCalledTimes(4);

    expect(await firstValueFrom(cache.load(['MSFT']).pipe(toArray()))).toEqual([['MSFT', null]]);
    expect(getHistory).toHaveBeenCalledTimes(4);
  });

  it('does not retry non-transient server errors', async () => {
    const { cache, getHistory } = cacheWith([]);

    expect(await firstValueFrom(cache.load(['MSFT']).pipe(toArray()))).toEqual([['MSFT', null]]);
    expect(getHistory).toHaveBeenCalledTimes(1);
  });
});
