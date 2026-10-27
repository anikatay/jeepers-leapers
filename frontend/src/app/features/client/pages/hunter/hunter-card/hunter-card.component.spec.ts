import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HunterCardComponent } from './hunter-card.component';
import { HunterSpec } from '../../../../../core/models/hunter.model';
import { CATALOG, spec } from '../../../../../testing/hunter-fixtures';

describe('HunterCardComponent', () => {
  let fixture: ComponentFixture<HunterCardComponent>;
  let element: HTMLElement;

  function render(value: HunterSpec, fieldErrors = [] as { field: string; message: string }[]) {
    fixture = TestBed.createComponent(HunterCardComponent);
    fixture.componentRef.setInput('spec', value);
    fixture.componentRef.setInput('catalog', CATALOG);
    fixture.componentRef.setInput('fieldErrors', fieldErrors);
    fixture.detectChanges();
    element = fixture.nativeElement;
  }

  const current = () => fixture.componentInstance.spec();

  beforeEach(() => {
    // These tests check English text, whatever language an earlier spec file left selected
    localStorage.setItem('app-language', 'en');
    TestBed.configureTestingModule({ imports: [HunterCardComponent] });
  });

  it('shows the filters, the checks and what was not used (U1)', () => {
    render(spec());

    expect(element.querySelector('[data-rule="q1"]')?.textContent).toContain('high-margin');
    expect((element.querySelector('[data-rule="s1"] textarea') as HTMLTextAreaElement).value)
      .toBe('Sells products used to run AI data centers');
    expect(element.querySelector('.not-used')?.textContent).toContain('pulled back more than 10% this month');
  });

  it('words a time-based phrase with the knowledge cutoff, not the server message (U2)', () => {
    render(spec());

    const note = element.querySelector('.not-used')?.textContent ?? '';
    expect(note).toContain("The AI's knowledge ends in May 2026");
    expect(note).not.toContain('from the server');
  });

  it('turns a rule off with its switch', () => {
    render(spec());
    const toggle = element.querySelector('[data-rule="q1"] [role="switch"]') as HTMLButtonElement;

    expect(toggle.getAttribute('aria-checked')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Use Gross margin');
    toggle.click();
    fixture.detectChanges();

    expect(current().quantRules[0].enabled).toBe(false);
    expect(toggle.getAttribute('aria-checked')).toBe('false');
  });

  it('edits thresholds and asks for an upper bound for between', () => {
    render(spec());
    const [, op] = Array.from(element.querySelectorAll('[data-rule="q1"] select')) as HTMLSelectElement[];
    const value = element.querySelector('[data-rule="q1"] input') as HTMLInputElement;

    value.value = '60';
    value.dispatchEvent(new Event('change'));
    op.value = 'between';
    op.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(current().quantRules[0]).toEqual(expect.objectContaining({ op: 'between', value: 60, value2: 60 }));
    expect(element.querySelectorAll('[data-rule="q1"] input').length).toBe(2);
  });

  it('adds filters and checks with unique IDs', () => {
    render(spec());
    const add = element.querySelector('select.add') as HTMLSelectElement;

    add.value = 'peRatio';
    add.dispatchEvent(new Event('change'));
    add.value = 'sector';
    add.dispatchEvent(new Event('change'));
    (element.querySelector('.link-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(current().quantRules.map(rule => rule.id)).toEqual(['q1', 'q2']);
    expect(current().sectorRule?.values).toEqual([]);
    expect(current().criteria.map(criterion => criterion.id)).toEqual(['s1', 's2']);
    expect(add.value).toBe('');
  });

  it('shows each field error under its rule', () => {
    render(spec(), [{ field: 'criteria[0].statement', message: 'is required' }]);

    expect(element.querySelector('[data-rule="s1"] .error')?.textContent).toContain('is required');
  });
});
