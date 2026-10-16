import { Component, signal, computed, OnInit, OnDestroy, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { AccountService } from '../../../../core/services/account.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Subscription, forkJoin } from 'rxjs';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { HoldingResponse } from '../../../../core/models/holding.model';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { AccountResponse } from '../../../../core/models/account.model';
import { TradeResponse } from '../../../../core/models/trade.model';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { calculateAverageCostBasis, calculateHoldingGain } from '../../../../shared/utils/portfolio-calculation';
import { GainIndicatorComponent } from '../../../../shared/components/gain-indicator.component';
import { SparklineComponent } from '../../../../shared/components/sparkline.component';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';

/* ─────────────────────────────────────────────────────────────────────────
   FIGMA-COMPLIANT PORTFOLIO PAGE WITH REAL BACKEND DATA
───────────────────────────────────────────────────────────────────────────── */

export interface Holding {
  instrumentId: string;
  symbol: string;
  name: string;
  currency: string;
  shares: number;
  currentPrice: number;
  currentValue: number;
  gain: number;
  gainPct: number;
  avgCostBasis: number;
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
  imports: [CommonModule, FormsModule, TranslatePipe, GainIndicatorComponent, SparklineComponent, ConnectionStatusComponent],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent implements OnInit {
  private auth = inject(AuthService);
  private get ACCOUNT_ID(): string {
    return this.auth.session()?.accountId ?? '';
  }

  // Real data from backend
  allHoldings = signal<Holding[]>([]);
  account = signal<AccountResponse | null>(null);
  holdingSparklines = signal<Record<string, number[]>>({});
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
  settlementFxRate = signal<number>(1);
  settlementRateLoading = signal(false);
  settlementRateError = signal<string | null>(null);
  orderInputMode = signal<'shares' | 'cash'>('shares');
  modalCashAmount = signal<string>('');
  private quoteSubscription?: Subscription;
  private subscribedTickers = new Set<string>();

  // Filter options
  filterOptions: Array<'all' | 'gain' | 'loss'> = ['all', 'gain', 'loss'];

  constructor(
    private authService: AuthService,
    private portfolioService: PortfolioService,
    private instrumentService: InstrumentService,
    private accountService: AccountService,
    private tradesService: TradesService,
    private tradeModalService: TradeModalService,
    private marketQuoteService: MarketQuoteService,
    private marketDataService: MarketDataService,
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
          currency: instrumentToTrade.currency || 'USD',
          shares: 0,
          currentPrice: instrumentToTrade.currentPrice,
          currentValue: 0,
          gain: 0,
          gainPct: 0,
          avgCostBasis: 0
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

    this.portfolioService.getPortfolio(this.ACCOUNT_ID).subscribe({
      next: (holdingsData) => {
        this.instrumentService.getAllInstruments().subscribe({
          next: (instruments) => {
            this.tradesService.getTradesByAccount(this.ACCOUNT_ID).subscribe({
              next: (trades) => this.buildHoldingsUI(holdingsData, instruments, trades),
              error: (err) => {
                console.error('Failed to load trades:', err);
                // Still show holdings even if gain/loss data can't be computed
                this.buildHoldingsUI(holdingsData, instruments, []);
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

  private buildHoldingsUI(holdingsData: HoldingResponse[], instruments: InstrumentResponse[], trades: TradeResponse[]) {
    const instrumentMap = new Map(instruments.map(i => [i.instrumentId, i]));
    const buildHoldings: Holding[] = [];

    holdingsData.forEach(h => {
      // Only include holdings with positive quantity
      if (h.quantity <= 0) return;

      const instrument = instrumentMap.get(h.instrumentId);
      if (instrument) {
        const currentValue = h.quantity * instrument.currentPrice;
        const avgCostBasis = calculateAverageCostBasis(h.instrumentId, trades);
        const { gain, gainPct } = calculateHoldingGain(h.quantity, instrument.currentPrice, avgCostBasis);

        buildHoldings.push({
          instrumentId: h.instrumentId,
          symbol: instrument.ticker,
          name: instrument.name,
          currency: instrument.currency || 'USD',
          shares: h.quantity,
          currentPrice: instrument.currentPrice,
          currentValue: currentValue,
          gain,
          gainPct,
          avgCostBasis
        });
      }
    });

    this.allHoldings.set(buildHoldings);
    this.subscribeToLiveQuotes(buildHoldings);
    this.loadSparklines(buildHoldings);

    // Load account for cash balance
    this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
      next: (acc) => {
        this.account.set(acc);
        this.refreshSettlementRate();
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load account:', err);
        this.error.set('Failed to load account data');
        this.loading.set(false);
      }
    });
  }

  private loadSparklines(holdings: Holding[]): void {
    if (holdings.length === 0) {
      this.holdingSparklines.set({});
      return;
    }

    const requests = holdings.map(h => this.marketDataService.getHistory(h.symbol, '1mo'));
    forkJoin(requests).subscribe({
      next: (histories) => {
        const sparklines: Record<string, number[]> = {};
        histories.forEach((history, idx) => {
          sparklines[holdings[idx].symbol] = history.bars.map(b => b.close);
        });
        this.holdingSparklines.set(sparklines);
      },
      error: (err) => {
        console.error('Failed to load sparkline history:', err);
        this.holdingSparklines.set({});
      }
    });
  }


  private subscribeToLiveQuotes(holdings: Holding[]): void{
    const tickers = new Set(holdings.map(h => h.symbol));
    this.subscribedTickers.forEach(ticker => {
      if (!tickers.has(ticker)) {
        this.marketQuoteService.unsubscribe(ticker);
      }
    });
    tickers.forEach(ticker => this.marketQuoteService.subscribe(ticker));
    this.subscribedTickers = tickers;

    if (!this.quoteSubscription) {
      this.quoteSubscription = this.marketQuoteService.quotes$.subscribe(quote => {
        this.allHoldings.update(holdings =>
          holdings.map(h => {
            if (h.symbol !== quote.ticker) return h;
            const { gain, gainPct } = calculateHoldingGain(h.shares, quote.price, h.avgCostBasis);
            return { ...h, currentPrice: quote.price, currentValue: h.shares * quote.price, gain, gainPct };
          })
        );
      });
    }
  }

  ngOnDestroy(): void {
    this.quoteSubscription?.unsubscribe();
    this.subscribedTickers.forEach(ticker => this.marketQuoteService.unsubscribe(ticker));
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
  resolvedQuantity = computed(() => {
    const holding = this.selectedHolding();
    if (!holding) return 0;

    if (this.orderInputMode() === 'cash') {
      const amount = parseFloat(this.modalCashAmount()) || 0;
      const price = holding.currentPrice || 0;
      const fxRate = this.settlementFxRate();
      if (price <= 0 || fxRate <= 0 || this.settlementRateLoading() || this.settlementRateError()) {
        return 0;
      }
      // Round down to 6 decimals so a cash order never spends more than the amount typed.
      return Math.floor((amount / (price * fxRate)) * 1_000_000) / 1_000_000;
    }

    return parseFloat(this.modalQuantity()) || 0;
  });

  // Summary cards data
  summaryCards = computed((): SummaryCard[] => {
    const holdings = this.allHoldings();
    const sparklines = this.holdingSparklines();
    const totalValue = holdings.reduce((sum, h) => sum + h.currentValue, 0);
    const totalGain = holdings.reduce((sum, h) => sum + h.gain, 0);
    const cashBalance = this.account()?.balance ?? 0;

    // Approximation: "today" = change since the most recent available daily close
    const todaysGain = holdings.reduce((sum, h) => {
      const bars = sparklines[h.symbol];
      if (!bars || bars.length < 2) return sum;
      const lastClose = bars[bars.length - 2];
      return sum + (h.currentPrice - lastClose) * h.shares;
    }, 0);

    const fmt = (v: number) => `${v >= 0 ? '+' : '-'}$${Math.abs(v).toLocaleString(undefined, { maximumFractionDigits: 2 })}`;

    return [
      { label: 'Total Portfolio Value', value: `$${totalValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: true },
      { label: "Today's Gain/Loss", value: fmt(todaysGain), isPositive: todaysGain >= 0 },
      { label: 'Overall Gain/Loss', value: fmt(totalGain), isPositive: totalGain >= 0 },
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
    this.modalCashAmount.set('');
    this.orderInputMode.set('shares');
    this.isSubmitting.set(false);
    this.tradeSuccess.set(false);
    this.tradeError.set(null);
    this.refreshSettlementRate();
  }

  setOrderInputMode(mode: 'shares' | 'cash'): void {
    this.orderInputMode.set(mode);
  }


  closeModal(): void {
    this.isModalOpen.set(false);
    // Reset after modal closes
    setTimeout(() => {
      this.selectedHolding.set(null);
      this.modalQuantity.set('');
      this.modalCashAmount.set('');
      this.tradeError.set(null);
      this.settlementFxRate.set(1);
      this.settlementRateError.set(null);
    }, 300);
  }

  formatCurrency(value: number | null | undefined, currency: string | null | undefined): string {
    const amount = value ?? 0;
    const code = currency || 'USD';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(amount);
  }

  private refreshSettlementRate(): void {
    const holding = this.selectedHolding();
    const accountCurrency = (this.account()?.currency || 'USD').toUpperCase();
    const instrumentCurrency = (holding?.currency || 'USD').toUpperCase();
    if (!holding || instrumentCurrency === accountCurrency) {
      this.settlementFxRate.set(1);
      this.settlementRateLoading.set(false);
      this.settlementRateError.set(null);
      return;
    }

    this.settlementRateLoading.set(true);
    this.settlementRateError.set(null);
    this.marketDataService.getExchangeRate(instrumentCurrency, accountCurrency).subscribe({
      next: response => {
        this.settlementFxRate.set(response.rate);
        this.settlementRateLoading.set(false);
      },
      error: err => {
        console.error('Failed to load settlement FX rate:', err);
        this.settlementFxRate.set(0);
        this.settlementRateLoading.set(false);
        this.settlementRateError.set('Unable to load the current currency conversion rate');
      }
    });
  }


  confirmTrade(): void {
    const holding = this.selectedHolding();
    const qty = this.resolvedQuantity();

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
