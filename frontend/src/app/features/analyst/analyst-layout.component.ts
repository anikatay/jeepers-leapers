import { Component } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { NavbarComponent, NavItem } from '../../shared/components/navbar.component';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-analyst-layout',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <app-navbar
      [navItems]="navItems"
      userName="Analyst"
      (profileOptionSelected)="onProfileOptionSelected($event)">
    </app-navbar>
    <router-outlet></router-outlet>
  `
})
export class AnalystLayoutComponent {
  navItems: NavItem[] = [
    { label: 'Dashboard', path: '/analyst/dashboard' },
    { label: 'Analysis', path: '/analyst/dashboard#analysis-chart' },
    { label: 'Activity', path: '/analyst/dashboard#trading-activity' }
  ];

  constructor(private authService: AuthService, private router: Router) {}

  onProfileOptionSelected(option: string): void {
    if (option === 'logout') {
      this.authService.logout();
      this.router.navigateByUrl('/auth/login');
    }
  }
}