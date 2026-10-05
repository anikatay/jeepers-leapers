import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/* ─────────────────────────────────────────────────────────────────────────
   FIGMA-COMPLIANT PORTFOLIO PAGE
   Static placeholder data to match Figma Portfolio.tsx exactly
───────────────────────────────────────────────────────────────────────────── */

export interface Holding {
  symbol: string;
  name: string;
  shares: number;
  buyingPrice: number;
  currentPrice: number;
  currentValue: number;
  gain: number;
  gainPct: number;
}

type SortKey = 'shares' | 'buyingPrice' | 'currentPrice' | 'currentValue' | 'gain';

interface SummaryCard {
  label: string;
  value: string;
  isPositive?: boolean;
}

// Stock icon styles from Figma StockIcon.tsx
interface StockIconStyle {
  label: string;
  background: string;
  color: string;
}

@Component({
  selector: 'app-client-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent {
  // ─────────────────────────────────────────────────────────────────
  // STATIC PLACEHOLDER DATA (Figma Base Holdings)
  // ─────────────────────────────────────────────────────────────────
  readonly baseHoldings: Holding[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', shares: 120, buyingPrice: 145.10, currentPrice: 175.20, currentValue: 21024, gain: 3612, gainPct: 20.75 },
    { symbol: 'TSLA', name: 'Tesla Inc.', shares: 54, buyingPrice: 210.30, currentPrice: 242.15, currentValue: 13076, gain: 1719, gainPct: 15.14 },
    { symbol: 'MSFT', name: 'Microsoft Corp.', shares: 19, buyingPrice: 380.00, currentPrice: 415.30, currentValue: 7891, gain: 670, gainPct: 9.29 },
    { symbol: 'NVDA', name: 'NVIDIA Corp.', shares: 5, buyingPrice: 750.00, currentPrice: 894.60, currentValue: 4473, gain: 723, gainPct: 19.28 },
    { symbol: 'GOOGL', name: 'Alphabet Inc.', shares: 6, buyingPrice: 160.00, currentPrice: 178.50, currentValue: 1071, gain: 111, gainPct: 11.56 },
    { symbol: 'AMZN', name: 'Amazon.com Inc.', shares: 8, buyingPrice: 195.00, currentPrice: 201.30, currentValue: 1610, gain: 50, gainPct: 3.23 },
    { symbol: 'META', name: 'Meta Platforms', shares: 3, buyingPrice: 500.00, currentPrice: 553.10, currentValue: 1659, gain: 159, gainPct: 10.60 },
    { symbol: 'BRK.B', name: 'Berkshire Hathaway', shares: 10, buyingPrice: 390.00, currentPrice: 415.00, currentValue: 4150, gain: 250, gainPct: 6.41 },
  ];

  // Stock icon styles - matches Figma StockIcon.tsx exactly
  readonly stockIconStyles: Record<string, StockIconStyle> = {
    AAPL: { label: 'A', background: '#111827', color: '#ffffff' },
    TSLA: { label: 'T', background: '#dc2626', color: '#ffffff' },
    MSFT: { label: 'M', background: '#2563eb', color: '#ffffff' },
    NVDA: { label: 'N', background: '#65a30d', color: '#ffffff' },
    GOOGL: { label: 'G', background: '#f3f4f6', color: '#2563eb' },
    AMZN: { label: 'a', background: '#111827', color: '#f59e0b' },
    META: { label: 'M', background: '#2563eb', color: '#ffffff' },
    'BRK.B': { label: 'B', background: '#1e3a8a', color: '#ffffff' },
  };

  // Cash available from static data
  readonly cashAvailable = 18250;

  // ─────────────────────────────────────────────────────────────────
  // UI STATE (Signals for reactive updates)
  // ─────────────────────────────────────────────────────────────────
  searchQuery = signal<string>('');
  filterType = signal<'all' | 'gain' | 'loss'>('all');
  sortBy = signal<SortKey>('currentValue');
  sortDir = signal<'asc' | 'desc'>('desc');

  // Modal state
  isModalOpen = signal<boolean>(false);
  modalMode = signal<'BUY' | 'SELL'>('BUY');
  selectedHolding = signal<Holding | null>(null);
  modalQuantity = signal<string>('');
  isSubmitting = signal<boolean>(false);
  tradeSuccess = signal<boolean>(false);

  // Filter options
  filterOptions: Array<'all' | 'gain' | 'loss'> = ['all', 'gain', 'loss'];

  // ─────────────────────────────────────────────────────────────────
  // COMPUTED VALUES (Derived state)
  // ─────────────────────────────────────────────────────────────────

  filteredAndSorted = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const filter = this.filterType();
    const sortKey = this.sortBy();
    const sortDirection = this.sortDir();

    // Filter by search query and gain/loss
    let result = this.baseHoldings.filter(h => {
      const matchesQuery = h.symbol.toLowerCase().includes(query) || h.name.toLowerCase().includes(query);
      const matchesFilter =
        filter === 'all' ||
        (filter === 'gain' && h.gain >= 0) ||
        (filter === 'loss' && h.gain < 0);
      return matchesQuery && matchesFilter;
    });

    // Sort
    result.sort((a, b) => {
      const aVal = a[sortKey];
      const bVal = b[sortKey];
      return sortDirection === 'desc' ? bVal - aVal : aVal - bVal;
    });

    return result;
  });

  // Summary cards data
  summaryCards = computed((): SummaryCard[] => {
    const totalValue = this.baseHoldings.reduce((sum, h) => sum + h.currentValue, 0);
    const totalGain = this.baseHoldings.reduce((sum, h) => sum + h.gain, 0);

    return [
      { label: 'Total Portfolio Value', value: `$${totalValue.toLocaleString()}`, isPositive: true },
      { label: "Today's Gain/Loss", value: '+2.35%', isPositive: true },
      { label: 'Overall Gain/Loss', value: `+$${totalGain.toLocaleString()}`, isPositive: true },
      { label: 'Cash Available', value: `$${this.cashAvailable.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: undefined },
    ];
  });

  // ─────────────────────────────────────────────────────────────────
  // METHODS
  // ─────────────────────────────────────────────────────────────────

  getStockIcon(symbol: string): StockIconStyle {
    return this.stockIconStyles[symbol] || {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  toggleSort(key: SortKey): void {
    if (this.sortBy() === key) {
      // Toggle direction
      this.sortDir.set(this.sortDir() === 'desc' ? 'asc' : 'desc');
    } else {
      // New sort key, default descending
      this.sortBy.set(key);
      this.sortDir.set('desc');
    }
  }

  setFilter(filter: 'all' | 'gain' | 'loss'): void {
    this.filterType.set(filter);
  }

  openTradeModal(holding: Holding, mode: 'BUY' | 'SELL'): void {
    this.selectedHolding.set(holding);
    this.modalMode.set(mode);
    this.isModalOpen.set(true);
    this.modalQuantity.set('');
    this.isSubmitting.set(false);
    this.tradeSuccess.set(false);
  }

  closeModal(): void {
    this.isModalOpen.set(false);
    // Reset after modal closes
    setTimeout(() => {
      this.selectedHolding.set(null);
      this.modalQuantity.set('');
    }, 300);
  }

  confirmTrade(): void {
    const qty = parseFloat(this.modalQuantity());
    if (qty <= 0) return;

    this.isSubmitting.set(true);

    // Simulate API call
    setTimeout(() => {
      this.isSubmitting.set(false);
      this.tradeSuccess.set(true);

      // Auto-close after success
      setTimeout(() => {
        this.closeModal();
      }, 1400);
    }, 1000);
  }

  // Expose Math for template
  Math = Math;
  parseFloat = parseFloat;
  Object = Object;
}
  // Expose parseFloat for template use (remove last line and add this)
