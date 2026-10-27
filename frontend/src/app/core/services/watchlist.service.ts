import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import { WatchlistItem } from '../models/hunter.model';
import { toHunterError } from './hunter.service';

/** The caller's watchlist, kept in a signal so the Hunter page and the Watchlist page agree. */
@Injectable({
  providedIn: 'root'
})
export class WatchlistService {
  private http = inject(HttpClient);

  readonly items = signal<WatchlistItem[]>([]);
  readonly count = computed(() => this.items().length);
  private readonly tickers = computed(() => new Set(this.items().map(item => item.ticker)));

  has(ticker: string): boolean {
    return this.tickers().has(ticker);
  }

  load(): Observable<WatchlistItem[]> {
    return this.http.get<WatchlistItem[]>('/api/watchlist').pipe(
      tap(items => this.items.set(items)),
      catchError(error => throwError(() => toHunterError(error)))
    );
  }

  add(ticker: string, sourceSpecId: string | null): Observable<WatchlistItem> {
    return this.http.put<WatchlistItem>(`/api/watchlist/${encodeURIComponent(ticker)}`, { sourceSpecId }).pipe(
      tap(item => this.items.update(items => [item, ...items.filter(existing => existing.ticker !== item.ticker)])),
      catchError(error => throwError(() => toHunterError(error)))
    );
  }

  remove(ticker: string): Observable<void> {
    return this.http.delete<void>(`/api/watchlist/${encodeURIComponent(ticker)}`).pipe(
      map(() => undefined),
      tap(() => this.items.update(items => items.filter(item => item.ticker !== ticker))),
      catchError(error => throwError(() => toHunterError(error)))
    );
  }
}
