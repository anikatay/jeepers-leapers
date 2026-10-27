import { TestBed } from '@angular/core/testing';
import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TradeAnalysisService } from './trade-analysis.service';
import { AuthService } from './auth.service';
import { AnalysisError, TradeAnalysis, TradeAnalysisResult } from '../models/trade-analysis.model';

const TRADE_ID = 'c3000000-0000-0000-0000-000000000007';
const REVIEW_URL = `/api/trades/${TRADE_ID}/analysis`;
const CONTEXT_URL = `${REVIEW_URL}/context`;
const RESULT: TradeAnalysisResult = {
  verdict: 'GOOD',
  summary: 'You sold at a gain.',
  strengths: ['Took profit'],
  concerns: [],
  suggestions: ['Keep sizes similar'],
};

describe('TradeAnalysisService', () => {
  let service: TradeAnalysisService;
  let http: HttpTestingController;
  let token: string | null;

  beforeEach(() => {
    token = 'test-token';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { get token() { return token; } } },
      ],
    });
    service = TestBed.inject(TradeAnalysisService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function analyze() {
    const outcome: { value?: TradeAnalysis; error?: AnalysisError } = {};
    service.analyze(TRADE_ID).subscribe({
      next: value => (outcome.value = value),
      error: error => (outcome.error = error),
    });
    return outcome;
  }

  // U3
  it('sends the stored token on both the review and the context request', () => {
    analyze();

    const review = http.expectOne(REVIEW_URL);
    const context = http.expectOne(CONTEXT_URL);
    expect(review.request.method).toBe('POST');
    expect(context.request.method).toBe('GET');
    expect(review.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(context.request.headers.get('Authorization')).toBe('Bearer test-token');
    review.flush(RESULT);
    context.flush({ outcome: null });
  });

  it('sends no Authorization header when there is no stored token', () => {
    token = null;
    analyze();

    const review = http.expectOne(REVIEW_URL);
    expect(review.request.headers.has('Authorization')).toBe(false);
    review.flush(RESULT);
    http.expectOne(CONTEXT_URL).flush({ outcome: null });
  });

  // U5
  it('combines the review with the time the P&L was calculated', () => {
    const outcome = analyze();

    http.expectOne(REVIEW_URL).flush(RESULT);
    http.expectOne(CONTEXT_URL).flush({ outcome: { asOf: '2026-10-20T14:10:00Z', realizedPnl: 68 } });

    expect(outcome.value).toEqual({ result: RESULT, pnl: { kind: 'calculated', asOf: '2026-10-20T14:10:00Z' } });
  });

  // U6
  it('reports P&L as not calculated when the context has no outcome', () => {
    const outcome = analyze();

    http.expectOne(REVIEW_URL).flush(RESULT);
    http.expectOne(CONTEXT_URL).flush({ outcome: null });

    expect(outcome.value?.pnl).toEqual({ kind: 'not-calculated' });
  });

  // U6
  it('still returns the review when the context request fails', () => {
    const outcome = analyze();

    http.expectOne(REVIEW_URL).flush(RESULT);
    http.expectOne(CONTEXT_URL).flush(null, { status: 500, statusText: 'Server Error' });

    expect(outcome.value).toEqual({ result: RESULT, pnl: { kind: 'unknown' } });
  });

  // U8, U9, U10
  it.each([
    [401, undefined, { kind: 'session-expired' }],
    [404, undefined, { kind: 'not-found' }],
    [429, '41', { kind: 'rate-limited', retryAfterSeconds: 41 }],
    [429, undefined, { kind: 'rate-limited', retryAfterSeconds: 60 }],
    [429, 'soon', { kind: 'rate-limited', retryAfterSeconds: 60 }],
    [502, undefined, { kind: 'unavailable' }],
    [500, undefined, { kind: 'unknown' }],
  ] as const)('maps a %s review response (Retry-After %s) to %o', (status, retryAfter, expected) => {
    const outcome = analyze();

    const headers = retryAfter ? new HttpHeaders({ 'Retry-After': retryAfter }) : undefined;
    const context = http.expectOne(CONTEXT_URL);
    http.expectOne(REVIEW_URL).flush({ code: 'X' }, { status, statusText: 'Error', headers });

    expect(context.cancelled).toBe(true);

    expect(outcome.error).toEqual(expected);
  });

  // U10
  it('maps a request that gets no response to an unknown error', () => {
    const outcome = analyze();

    const context = http.expectOne(CONTEXT_URL);
    http.expectOne(REVIEW_URL).error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(context.cancelled).toBe(true);

    expect(outcome.error).toEqual({ kind: 'unknown' });
  });
});
