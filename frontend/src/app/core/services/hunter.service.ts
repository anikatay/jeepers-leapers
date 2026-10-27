import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import {
  HunterCatalog,
  HunterError,
  HunterRun,
  HunterSpec,
  HunterSpecSummary,
} from '../models/hunter.model';

const BASE = '/api/hunter';
const DEFAULT_RETRY_AFTER_SECONDS = 60;

/** Every call errors with a HunterError. ApiInterceptor adds the login token. */
@Injectable({
  providedIn: 'root'
})
export class HunterService {
  private http = inject(HttpClient);

  catalog(): Observable<HunterCatalog> {
    return this.http.get<HunterCatalog>(`${BASE}/catalog`).pipe(catchError(toError));
  }

  draft(prompt: string): Observable<HunterSpec> {
    return this.http.post<HunterSpec>(`${BASE}/specs/draft`, { prompt }).pipe(catchError(toError));
  }

  listSpecs(): Observable<HunterSpecSummary[]> {
    return this.http.get<HunterSpecSummary[]>(`${BASE}/specs`).pipe(catchError(toError));
  }

  getSpec(id: string): Observable<HunterSpec> {
    return this.http.get<HunterSpec>(`${BASE}/specs/${encodeURIComponent(id)}`).pipe(catchError(toError));
  }

  /** Creates the spec when it has no id yet, otherwise replaces it. */
  saveSpec(spec: HunterSpec): Observable<HunterSpec> {
    const request = spec.id
      ? this.http.put<HunterSpec>(`${BASE}/specs/${encodeURIComponent(spec.id)}`, spec)
      : this.http.post<HunterSpec>(`${BASE}/specs`, spec);
    return request.pipe(catchError(toError));
  }

  deleteSpec(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/specs/${encodeURIComponent(id)}`).pipe(catchError(toError));
  }

  run(id: string): Observable<HunterRun> {
    return this.http.post<HunterRun>(`${BASE}/specs/${encodeURIComponent(id)}/runs`, null).pipe(catchError(toError));
  }

  /** null when the hunter has never been run. */
  latestRun(id: string): Observable<HunterRun | null> {
    return this.http.get<HunterRun>(`${BASE}/specs/${encodeURIComponent(id)}/runs/latest`).pipe(
      catchError(error => error instanceof HttpErrorResponse && error.status === 404 ? of(null) : toError(error))
    );
  }
}

function toError(error: unknown): Observable<never> {
  return throwError(() => toHunterError(error));
}

export function toHunterError(error: unknown): HunterError {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'unknown' };
  }
  switch (error.status) {
    case 400:
      return { kind: 'invalid', fieldErrors: error.error?.fieldErrors ?? [] };
    case 401:
      return { kind: 'session-expired' };
    case 404:
      return { kind: 'not-found' };
    case 409:
      return { kind: 'limit-reached' };
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
