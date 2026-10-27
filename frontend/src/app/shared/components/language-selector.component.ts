import { Component, HostListener, inject, signal } from '@angular/core';
import { TranslationService, LANGUAGES, type Language } from '../../core/services/translation.service';
import { TranslatePipe } from '../../core/pipes/translate.pipe';

@Component({
  selector: 'app-language-selector',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="language-container" (click)="$event.stopPropagation()">
      <button
        type="button"
        class="language-button"
        (click)="toggle()"
        [attr.aria-expanded]="open()"
        [attr.aria-haspopup]="'listbox'"
        [title]="'nav.select_language' | translate"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="12" cy="12" r="10"/>
          <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
        </svg>
        <span class="language-label">{{ selectedCode() }}</span>
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true">
          <polyline points="6 9 12 15 18 9"></polyline>
        </svg>
      </button>

      @if (open()) {
        <div class="language-menu" role="listbox" [attr.aria-label]="'nav.language' | translate" (click)="$event.stopPropagation()">
          <div class="language-panel">
            <p class="panel-title">{{ 'nav.language' | translate }}</p>
            <div class="language-list">
              @for (lang of languages; track lang.code) {
                <button
                  type="button"
                  class="language-item"
                  role="option"
                  [class.active]="translationService.currentLanguage() === lang.code"
                  [attr.aria-selected]="translationService.currentLanguage() === lang.code"
                  (click)="select(lang.code)"
                >
                  <span class="lang-english">{{ lang.englishName }}</span>
                  <span class="lang-native">{{ lang.nativeName }}</span>
                </button>
              }
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      position: relative;
      z-index: 50;
    }

    .language-container {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .language-button {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--foreground, #1a2e1f);
      background: color-mix(in srgb, var(--cream, #fff8ee) 70%, #ffffff);
      border: 1px solid color-mix(in srgb, #ffffff 48%, var(--border, #c8e6d2));
      border-radius: 0.625rem;
      cursor: pointer;
      transition: background-color 0.2s ease, border-color 0.2s ease;
      white-space: nowrap;
      box-shadow: 0 4px 12px color-mix(in srgb, var(--primary, #1a6b3c) 8%, transparent);
    }

    .language-button:hover,
    .language-button:focus-visible {
      background: color-mix(in srgb, var(--secondary, #e6f5ec) 80%, #ffffff);
      border-color: var(--primary, #1a6b3c);
    }

    .language-button svg {
      flex-shrink: 0;
    }

    .language-label {
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .language-menu {
      position: absolute;
      top: calc(100% + 0.5rem);
      inset-inline-end: 0;
      width: min(16rem, calc(100vw - 2rem));
      background: var(--surface-raised, #fffdf8);
      border: 1px solid var(--border, #c8e6d2);
      border-radius: 1rem;
      box-shadow: var(--shadow-raised, 0 18px 40px color-mix(in srgb, #0f5132 16%, transparent));
      z-index: 60;
      overflow: hidden;
    }

    .language-panel {
      padding: 1rem;
    }

    .panel-title {
      margin: 0 0 0.65rem;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--muted-foreground, #4a7c5a);
    }

    .language-list {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      max-height: 20rem;
      overflow-y: auto;
    }

    .language-item {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      text-align: start;
      gap: 0.2rem;
      width: 100%;
      padding: 0.75rem;
      border: 1px solid transparent;
      border-radius: 0.625rem;
      font-size: 0.875rem;
      color: var(--foreground, #1a2e1f);
      background: transparent;
      cursor: pointer;
      transition: background-color 0.2s ease, border-color 0.2s ease;
    }

    .language-item:hover,
    .language-item:focus-visible {
      background: var(--secondary, #e6f5ec);
      border-color: var(--border, #c8e6d2);
    }

    .language-item.active {
      background: var(--primary, #1a6b3c);
      color: var(--primary-foreground, #ffffff);
      font-weight: 600;
      border-color: var(--primary, #1a6b3c);
    }

    .lang-english {
      font-weight: 600;
      font-size: 0.875rem;
    }

    .lang-native {
      font-size: 0.75rem;
      opacity: 0.85;
    }
  `],
})
export class LanguageSelectorComponent {
  readonly translationService = inject(TranslationService);
  readonly languages = LANGUAGES;
  readonly open = signal(false);

  selectedCode(): string {
    return this.translationService.currentLanguage().toUpperCase();
  }

  toggle(): void {
    this.open.update((value) => !value);
  }

  select(lang: Language): void {
    this.translationService.setLanguage(lang);
    this.open.set(false);
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.open.set(false);
  }
}
