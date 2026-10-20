import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, forkJoin, map, of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import {
  AnalysisError,
  PnlStatus,
  TradeAnalysis,
  TradeAnalysisContext,
  TradeAnalysisResult,
} from '../models/trade-analysis.model';

const DEFAULT_RETRY_AFTER_SECONDS = 60;

@Injectable({
  providedIn: 'root'
})
export class TradeAnalysisService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  /** Errors with an AnalysisError. A failed context request only makes the P&L status unknown. */
  analyze(tradeId: string): Observable<TradeAnalysis> {
    const url = `/api/trades/${encodeURIComponent(tradeId)}/analysis`;
    const headers = this.authHeaders();

    const result = this.http.post<TradeAnalysisResult>(url, null, { headers });
    const pnl = this.http.get<TradeAnalysisContext>(`${url}/context`, { headers }).pipe(
      map((context): PnlStatus => context.outcome
        ? { kind: 'calculated', asOf: context.outcome.asOf }
        : { kind: 'not-calculated' }),
      catchError(() => of<PnlStatus>({ kind: 'unknown' }))
    );

    return forkJoin({ result, pnl }).pipe(
      catchError(error => throwError(() => toAnalysisError(error)))
    );
  }

  // Only these endpoints get the token: the backend rejects any request carrying an expired
  // token, even on endpoints that need no login, so sending it app-wide would break other pages.
  private authHeaders(): HttpHeaders {
    const token = this.auth.token;
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}

function toAnalysisError(error: unknown): AnalysisError {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'unknown' };
  }
  switch (error.status) {
    case 401:
      return { kind: 'session-expired' };
    case 404:
      return { kind: 'not-found' };
    case 429:
      return { kind: 'rate-limited', retryAfterSeconds: retryAfterSeconds(error) };
    case 502:
      return { kind: 'unavailable' };
    default:
      return { kind: 'unknown' };
  }
}

function retryAfterSeconds(error: HttpErrorResponse): number {
  const seconds = Number.parseInt(error.headers.get('Retry-After') ?? '', 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : DEFAULT_RETRY_AFTER_SECONDS;
}
