import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Trade {
  id?: string;
  date: string;
  time: string;
  symbol: string;
  name: string;
  type: 'Buy' | 'Sell';
  qty: number;
  price: number;
  total: number;
}

type SortOrder = 'newest' | 'oldest';
type TypeFilter = 'all' | 'Buy' | 'Sell';

@Component({
  selector: 'app-client-trades',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-trades.component.html',
  styleUrl: './client-trades.component.css'
})
export class ClientTradesComponent implements OnInit {
  // Placeholder trade data
  allTrades: Trade[] = [
    { date: 'Sep 24, 2026', time: '10:24 AM', symbol: 'AAPL', name: 'Apple Inc.', type: 'Buy', qty: 10, price: 175.20, total: 1752.00 },
    { date: 'Sep 24, 2026', time: '11:08 AM', symbol: 'TSLA', name: 'Tesla Inc.', type: 'Sell', qty: 5, price: 242.15, total: 1210.75 },
    { date: 'Sep 24, 2026', time: '1:32 PM', symbol: 'MSFT', name: 'Microsoft Corp.', type: 'Buy', qty: 8, price: 415.30, total: 3322.40 },
    { date: 'Sep 24, 2026', time: '3:10 PM', symbol: 'NVDA', name: 'NVIDIA Corp.', type: 'Sell', qty: 3, price: 894.60, total: 2683.80 },
    { date: 'Sep 23, 2026', time: '9:35 AM', symbol: 'GOOGL', name: 'Alphabet Inc.', type: 'Buy', qty: 6, price: 178.50, total: 1071.00 },
    { date: 'Sep 23, 2026', time: '2:15 PM', symbol: 'META', name: 'Meta Platforms', type: 'Buy', qty: 3, price: 553.10, total: 1659.30 },
    { date: 'Sep 22, 2026', time: '10:00 AM', symbol: 'AMZN', name: 'Amazon.com', type: 'Buy', qty: 8, price: 201.30, total: 1610.40 },
    { date: 'Sep 22, 2026', time: '3:45 PM', symbol: 'AAPL', name: 'Apple Inc.', type: 'Buy', qty: 5, price: 173.80, total: 869.00 },
    { date: 'Sep 21, 2026', time: '11:20 AM', symbol: 'TSLA', name: 'Tesla Inc.', type: 'Sell', qty: 2, price: 240.00, total: 480.00 },
    { date: 'Sep 20, 2026', time: '9:45 AM', symbol: 'NVDA', name: 'NVIDIA Corp.', type: 'Buy', qty: 2, price: 885.00, total: 1770.00 },
    { date: 'Sep 19, 2026', time: '1:00 PM', symbol: 'MSFT', name: 'Microsoft Corp.', type: 'Buy', qty: 4, price: 410.20, total: 1640.80 },
    { date: 'Sep 18, 2026', time: '10:30 AM', symbol: 'BRK.B', name: 'Berkshire Hathaway', type: 'Buy', qty: 10, price: 390.00, total: 3900.00 },
  ];

  // Filter controls
  search = signal('');
  typeFilter = signal<TypeFilter>('all');
  fromDate = signal('');
  toDate = signal('');
  sortOrder = signal<SortOrder>('newest');
  showDateFilter = signal(false);

  // Stock icon styles
  stockIconStyles: Record<string, { label: string; background: string; color: string }> = {
    AAPL: { label: 'A', background: '#111827', color: '#ffffff' },
    TSLA: { label: 'T', background: '#dc2626', color: '#ffffff' },
    MSFT: { label: 'M', background: '#2563eb', color: '#ffffff' },
    NVDA: { label: 'N', background: '#65a30d', color: '#ffffff' },
    GOOGL: { label: 'G', background: '#f3f4f6', color: '#2563eb' },
    AMZN: { label: 'a', background: '#111827', color: '#f59e0b' },
    META: { label: 'M', background: '#2563eb', color: '#ffffff' },
    'BRK.B': { label: 'B', background: '#1e3a8a', color: '#ffffff' },
  };

  ngOnInit() {
    // UI initialization only
  }

  getStockIcon(symbol: string) {
    return this.stockIconStyles[symbol] ?? {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  get filteredTrades(): Trade[] {
    const searchLower = this.search().toLowerCase().trim();
    const type = this.typeFilter();
    const from = this.fromDate();
    const to = this.toDate();
    const order = this.sortOrder();

    let filtered = this.allTrades.filter(trade => {
      // Search filter
      if (searchLower && !trade.symbol.toLowerCase().includes(searchLower) && !trade.name.toLowerCase().includes(searchLower)) {
        return false;
      }

      // Type filter
      if (type !== 'all' && trade.type !== type) {
        return false;
      }

      // Date range filter
      const tradeDate = new Date(trade.date);
      if (from) {
        const fromDateTime = new Date(`${from}T00:00:00`);
        if (tradeDate < fromDateTime) return false;
      }
      if (to) {
        const toDateTime = new Date(`${to}T23:59:59`);
        if (tradeDate > toDateTime) return false;
      }

      return true;
    });

    // Sort
    filtered.sort((a, b) => {
      const aTime = new Date(`${a.date} ${a.time}`).getTime();
      const bTime = new Date(`${b.date} ${b.time}`).getTime();
      return order === 'newest' ? bTime - aTime : aTime - bTime;
    });

    return filtered;
  }

  get hasActiveFilters(): boolean {
    return this.search().trim() !== '' || 
           this.typeFilter() !== 'all' || 
           this.fromDate() !== '' || 
           this.toDate() !== '' || 
           this.sortOrder() !== 'newest' ||
           this.showDateFilter();
  }

  get totalTrades(): number {
    return this.allTrades.length;
  }

  get totalBought(): number {
    return this.allTrades
      .filter(tr => tr.type === 'Buy')
      .reduce((sum, tr) => sum + tr.total, 0);
  }

  get totalSold(): number {
    return this.allTrades
      .filter(tr => tr.type === 'Sell')
      .reduce((sum, tr) => sum + tr.total, 0);
  }

  clearFilters() {
    this.search.set('');
    this.typeFilter.set('all');
    this.fromDate.set('');
    this.toDate.set('');
    this.sortOrder.set('newest');
    this.showDateFilter.set(false);
  }

  toggleDateFilter() {
    this.showDateFilter.update(v => !v);
  }

  onSearchChange(value: string) {
    this.search.set(value);
  }

  onTypeFilterChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.typeFilter.set(value as TypeFilter);
  }

  onSortChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.sortOrder.set(value as SortOrder);
  }

  onFromDateChange(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.fromDate.set(value);
  }

  onToDateChange(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.toDate.set(value);
  }

  openNewTrade() {
    // Placeholder for opening trade modal
    console.log('Open new trade modal');
  }
}
