import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { Router, RouterOutlet, NavigationEnd  } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../layout/navbar/navbar.component';
import { filter } from 'rxjs/operators';


@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})
export class ClientHomeComponent implements OnInit{
  portfolio;
  profileOpen = false;
  isHomeRoute = true;
  userName = 'Joana';
  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Portfolio', path: 'portfolio' },
    { label: 'Orders', path: 'orders' },
    { label: 'Alerts', path: 'alerts' },
  ];

  constructor(private portfolioService: PortfolioService, private router: Router ) {
    this.portfolio = this.portfolioService.portfolio;

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
    const userId = 'a1000000-0000-0000-0000-000000000001';
    this.portfolioService.getPortfolio(userId); // fire and forget — no .subscribe()
    this.isHomeRoute = this.router.url === '/client'; 
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
