import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent, NavItem } from '../../shared/components/navbar.component';

@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <app-navbar [navItems]="navItems" [userName]="userName"></app-navbar>
    <router-outlet></router-outlet>
  `,
  styles: []
})
export class ClientLayoutComponent {
  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Portfolio', path: 'portfolio' },
    { label: 'Trades', path: 'trades' }
  ];

  userName = 'Joanna';
}
