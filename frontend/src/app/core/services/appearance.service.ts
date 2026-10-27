import { Injectable, effect, signal } from '@angular/core';

export interface ThemeOption {
  id: string;
  label: string;
  /** Light-mode primary and accent from styles-theme.css, used only for the picker swatch. */
  swatch: [string, string];
  accessible?: boolean;
}

// Class names on <html>, defined in styles-theme.css. "bull" is the :root default.
export const THEMES: ThemeOption[] = [
  { id: 'bull', label: 'Bull', swatch: ['#1a6b3c', '#22c55e'] },
  { id: 'ocean', label: 'Ocean', swatch: ['#1d4ed8', '#38bdf8'] },
  { id: 'lavender', label: 'Lavender', swatch: ['#7c3aed', '#a78bfa'] },
  { id: 'rose', label: 'Rose', swatch: ['#be185d', '#f472b6'] },
  { id: 'sage', label: 'Sage', swatch: ['#4a7c59', '#6aaa7e'] },
  { id: 'mono', label: 'Mono', swatch: ['#374151', '#6b7280'] },
  { id: 'midnight', label: 'Midnight', swatch: ['#1e3a5f', '#2d7dd2'] },
  { id: 'a11y-bo', label: 'Blue & Orange', swatch: ['#1a4d8a', '#d4600a'], accessible: true },
  { id: 'a11y-bg', label: 'Blue & Gold', swatch: ['#0c2d6b', '#c8960a'], accessible: true },
  { id: 'a11y-hc', label: 'High Contrast', swatch: ['#000000', '#0060c0'], accessible: true }
];

export type TextSize = 'small' | 'normal' | 'large' | 'x-large';

/** `scale` mirrors the root font-size percentages in styles-theme.css, for px-based drawing (SVG charts). */
export const TEXT_SIZES: { id: TextSize; label: string; scale: number }[] = [
  { id: 'small', label: 'Small', scale: 0.875 },
  { id: 'normal', label: 'Normal', scale: 1 },
  { id: 'large', label: 'Large', scale: 1.125 },
  { id: 'x-large', label: 'Extra Large', scale: 1.25 }
];

interface AppearancePrefs {
  dark: boolean;
  theme: string;
  textSize: TextSize;
}

const STORAGE_KEY = 'app-appearance';
const DEFAULTS: AppearancePrefs = { dark: false, theme: 'bull', textSize: 'normal' };

/** Single source of truth for dark mode, color theme and text size across Client and Analyst. */
@Injectable({ providedIn: 'root' })
export class AppearanceService {
  private readonly root = document.documentElement;
  private readonly stored = this.load();

  readonly dark = signal(this.stored.dark);
  readonly theme = signal(this.stored.theme);
  readonly textSize = signal<TextSize>(this.stored.textSize);

  constructor() {
    effect(() => {
      const prefs: AppearancePrefs = { dark: this.dark(), theme: this.theme(), textSize: this.textSize() };
      this.root.classList.toggle('dark', prefs.dark);
      this.root.classList.remove(...THEMES.map(t => t.id));
      this.root.classList.add(prefs.theme);
      if (prefs.textSize === 'normal') {
        this.root.removeAttribute('data-text-size');
      } else {
        this.root.setAttribute('data-text-size', prefs.textSize);
      }
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
      } catch {
        // Storage unavailable (e.g. private mode); the choice still applies for this session.
      }
    });
  }

  toggleDark(): void {
    this.dark.update(value => !value);
  }

  setTheme(theme: string): void {
    if (THEMES.some(t => t.id === theme)) this.theme.set(theme);
  }

  textScale(): number {
    return TEXT_SIZES.find(s => s.id === this.textSize())?.scale ?? 1;
  }

  setTextSize(size: TextSize): void {
    if (TEXT_SIZES.some(s => s.id === size)) this.textSize.set(size);
  }

  /**
   * Client Home allocation-donut slice color.
   * Resolves `--donut-1` … `--donut-12` from styles-theme.css so the fill
   * follows the active theme/dark class without storing a snapshot hex.
   */
  donutSliceColor(index: number): string {
    const slots = 12;
    const slot = ((index % slots) + slots) % slots;
    return `var(--donut-${slot + 1})`;
  }

  private load(): AppearancePrefs {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<AppearancePrefs> | null;
      if (!raw) return DEFAULTS;
      return {
        dark: typeof raw.dark === 'boolean' ? raw.dark : DEFAULTS.dark,
        theme: THEMES.some(t => t.id === raw.theme) ? raw.theme! : DEFAULTS.theme,
        textSize: TEXT_SIZES.some(s => s.id === raw.textSize) ? raw.textSize! : DEFAULTS.textSize
      };
    } catch {
      return DEFAULTS;
    }
  }
}
