import { Component, OnInit, effect, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TradesService } from '../../../../core/services/trades.service';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../shared/components/navbar.component';
import { Trade } from '../../../../core/models/trade.model';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css'
})
export class AdminHomeComponent implements OnInit {

  trades!: Signal<Trade[] | null>;
  isHomeRoute = true;

  constructor(
    private tradesService: TradesService,
    private router: Router
  ) {
    this.trades = this.tradesService.trades;
    this.isHomeRoute = this.router.url === '/admin';

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        this.isHomeRoute = this.router.url === '/admin';
      });

    effect(() => {
      const data = this.trades();

      if (data) {
        console.log('Trades updated:', data);
      }
    });
  }

  ngOnInit() {
    const userId = 'a1000000-0000-0000-0000-000000000001';

    this.tradesService.getTrades(userId);
  }

  navItems: NavItem[] = [
    { label: 'Home', path: '/admin', exact: true },
    { label: 'Users', path: '/admin/users' },
    { label: 'Trades', path: '/admin/instruments' }
  ];

  userName = 'ADMIN';

  columns: { header: string; field: keyof Trade }[] = [
    { header: 'Trade ID', field: 'tradeId' },
    { header: 'Instrument', field: 'instrumentName' },
    { header: 'Ticker', field: 'ticker' },
    { header: 'Action', field: 'side' },
    { header: 'Quantity', field: 'quantity' },
    { header: 'Execution Price', field: 'executionPrice' },
    { header: 'Trade Value', field: 'tradeValue' },
    { header: 'Date', field: 'executedAt' }
  ];

  getCellValue(trade: Trade, field: keyof Trade) {
    return trade[field];
  }
}