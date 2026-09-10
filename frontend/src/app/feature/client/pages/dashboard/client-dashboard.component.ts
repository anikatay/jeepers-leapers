import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { Router, RouterOutlet } from '@angular/router';


@Component({
  selector: 'app-client-dashboard',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: './client-dashboard.component.html',
  styleUrl: './client-dashboard.component.css'
})
export class ClientDashboardComponent implements OnInit{
  portfolio;

  constructor(private portfolioService: PortfolioService, private router: Router ) {
    this.portfolio = this.portfolioService.portfolio;
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
  }

  navItems = ['Home', 'Portfolio', 'Orders', 'Holding', 'Alerts'];
  activeNav = 'Home';

  profileOpen = false;

  periods = ['1M', '3M', '6M', '1Y', 'All'];
  activePeriod = '6M';

  allocation = [
    { name: 'Stocks', value: 55, color: '#16a34a' },
    { name: 'Bonds', value: 25, color: '#0ea5e9' },
    { name: 'Cash', value: 20, color: '#f59e0b' }
  ];

  navigateTo(item: string) {
    this.activeNav = item;

    if (item === 'Portfolio') {
      this.router.navigate(['portfolio']); // relative to current route
    } else if (item === 'Home') {
      this.router.navigate(['.']); // back to the parent path (Home)
    }
    // add more branches later for Orders, Holding, Alerts as you build them out
  }

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
