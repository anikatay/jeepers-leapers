import { Component, HostListener, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

// Global theme classes on <html>, defined in styles-theme.css.
const THEMES = ['bull', 'ocean', 'lavender', 'rose', 'sage', 'mono', 'midnight'];

@Component({
  selector: 'app-analyst-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TitleCasePipe],
  template: `
    <header class="bar">
      <a class="brand" routerLink="/analyst/dashboard">
        <img src="/symbol.PNG" alt="" width="34" height="34" />
        <span class="font-display">Bull Exchange</span>
      </a>

      <nav aria-label="Analyst">
        <a routerLink="/analyst/dashboard" routerLinkActive="active">Analytics</a>
      </nav>

      <div class="actions">
        <button type="button" class="switch" role="switch" [class.on]="dark()" [attr.aria-checked]="dark()" aria-label="Dark mode" (click)="toggleDark()">
          <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.5" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
          <svg viewBox="0 0 24 24"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
          <span class="knob"></span>
        </button>

        <div class="theme" (click)="$event.stopPropagation()">
          <button type="button" class="pill" aria-label="Color theme" [attr.aria-expanded]="menuOpen()" (click)="menuOpen.set(!menuOpen())">
            <span class="swatch"></span>
            <svg viewBox="0 0 24 24" class="caret"><path d="m6 9 6 6 6-6" /></svg>
          </button>
          @if (menuOpen()) {
            <div class="menu" role="menu">
              @for (item of themes; track item) {
                <button type="button" role="menuitemradio" [attr.aria-checked]="theme() === item" [class.active]="theme() === item" (click)="setTheme(item)">{{ item | titlecase }}</button>
              }
            </div>
          }
        </div>

        <span class="user"><span class="avatar">A</span>Analyst</span>
        <button type="button" class="pill" aria-label="Sign out" title="Sign out" (click)="logout()">
          <svg viewBox="0 0 24 24"><path d="M15 17l5-5-5-5M20 12H9M12 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7" /></svg>
        </button>
      </div>
    </header>
    <router-outlet></router-outlet>
  `,
  styles: `
    .bar {
      align-items: center;
      background: color-mix(in srgb, var(--card) 88%, transparent);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--border);
      display: grid;
      gap: 1rem;
      grid-template-columns: 1fr auto 1fr;
      height: 4rem;
      padding: 0 clamp(1rem, 3vw, 2.5rem);
      position: sticky;
      top: 0;
      z-index: 20;
    }

    .brand { align-items: center; color: var(--primary); display: flex; font-size: 1.1rem; font-weight: 700; gap: 0.6rem; text-decoration: none; }
    .brand img { border-radius: 50%; object-fit: cover; }

    nav a {
      border-bottom: 2px solid transparent;
      color: var(--muted-foreground);
      font-size: 0.875rem;
      font-weight: 600;
      padding: 1.3rem 0.25rem;
      text-decoration: none;
    }

    nav a.active { border-color: var(--primary); color: var(--primary); }

    .actions { align-items: center; display: flex; gap: 0.5rem; justify-self: end; }

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
    .pill:focus-visible,
    .menu button:focus-visible { outline: 2px solid var(--ring); outline-offset: 1px; }

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
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 0.75rem;
      box-shadow: 0 12px 28px rgb(0 0 0 / 0.14);
      display: grid;
      margin-top: 0.4rem;
      min-width: 9rem;
      padding: 0.3rem;
      position: absolute;
      right: 0;
      top: 100%;
    }

    .menu button {
      background: transparent;
      border: 0;
      border-radius: 0.45rem;
      color: var(--foreground);
      cursor: pointer;
      font: inherit;
      font-size: 0.8rem;
      padding: 0.45rem 0.65rem;
      text-align: left;
    }

    .menu button:hover { background: var(--secondary); }
    .menu button.active { color: var(--primary); font-weight: 600; }

    .user { align-items: center; color: var(--foreground); display: inline-flex; font-size: 0.85rem; font-weight: 600; gap: 0.5rem; margin-left: 0.35rem; }

    .avatar {
      background: var(--secondary);
      border-radius: 50%;
      color: var(--primary);
      display: grid;
      font-size: 0.75rem;
      height: 1.9rem;
      place-items: center;
      width: 1.9rem;
    }

    @media (max-width: 720px) {
      .bar { grid-template-columns: auto 1fr auto; }
      .brand span,
      .user { display: none; }
    }
  `
})
export class AnalystLayoutComponent {
  readonly themes = THEMES;
  private readonly root = document.documentElement;
  readonly dark = signal(this.root.classList.contains('dark'));
  readonly theme = signal(THEMES.find(item => this.root.classList.contains(item)) ?? 'bull');
  readonly menuOpen = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  @HostListener('document:click')
  @HostListener('document:keydown.escape')
  closeMenu(): void {
    this.menuOpen.set(false);
  }

  toggleDark(): void {
    this.dark.set(this.root.classList.toggle('dark'));
  }

  setTheme(theme: string): void {
    this.root.classList.remove(...THEMES);
    this.root.classList.add(theme);
    this.theme.set(theme);
    this.menuOpen.set(false);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/auth/login');
  }
}
