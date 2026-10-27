import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { HunterPageComponent } from './hunter-page.component';
import { CATALOG, match, run, spec } from '../../../../testing/hunter-fixtures';

describe('HunterPageComponent', () => {
  let fixture: ComponentFixture<HunterPageComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    // These tests check English text, whatever language an earlier spec file left selected
    localStorage.setItem('app-language', 'en');
    TestBed.configureTestingModule({
      imports: [HunterPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HunterPageComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();

    http.expectOne('/api/hunter/catalog').flush(CATALOG);
    http.expectOne('/api/hunter/specs').flush([]);
    http.expectOne('/api/watchlist').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function typeIdea(text: string) {
    const prompt = element.querySelector('#hunter-prompt') as HTMLTextAreaElement;
    prompt.value = text;
    prompt.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(element.querySelectorAll('button')).find(b => b.textContent?.trim() === label) as HTMLButtonElement;
  }

  function buildDraft() {
    typeIdea('high-margin AI infrastructure');
    button('Build hunter').click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Reading your idea…');
    const draft = http.expectOne({ method: 'POST', url: '/api/hunter/specs/draft' });
    expect(draft.request.body).toEqual({ prompt: 'high-margin AI infrastructure' });
    draft.flush(spec());
    fixture.detectChanges();
  }

  function runHunter(result = run()) {
    button('Run hunter').click();
    fixture.detectChanges();
    http.expectOne({ method: 'POST', url: '/api/hunter/specs' }).flush(spec({ id: 'spec-1' }));
    http.expectOne({ method: 'POST', url: '/api/hunter/specs/spec-1/runs' }).flush(result);
    http.expectOne('/api/hunter/specs').flush([]);
    fixture.detectChanges();
  }

  it('turns an idea into a card of rules (U1)', () => {
    buildDraft();

    expect(element.querySelector('app-hunter-card')).not.toBeNull();
    expect(element.querySelector('[data-rule="q1"]')).not.toBeNull();
  });

  it('saves, runs and shows the matches with the AI-knowledge badge and disclaimer (U3)', () => {
    buildDraft();
    runHunter(run({ matches: [match(), match({ ticker: 'ANET', companyName: 'Arista Networks', matchScore: 0 })] }));

    const results = element.querySelector('.results')?.textContent ?? '';
    expect(results).toContain('Educational candidate matches');
    expect(results).toContain('AI knowledge · up to May 2026 · not verified');
    expect(results).toContain('not a recommendation to buy, sell or hold');
    expect(element.querySelectorAll('.match-grid app-match-card').length).toBe(2);
    expect(element.querySelector('.zero-group summary')?.textContent).toContain('(1)');
  });

  it('says when the saved run was reused (U5)', () => {
    buildDraft();
    runHunter(run({ reused: true }));

    expect(element.querySelector('.results')?.textContent).toContain('Same rules as your run on');
  });

  it('shows the wait after too many requests (U6)', () => {
    buildDraft();
    button('Run hunter').click();
    http.expectOne({ method: 'POST', url: '/api/hunter/specs' }).flush(spec({ id: 'spec-1' }));
    http.expectOne('/api/hunter/specs/spec-1/runs').flush({}, { status: 429, statusText: 'x', headers: { 'Retry-After': '41' } });
    http.expectOne('/api/hunter/specs').flush([]);
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Too many requests. Try again in 41 seconds.');
  });

  it('marks invalid rules after a 400', () => {
    buildDraft();
    button('Run hunter').click();
    http.expectOne({ method: 'POST', url: '/api/hunter/specs' }).flush(
      { code: 'VALIDATION_ERROR', fieldErrors: [{ field: 'quantRules[0].value', message: 'is required' }] },
      { status: 400, statusText: 'Bad Request' });
    http.expectOne('/api/hunter/specs').flush([]);
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Some rules need fixing');
    expect(element.querySelector('[data-rule="q1"] .error')?.textContent).toContain('is required');
  });

  it('adds a match to the watchlist from its card (U4)', () => {
    buildDraft();
    runHunter();

    (element.querySelector('.watch-button') as HTMLButtonElement).click();
    const add = http.expectOne({ method: 'PUT', url: '/api/watchlist/NVDA' });
    expect(add.request.body).toEqual({ sourceSpecId: 'spec-1' });
    add.flush({ ticker: 'NVDA', companyName: 'Nvidia', gicsSector: 'Information Technology',
      addedAt: '2026-10-23T12:00:00Z', sourceSpec: { id: 'spec-1', name: 'AI infrastructure' } });
    fixture.detectChanges();

    expect(element.querySelector('.watch-button')?.getAttribute('aria-pressed')).toBe('true');
    expect(element.querySelector('.page-head .link')?.textContent).toContain('View watchlist (1)');
  });
});
