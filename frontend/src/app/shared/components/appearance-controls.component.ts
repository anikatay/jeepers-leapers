import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { AppearanceService } from '../../core/services/appearance.service';
import { AppearanceSettingsComponent } from './appearance-settings.component';

let nextId = 0;

/** Header dark-mode switch and appearance dropdown, shared by the Client and Analyst headers. */
@Component({
  selector: 'app-appearance-controls',
  standalone: true,
  imports: [AppearanceSettingsComponent],
  template: `
    <button type="button" class="switch" role="switch" [class.on]="appearance.dark()" [attr.aria-checked]="appearance.dark()" aria-label="Dark mode" (click)="appearance.toggleDark()">
      <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.5" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
      <svg viewBox="0 0 24 24"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
      <span class="knob"></span>
    </button>

    <div class="theme">
      <button type="button" class="pill" aria-label="Appearance and accessibility" [attr.aria-controls]="menuId"
              [attr.aria-expanded]="menuOpen()" (click)="toggleMenu()">
        <span class="swatch"></span>
        <svg viewBox="0 0 24 24" class="caret"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      @if (menuOpen()) {
        <div class="menu" [class.clamped]="menuLeft() !== null" [style.left.px]="menuLeft()"
             [style.width.px]="menuLeft() !== null ? menuWidth() : null" [id]="menuId" role="group" aria-label="Appearance and accessibility">
          <app-appearance-settings [showMode]="false" />
        </div>
      }
    </div>
  `,
  styles: `
    :host { align-items: center; display: flex; gap: 0.5rem; }

    svg { fill: none; height: 14px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 14px; }

    .switch,
    .pill {
      align-items: center;
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 999px;
      color: var(--muted-foreground);
      cursor: pointer;
      display: inline-flex;
      height: 2rem;
      transition: background 0.2s;
    }

    .switch:hover,
    .pill:hover { background: var(--secondary); }
    .switch:focus-visible,
    .pill:focus-visible { outline: 2px solid var(--ring); outline-offset: 1px; }

    .switch { justify-content: space-between; padding: 0 0.45rem; position: relative; width: 4rem; }
    .switch svg { position: relative; transition: color 0.2s; z-index: 1; }
    .switch:not(.on) svg:first-child,
    .switch.on svg:nth-child(2) { color: var(--primary-foreground); }

    .knob {
      background: var(--primary);
      border-radius: 50%;
      height: 1.625rem;
      left: 2px;
      position: absolute;
      top: 2px;
      transition: transform 0.25s cubic-bezier(0.3, 0.7, 0.3, 1);
      width: 1.625rem;
    }

    .switch.on .knob { transform: translateX(calc(4rem - 1.625rem - 6px)); }

    .theme { position: relative; }
    .pill { gap: 0.35rem; justify-content: center; min-width: 2rem; padding: 0 0.55rem; }
    .swatch { background: var(--primary); border-radius: 50%; height: 0.8rem; width: 0.8rem; }
    .caret { height: 10px; width: 10px; }

    .menu {
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: 0.75rem;
      box-shadow: var(--shadow-raised);
      margin-top: 0.4rem;
      max-height: calc(100vh - 6rem);
      overflow-y: auto;
      padding: 1rem;
      position: absolute;
      right: 0;
      top: 100%;
      width: min(24rem, calc(100vw - 2rem));
      z-index: 30;
    }

    .menu.clamped { right: auto; }
  `
})
export class AppearanceControlsComponent {
  readonly appearance = inject(AppearanceService);
  readonly menuOpen = signal(false);
  readonly menuId = `appearance-menu-${nextId++}`;
  /** Horizontal offset from the pill's container; null keeps the default right-edge alignment. */
  readonly menuLeft = signal<number | null>(null);
  readonly menuWidth = signal<number | null>(null);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  toggleMenu(): void {
    if (!this.menuOpen()) {
      // Right-aligned under the pill, but kept on screen when the header wraps the controls to the left.
      const theme = this.host.nativeElement.querySelector('.theme')?.getBoundingClientRect();
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
      const viewport = document.documentElement.clientWidth;
      const menuWidth = Math.min(24 * rem, viewport - 2 * rem);
      const preferred = theme ? theme.right - menuWidth : rem;
      const left = Math.min(Math.max(preferred, rem), viewport - rem - menuWidth);
      this.menuLeft.set(theme && left !== preferred ? left - theme.left : null);
      this.menuWidth.set(menuWidth);
    }
    this.menuOpen.update(open => !open);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.querySelector('.theme')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  closeMenu(): void {
    this.menuOpen.set(false);
  }
}
