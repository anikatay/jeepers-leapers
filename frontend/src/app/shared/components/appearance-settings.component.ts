import { Component, inject, input } from '@angular/core';
import { AppearanceService, TEXT_SIZES, THEMES } from '../../core/services/appearance.service';
import { TranslatePipe } from '../../core/pipes/translate.pipe';

let nextId = 0;

/** Appearance & Accessibility options shown in the shared header appearance dropdown. */
@Component({
  selector: 'app-appearance-settings',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    @if (showMode()) {
      <fieldset class="group">
        <legend>{{ 'appearance.mode' | translate }}</legend>
        <div class="segment">
          <label class="seg" [class.checked]="!appearance.dark()">
            <input type="radio" [name]="id + '-mode'" [checked]="!appearance.dark()" (change)="appearance.dark.set(false)" />
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4.5" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
            {{ 'appearance.light' | translate }}
          </label>
          <label class="seg" [class.checked]="appearance.dark()">
            <input type="radio" [name]="id + '-mode'" [checked]="appearance.dark()" (change)="appearance.dark.set(true)" />
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
            {{ 'appearance.dark' | translate }}
          </label>
        </div>
      </fieldset>
    }

    <fieldset class="group">
      <legend>{{ 'appearance.color_theme' | translate }}</legend>
      <div class="themes">
        @for (theme of themes; track theme.id) {
          <label class="theme" [class.checked]="appearance.theme() === theme.id">
            <input type="radio" [name]="id + '-theme'" [checked]="appearance.theme() === theme.id"
                   (change)="appearance.setTheme(theme.id)" />
            <span class="swatch" aria-hidden="true"
                  [style.background]="'linear-gradient(135deg, ' + theme.swatch[0] + ' 50%, ' + theme.swatch[1] + ' 50%)'"></span>
            <span class="theme-text">
              {{ theme.label }}
              @if (theme.accessible) { <small>{{ 'appearance.accessible' | translate }}</small> }
            </span>
          </label>
        }
      </div>
    </fieldset>

    <fieldset class="group">
      <legend>{{ 'appearance.text_size' | translate }}</legend>
      <div class="segment sizes">
        @for (size of textSizes; track size.id; let i = $index) {
          <label class="seg" [class.checked]="appearance.textSize() === size.id">
            <input type="radio" [name]="id + '-size'" [checked]="appearance.textSize() === size.id"
                   (change)="appearance.setTextSize(size.id)" />
            <span class="sample" aria-hidden="true" [style.font-size.em]="0.8 + i * 0.15">Aa</span>
            {{ (size.id === 'x-large' ? 'appearance.x_large' : 'appearance.' + size.id) | translate }}
          </label>
        }
      </div>
      <p class="hint">{{ 'appearance.hint' | translate }}</p>
    </fieldset>
  `,
  styles: `
    :host { display: grid; gap: 1.25rem; color: var(--foreground); }

    .group { border: 0; margin: 0; padding: 0; min-width: 0; display: grid; gap: 0.5rem; }
    legend { padding: 0; margin-bottom: 0.5rem; font-size: 0.8125rem; font-weight: 700; }

    input[type='radio'] {
      position: absolute;
      opacity: 0;
      width: 1px;
      height: 1px;
      margin: 0;
      pointer-events: none;
    }

    .segment {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
      padding: 0.25rem;
      border-radius: 0.75rem;
      background: var(--secondary);
      border: 1px solid var(--border);
    }

    .seg {
      position: relative;
      flex: 1 1 6rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.4rem;
      min-height: 2.5rem;
      padding: 0.4rem 0.6rem;
      border-radius: 0.55rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--muted-foreground);
      cursor: pointer;
      text-align: center;
    }

    .sizes .seg { flex-direction: column; gap: 0.1rem; flex-basis: 5rem; }
    .sample { font-family: 'DM Sans', sans-serif; font-weight: 700; line-height: 1; }

    .seg:hover:not(.checked) { background: var(--card); color: var(--foreground); }
    .seg.checked { background: var(--primary); color: var(--primary-foreground); }

    svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; flex-shrink: 0; }

    .themes { display: grid; grid-template-columns: repeat(auto-fill, minmax(8.5rem, 1fr)); gap: 0.5rem; }

    .theme {
      position: relative;
      display: flex;
      align-items: center;
      gap: 0.6rem;
      min-height: 2.75rem;
      padding: 0.5rem 0.65rem;
      border: 1.5px solid var(--border);
      border-radius: 0.75rem;
      background: var(--card);
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
    }

    .theme:hover { background: var(--secondary); }
    .theme.checked { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }

    .swatch {
      width: 1.5rem;
      height: 1.5rem;
      border-radius: 50%;
      flex-shrink: 0;
      border: 1px solid color-mix(in srgb, var(--foreground) 20%, transparent);
    }

    .theme-text { display: grid; min-width: 0; overflow-wrap: anywhere; }
    small { font-size: 0.6875rem; font-weight: 500; color: var(--muted-foreground); }

    .seg:has(input:focus-visible),
    .theme:has(input:focus-visible) { outline: 2px solid var(--ring); outline-offset: 2px; }

    .hint { margin: 0; font-size: 0.75rem; color: var(--muted-foreground); }
  `
})
export class AppearanceSettingsComponent {
  readonly appearance = inject(AppearanceService);
  readonly showMode = input(true);
  readonly themes = THEMES;
  readonly textSizes = TEXT_SIZES;
  readonly id = `appearance-${nextId++}`;
}
