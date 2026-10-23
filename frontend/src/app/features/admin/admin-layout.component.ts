import { Component } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NavbarComponent, NavItem } from '../../shared/components/navbar.component';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [NavbarComponent, RouterOutlet],
  template: `
    <app-navbar
      [homePath]="'/admin'"
      [navItems]="navItems"
      [showSearch]="false"
      [userName]="userName"
      [brandName]="'Bull Exchange'"
      (profileOptionSelected)="onProfileOptionSelected($event)">
    </app-navbar>

    <router-outlet></router-outlet>
  `
})
export class AdminLayoutComponent {
  readonly navItems: NavItem[] = [
    { label: 'Home', path: '/admin' },
    { label: 'Analytics', path: '/admin/analytics' }
  ];

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  get userName(): string {
    const session = this.authService.session();
    return [session?.firstName, session?.lastName].filter(Boolean).join(' ') || 'Administrator';
  }

  onProfileOptionSelected(option: string): void {
    if (option === 'logout') {
      this.authService.logout();
      this.router.navigateByUrl('/auth/login');
    }
  }
}