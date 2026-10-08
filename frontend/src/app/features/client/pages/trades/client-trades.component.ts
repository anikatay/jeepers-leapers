import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TradesService } from '../../../../core/services/trades.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { TradeResponse } from '../../../../core/models/trade.model';

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
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './client-trades.component.html',
  styleUrl: './client-trades.component.css'
})
export class ClientTradesComponent implements OnInit {
  private readonly ACCOUNT_ID = 'd4000000-0000-0000-0000-000000000001';

  // Real trade data from backend
  allTrades = signal<Trade[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

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

  constructor(private tradesService: TradesService, private instrumentService: InstrumentService, public translationService: TranslationService) {}

  ngOnInit() {
    this.loadTrades();
  }

  private loadTrades() {
    this.loading.set(true);
    this.error.set(null);
    this.tradesService.getTradesByAccount(this.ACCOUNT_ID).subscribe({
      next: (tradeResponses) => {
        const trades = tradeResponses.map((tr: TradeResponse) => this.mapToTrade(tr));
        this.allTrades.set(trades);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load trades:', err);
        this.error.set('Failed to load trades');
        this.allTrades.set([]);
        this.loading.set(false);
      }
    });
  }

  private mapToTrade(response: TradeResponse): Trade {
    const dateTime = new Date(response.executedAt);
    return {
      id: response.tradeId,
      date: dateTime.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }),
      time: dateTime.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true }),
      symbol: response.ticker,
      name: response.name,
      type: response.side === 'BUY' ? 'Buy' : 'Sell',
      qty: response.quantity,
      price: response.executionPrice,
      total: response.tradeValue
    };
  }

  getStockIcon(symbol: string) {
    return this.stockIconStyles[symbol] ?? {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  get filteredTrades(): Trade[] {
    const trades = this.allTrades();
    const searchLower = this.search().toLowerCase().trim();
    const type = this.typeFilter();
    const from = this.fromDate();
    const to = this.toDate();
    const order = this.sortOrder();

    let filtered = trades.filter(trade => {
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
    return this.allTrades().length;
  }

  get totalBought(): number {
    return this.allTrades()
      .filter(tr => tr.type === 'Buy')
      .reduce((sum, tr) => sum + tr.total, 0);
  }

  get totalSold(): number {
    return this.allTrades()
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
    // Redirect to Portfolio to use its trade modal
    window.location.href = '/client/portfolio';
  }

}
