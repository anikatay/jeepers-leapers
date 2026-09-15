import { Component, OnInit, ChangeDetectorRef, effect, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TradesService } from '../../../../core/services/trades.service';
import { Router, RouterOutlet } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../layout/navbar/navbar.component';
import { Trade } from '../../../../core/models/trade.model';



@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css'
})
export class AdminHomeComponent implements OnInit{
  trades: Signal<Trade[] | null>;
  loading: Signal<boolean>;
  error: Signal<string | null>;

  constructor(private tradesService: TradesService, private router: Router ) {
    this.trades = this.tradesService.trades;
    this.loading = this.tradesService.loading;
    this.error = this.tradesService.error;
    effect(() => {
      const data = this.trades();
      if (data) {
        console.log('Portfolio updated:', data);
      }
    });
  }

  ngOnInit() {
    this.tradesService.getTrades(); 
  }

  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Trades', path: 'trades' },
    { label: 'Users', path: 'users' },
  ];

  userName = 'ADMIN'; // or pulled from an auth service later
}