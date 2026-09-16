import { Component, OnInit, ChangeDetectorRef, effect, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { Router, RouterOutlet, NavigationEnd  } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../shared/components/navbar.component';
import { filter } from 'rxjs/operators';
import { Portfolio } from '../../../../core/models/portfolio.model';


@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})
export class ClientHomeComponent implements OnInit{
  portfolio: Signal<Portfolio | null>;
  loading: Signal<boolean>;
  error: Signal<string | null>;
  profileOpen = false;
  isHomeRoute = true;
  userName = 'Joana';
  selectedUserId = 'a1000000-0000-0000-0000-000000000001';
  users = [
    { id: 'a1000000-0000-0000-0000-000000000001', name: 'Alice' },
    { id: 'a1000000-0000-0000-0000-000000000002', name: 'Bob' },
    { id: 'a1000000-0000-0000-0000-000000000003', name: 'Charlie' },
  ];
  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Portfolio', path: 'portfolio' },
    { label: 'Orders', path: 'orders' },
    { label: 'Alerts', path: 'alerts' },
  ];

  constructor(private portfolioService: PortfolioService, private router: Router ) {
    this.portfolio = this.portfolioService.portfolio;
    this.loading = this.portfolioService.loading;
    this.error = this.portfolioService.error;

    this.router.events
    .pipe(filter(event => event instanceof NavigationEnd))
    .subscribe(() => {
      this.isHomeRoute = this.router.url === '/client';
    });

    effect(() => {
      const data = this.portfolio();
      if (data) {
        console.log('Portfolio updated:', data);
      }
    });
  }

  ngOnInit() {
    this.portfolioService.getPortfolio(this.selectedUserId);
    this.isHomeRoute = this.router.url === '/client';
  }

  onUserChange(userId: string) {
    this.selectedUserId = userId;
    const user = this.users.find(u => u.id === userId);
    if (user) {
      this.userName = user.name;
    }
    this.portfolioService.getPortfolio(userId);
  }
  
  handleProfileOption(option: string) {
    if (option === 'Logout') {
      // your logout logic
    }
  }

  periods = ['1M', '3M', '6M', '1Y', 'All'];
  activePeriod = '6M';

  allocation = [
    { name: 'Stocks', value: 55, color: '#16a34a' },
    { name: 'Bonds', value: 25, color: '#0ea5e9' },
    { name: 'Cash', value: 20, color: '#f59e0b' }
  ];

  toggleProfile(event: Event) {
    event.stopPropagation();
    this.profileOpen = !this.profileOpen;
  }

  selectProfileOption(option: string) {
    console.log(`Selected: ${option}`);
    this.profileOpen = false;
  }

  setPeriod(period: string) {
    this.activePeriod = period;
  }
}