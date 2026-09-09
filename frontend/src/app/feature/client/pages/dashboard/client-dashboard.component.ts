import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioService } from '../../../../core/services/portfolio.service';
// import { Portfolio, Holding } from '../../models/portfolio.model';


@Component({
  selector: 'app-client-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './client-dashboard.component.html',
  styleUrl: './client-dashboard.component.css'
})
export class ClientDashboardComponent implements OnInit{
  // Just expose the service's signal directly — no local copy needed
  portfolio;

  constructor(private portfolioService: PortfolioService) {
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
