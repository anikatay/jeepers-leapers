import { Component, signal, computed, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { AccountService } from '../../../../core/services/account.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { HoldingResponse } from '../../../../core/models/holding.model';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { AccountResponse } from '../../../../core/models/account.model';

/* ─────────────────────────────────────────────────────────────────────────
   FIGMA-COMPLIANT PORTFOLIO PAGE WITH REAL BACKEND DATA
───────────────────────────────────────────────────────────────────────────── */

export interface Holding {
  instrumentId: string;
  symbol: string;
  name: string;
  shares: number;
  currentPrice: number;
  currentValue: number;
  gain: number;
  gainPct: number;
}

type SortKey = 'shares' | 'currentPrice' | 'currentValue' | 'gain';

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
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent implements OnInit {

  private readonly ACCOUNT_ID = 'd4000000-0000-0000-0000-000000000001';

  // Real data from backend
  allHoldings = signal<Holding[]>([]);
  account = signal<AccountResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

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
  tradeError = signal<string | null>(null);

  // Filter options
  filterOptions: Array<'all' | 'gain' | 'loss'> = ['all', 'gain', 'loss'];

  constructor(
    private portfolioService: PortfolioService,
    private instrumentService: InstrumentService,
    private accountService: AccountService,
    private tradesService: TradesService,
    private tradeModalService: TradeModalService,
    public translationService: TranslationService
  ) {
    // Watch for instrument changes from navbar trade request
    effect(() => {
      const instrumentToTrade = this.tradeModalService.instrumentToTrade();
      if (instrumentToTrade) {
        // Create a holding object to pass to openTradeModal
        const holding: Holding = {
          instrumentId: instrumentToTrade.instrumentId,
          symbol: instrumentToTrade.ticker,
          name: instrumentToTrade.name,
          shares: 0,
          currentPrice: instrumentToTrade.currentPrice,
          currentValue: 0,
          gain: 0,
          gainPct: 0
        };
        this.openTradeModal(holding, 'BUY');
        // Clear the signal after handling
        this.tradeModalService.clearInstrument();
      }
    });
  }

  ngOnInit() {
    this.loadData();

  }

  private loadData() {
    this.loading.set(true);
    this.error.set(null);

    // Load holdings and instruments in parallel
    this.portfolioService.getPortfolio(this.ACCOUNT_ID).subscribe({
      next: (holdings) => {
        this.instrumentService.getAllInstruments().subscribe({
          next: (instruments) => {
            // Build holdings with instrument data
            const instrumentMap = new Map(instruments.map(i => [i.instrumentId, i]));
            const buildHoldings: Holding[] = [];

            holdings.forEach(h => {
              // Only include holdings with positive quantity
              if (h.quantity <= 0) return;
              
              const instrument = instrumentMap.get(h.instrumentId);
              if (instrument) {
                const currentValue = h.quantity * instrument.currentPrice;
                buildHoldings.push({
                  instrumentId: h.instrumentId,
                  symbol: instrument.ticker,
                  name: instrument.name,
                  shares: h.quantity,
                  currentPrice: instrument.currentPrice,
                  currentValue: currentValue,
                  gain: 0, // N/A - backend doesn't provide
                  gainPct: 0 // N/A - backend doesn't provide
                });
              }
            });

            this.allHoldings.set(buildHoldings);

            // Load account for cash balance
            this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
              next: (acc) => {
                this.account.set(acc);
                this.loading.set(false);
              },
              error: (err) => {
                console.error('Failed to load account:', err);
                this.error.set('Failed to load account data');
                this.loading.set(false);
              }
            });
          },
          error: (err) => {
            console.error('Failed to load instruments:', err);
            this.error.set('Failed to load instruments');
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load holdings:', err);
        this.error.set('Failed to load holdings');
        this.loading.set(false);
      }
    });
  }

  // ─────────────────────────────────────────────────────────────────
  // COMPUTED VALUES (Derived state)
  // ─────────────────────────────────────────────────────────────────

  filteredAndSorted = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const filter = this.filterType();
    const sortKey = this.sortBy();
    const sortDirection = this.sortDir();

    // Filter by search query and gain/loss
    let result = this.allHoldings().filter(h => {
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
    const totalValue = this.allHoldings().reduce((sum, h) => sum + h.currentValue, 0);
    const totalGain = this.allHoldings().reduce((sum, h) => sum + h.gain, 0);
    const cashBalance = this.account()?.balance ?? 0;

    return [
      { label: 'Total Portfolio Value', value: `$${totalValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: true },
      { label: "Today's Gain/Loss", value: 'N/A', isPositive: undefined },
      { label: 'Overall Gain/Loss', value: 'N/A', isPositive: undefined },
      { label: 'Cash Available', value: `$${cashBalance.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: undefined },
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
    this.tradeError.set(null);
  }

  closeModal(): void {
    this.isModalOpen.set(false);
    // Reset after modal closes
    setTimeout(() => {
      this.selectedHolding.set(null);
      this.modalQuantity.set('');
      this.tradeError.set(null);
    }, 300);
  }

  confirmTrade(): void {
    const holding = this.selectedHolding();
    const qty = parseInt(this.modalQuantity(), 10);
    
    if (!holding || qty <= 0) {
      this.tradeError.set('Invalid quantity');
      return;
    }

    this.isSubmitting.set(true);
    this.tradeError.set(null);

    const tradeRequest = {
      accountId: this.ACCOUNT_ID,
      instrumentId: holding.instrumentId,
      side: this.modalMode(),
      quantity: qty
    };

    this.tradesService.createTrade(tradeRequest).subscribe({
      next: (response) => {
        this.isSubmitting.set(false);
        this.tradeSuccess.set(true);

        // Reload data after successful trade
        setTimeout(() => {
          this.loadData();
          // Notify all components that a trade was completed (for chart refresh, etc.)
          this.tradeModalService.notifyTradeCompleted();
          this.closeModal();
        }, 1400);
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const errorMessage = err?.error?.message || 
                           err?.message || 
                           'Trade failed';
        this.tradeError.set(errorMessage);
        console.error('Trade error:', err);
      }
    });
  }

  // Expose Math for template
  Math = Math;
  parseFloat = parseFloat;
  Object = Object;
}
  // Expose parseFloat for template use (remove last line and add this)
