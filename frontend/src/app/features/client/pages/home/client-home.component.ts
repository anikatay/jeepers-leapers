import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

export interface StockHolding {
  symbol: string;
  name: string;
  pct: number;
  value: number;
  price: number;
}

export interface Trade {
  time: string;
  symbol: string;
  type: 'Buy' | 'Sell';
  qty: number;
  price: number;
  total: number;
}

type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'All';

@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})
export class ClientHomeComponent implements OnInit {
  userInitial = 'J';
  userName = 'Joanna';

  holdings: StockHolding[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', pct: 40, value: 20972, price: 175.20 },
    { symbol: 'TSLA', name: 'Tesla Inc.', pct: 25, value: 13108, price: 242.15 },
    { symbol: 'MSFT', name: 'Microsoft Corp.', pct: 15, value: 7865, price: 415.30 },
    { symbol: 'NVDA', name: 'NVIDIA Corp.', pct: 10, value: 5243, price: 894.60 },
    { symbol: 'Others', name: 'Other Holdings', pct: 10, value: 5242, price: 0 },
  ];

  completedTrades: Trade[] = [
    { time: '10:24 AM', symbol: 'AAPL', type: 'Buy', qty: 10, price: 175.20, total: 1752.00 },
    { time: '11:08 AM', symbol: 'TSLA', type: 'Sell', qty: 5, price: 242.15, total: 1210.75 },
    { time: '1:32 PM', symbol: 'MSFT', type: 'Buy', qty: 8, price: 415.30, total: 3322.40 },
    { time: '3:10 PM', symbol: 'NVDA', type: 'Sell', qty: 3, price: 894.60, total: 2683.80 },
  ];

  selectedPeriod = signal<Period>('1M');
  activePieIndex = signal<number | undefined>(undefined);
  periodOptions: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'All'];

  portfolioValue = '$52,430';
  todaysGain = '+2.35%';
  overallGain = '+12.48%';
  cashRemaining = '$18,250';

  stockIconStyles: Record<string, { label: string; background: string; color: string }> = {
    AAPL: { label: 'A', background: '#111827', color: '#ffffff' },
    TSLA: { label: 'T', background: '#dc2626', color: '#ffffff' },
    MSFT: { label: 'M', background: '#2563eb', color: '#ffffff' },
    NVDA: { label: 'N', background: '#65a30d', color: '#ffffff' },
    GOOGL: { label: 'G', background: '#f3f4f6', color: '#2563eb' },
    AMZN: { label: 'a', background: '#111827', color: '#f59e0b' },
    META: { label: 'M', background: '#2563eb', color: '#ffffff' },
    'BRK.B': { label: 'B', background: '#1e3a8a', color: '#ffffff' },
    Others: { label: 'O', background: 'var(--secondary)', color: 'var(--primary)' },
  };

  constructor(private router: Router) {}

  ngOnInit() {
    // Component initialization - UI only, no backend calls
  }

  getStockIcon(symbol: string) {
    return this.stockIconStyles[symbol] ?? {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  setPeriod(period: Period) {
    this.selectedPeriod.set(period);
  }

  setPieActive(index: number) {
    this.activePieIndex.set(index);
  }

  clearPieActive() {
    this.activePieIndex.set(undefined);
  }

  onStockClick(holding: StockHolding) {
    if (holding.symbol !== 'Others') {
      console.log('Navigate to stock:', holding.symbol);
    }
  }

  goToPortfolio() {
    this.router.navigate(['/client/portfolio']);
  }

  goToTrades() {
    this.router.navigate(['/client/trades']);
  }

  goToProfile() {
    this.router.navigate(['/client/profile']);
  }

  handleProfileOption(option: string) {
    console.log('Profile option:', option);
  }
}
