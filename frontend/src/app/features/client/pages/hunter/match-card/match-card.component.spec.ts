import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatchCardComponent } from './match-card.component';
import { HunterMatch } from '../../../../../core/models/hunter.model';
import { CATALOG, match, spec } from '../../../../../testing/hunter-fixtures';

describe('MatchCardComponent', () => {
  let fixture: ComponentFixture<MatchCardComponent>;
  let element: HTMLElement;

  function render(value: HunterMatch, onWatchlist = false) {
    fixture = TestBed.createComponent(MatchCardComponent);
    fixture.componentRef.setInput('match', value);
    fixture.componentRef.setInput('catalog', CATALOG);
    fixture.componentRef.setInput('criteria', spec().criteria);
    fixture.componentRef.setInput('onWatchlist', onWatchlist);
    fixture.detectChanges();
    element = fixture.nativeElement;
  }

  beforeEach(() => {
    // These tests check English text, whatever language an earlier spec file left selected
    localStorage.setItem('app-language', 'en');
    TestBed.configureTestingModule({ imports: [MatchCardComponent] });
  });

  it('shows the score as text, up to three reasons and labelled estimates (U3)', () => {
    render(match());

    expect(element.querySelector('.score')?.textContent?.trim()).toBe('83% thesis match');
    expect(element.querySelectorAll('.why li').length).toBe(3);
    const estimate = element.querySelector('.estimate')?.textContent ?? '';
    expect(estimate).toContain('Gross margin ≈ 75%');
    expect(estimate).toContain('FY2026');
    expect(estimate).toContain('AI estimate');
  });

  it('says it matches all filters when there are no checks', () => {
    render(match({ matchScore: null, criteria: [] }));

    expect(element.querySelector('.score')?.textContent?.trim()).toBe('Matches all filters');
    expect(element.querySelector('.breakdown')).toBeNull();
  });

  it('shows each verdict next to its statement', () => {
    render(match());

    const breakdown = element.querySelector('.breakdown')?.textContent ?? '';
    expect(breakdown).toContain('Met');
    expect(breakdown).toContain('Sells products used to run AI data centers');
  });

  it('asks to toggle the watchlist and shows whether the stock is on it (U4)', () => {
    render(match(), false);
    const button = element.querySelector('.watch-button') as HTMLButtonElement;
    let toggles = 0;
    fixture.componentInstance.toggleWatchlist.subscribe(() => toggles++);

    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.getAttribute('aria-label')).toBe('Add NVDA to watchlist');
    button.click();
    expect(toggles).toBe(1);

    fixture.componentRef.setInput('onWatchlist', true);
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.textContent?.trim()).toBe('On watchlist ✓');
  });
});
