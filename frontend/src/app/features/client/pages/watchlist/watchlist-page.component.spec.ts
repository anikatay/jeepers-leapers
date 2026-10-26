import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { WatchlistPageComponent } from './watchlist-page.component';
import { CATALOG } from '../../../../testing/hunter-fixtures';

describe('WatchlistPageComponent', () => {
  let fixture: ComponentFixture<WatchlistPageComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WatchlistPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WatchlistPageComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function load(items: unknown[]) {
    http.expectOne('/api/watchlist').flush(items);
    http.expectOne('/api/hunter/catalog').flush(CATALOG);
    fixture.detectChanges();
  }

  it('lists saved stocks with the hunter that found them, and removes one (U4)', () => {
    load([
      { ticker: 'NVDA', companyName: 'Nvidia', gicsSector: 'Information Technology', addedAt: '2026-10-23T12:00:00Z',
        sourceSpec: { id: 'spec-1', name: 'AI infrastructure' } },
      { ticker: 'GONE', companyName: null, gicsSector: null, addedAt: '2026-10-01T12:00:00Z', sourceSpec: null },
    ]);

    const rows = element.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Nvidia');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toContain('spec=spec-1');
    expect(rows[1].textContent).toContain('—');
    expect(element.querySelector('.disclaimer')?.textContent).toContain('up to May 2026');

    (rows[0].querySelector('.remove-button') as HTMLButtonElement).click();
    http.expectOne({ method: 'DELETE', url: '/api/watchlist/NVDA' }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(element.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('points an empty watchlist to Hunter', () => {
    load([]);

    expect(element.querySelector('.empty')?.textContent).toContain('Your watchlist is empty.');
    expect(element.querySelector('table')).toBeNull();
  });
});
