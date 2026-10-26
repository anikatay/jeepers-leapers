import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AppearanceControlsComponent } from '../../shared/components/appearance-controls.component';

@Component({
  selector: 'app-analyst-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppearanceControlsComponent],
  template: `
    <header class="bar">
      <a class="brand" routerLink="/analyst/dashboard">
        <img src="/symbol.PNG" alt="" width="34" height="34" />
        <span class="font-display">Bull Exchange</span>
      </a>

      <nav aria-label="Analyst">
        <a routerLink="/analyst/dashboard" routerLinkActive="active">Analytics</a>
        <a routerLink="/analyst/heatmap" routerLinkActive="active">Market Heatmap</a>
      </nav>

      <div class="actions">
        <app-appearance-controls />

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

    nav { display: flex; gap: 1.5rem; }
    nav a.active { border-color: var(--primary); color: var(--primary); }

    .actions { align-items: center; display: flex; gap: 0.5rem; justify-self: end; }

    svg { fill: none; height: 14px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 14px; }

    .pill {
      align-items: center;
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 999px;
      color: var(--muted-foreground);
      cursor: pointer;
      display: inline-flex;
      gap: 0.35rem;
      height: 2rem;
      justify-content: center;
      min-width: 2rem;
      padding: 0 0.55rem;
      transition: background 0.2s;
    }

    .pill:hover { background: var(--secondary); }
    .pill:focus-visible { outline: 2px solid var(--ring); outline-offset: 1px; }

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
  constructor(private authService: AuthService, private router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/auth/login');
  }
}
