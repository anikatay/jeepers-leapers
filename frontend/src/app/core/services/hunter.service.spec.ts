import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HunterService } from './hunter.service';
import { WatchlistService } from './watchlist.service';
import { HunterError } from '../models/hunter.model';
import { spec } from '../../testing/hunter-fixtures';

describe('HunterService', () => {
  let service: HunterService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(HunterService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a new spec and replaces a saved one', () => {
    service.saveSpec(spec()).subscribe();
    expect(http.expectOne({ method: 'POST', url: '/api/hunter/specs' }).request.body.name).toBe('AI infrastructure');

    service.saveSpec(spec({ id: 'spec-1' })).subscribe();
    http.expectOne({ method: 'PUT', url: '/api/hunter/specs/spec-1' }).flush(spec({ id: 'spec-1' }));
  });

  it('reports a hunter that was never run as null', () => {
    let result: unknown = 'unset';
    service.latestRun('spec-1').subscribe(run => result = run);

    http.expectOne('/api/hunter/specs/spec-1/runs/latest').flush({ code: 'RESOURCE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect(result).toBeNull();
  });

  it.each([
    [400, {}, 'invalid'],
    [401, {}, 'session-expired'],
    [404, {}, 'not-found'],
    [409, {}, 'limit-reached'],
    [502, {}, 'unavailable'],
    [500, {}, 'unknown'],
  ])('maps HTTP %i to an error of kind %s', (status, headers, kind) => {
    let error: HunterError | undefined;
    service.run('spec-1').subscribe({ error: e => error = e });

    http.expectOne('/api/hunter/specs/spec-1/runs').flush({}, { status, statusText: 'x', headers });

    expect(error?.kind).toBe(kind);
  });

  it('passes on field errors and the Retry-After wait', () => {
    let invalid: HunterError | undefined;
    let limited: HunterError | undefined;
    let limitedWithoutHeader: HunterError | undefined;
    service.saveSpec(spec()).subscribe({ error: e => invalid = e });
    service.draft('idea').subscribe({ error: e => limited = e });
    service.run('spec-1').subscribe({ error: e => limitedWithoutHeader = e });

    http.expectOne('/api/hunter/specs').flush(
      { code: 'VALIDATION_ERROR', fieldErrors: [{ field: 'criteria[0].statement', message: 'is required' }] },
      { status: 400, statusText: 'Bad Request' });
    http.expectOne('/api/hunter/specs/draft').flush({}, { status: 429, statusText: 'x', headers: { 'Retry-After': '41' } });
    http.expectOne('/api/hunter/specs/spec-1/runs').flush({}, { status: 429, statusText: 'x' });

    expect(invalid?.fieldErrors?.[0].field).toBe('criteria[0].statement');
    expect(limited?.retryAfterSeconds).toBe(41);
    expect(limitedWithoutHeader?.retryAfterSeconds).toBe(60);
  });

  it('reports a network failure as unknown', () => {
    let error: HunterError | undefined;
    service.catalog().subscribe({ error: e => error = e });

    http.expectOne('/api/hunter/catalog').error(new ProgressEvent('error'));

    expect(error?.kind).toBe('unknown');
  });
});

describe('WatchlistService', () => {
  let watchlist: WatchlistService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    watchlist = TestBed.inject(WatchlistService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps its list in step with adds and removals', () => {
    watchlist.load().subscribe();
    http.expectOne('/api/watchlist').flush([
      { ticker: 'AAPL', companyName: 'Apple Inc.', gicsSector: 'Information Technology', addedAt: '2026-10-01T00:00:00Z', sourceSpec: null }
    ]);
    expect(watchlist.count()).toBe(1);

    watchlist.add('NVDA', 'spec-1').subscribe();
    const add = http.expectOne({ method: 'PUT', url: '/api/watchlist/NVDA' });
    expect(add.request.body).toEqual({ sourceSpecId: 'spec-1' });
    add.flush({ ticker: 'NVDA', companyName: 'Nvidia', gicsSector: 'Information Technology', addedAt: '2026-10-23T12:00:00Z',
      sourceSpec: { id: 'spec-1', name: 'AI infrastructure' } });
    expect(watchlist.has('NVDA')).toBe(true);
    expect(watchlist.items()[0].ticker).toBe('NVDA');

    watchlist.remove('NVDA').subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/watchlist/NVDA' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(watchlist.has('NVDA')).toBe(false);
    expect(watchlist.count()).toBe(1);
  });

  it('reports a full watchlist as limit-reached', () => {
    let error: HunterError | undefined;
    watchlist.add('NVDA', null).subscribe({ error: e => error = e });

    http.expectOne('/api/watchlist/NVDA').flush({ code: 'LIMIT_REACHED' }, { status: 409, statusText: 'Conflict' });

    expect(error?.kind).toBe('limit-reached');
    expect(watchlist.count()).toBe(0);
  });
});
