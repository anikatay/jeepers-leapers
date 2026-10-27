import { Component, signal, computed, OnInit, OnDestroy, effect, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { AccountService } from '../../../../core/services/account.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Subscription, forkJoin, of, timer } from 'rxjs';
import { catchError, map, throttleTime } from 'rxjs/operators';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { HistoryResponse } from '../../../../core/models/market.model';
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
import { PortfolioChartComponent } from '../../../../shared/components/portfolio-chart.component';
import { normalizeIntradayBaseline, combinePortfolioHistory, PortfolioHistoryPoint } from '../../../../shared/utils/portfolio-history';
import {
  LoadStatus,
  TradePanelComponent,
  TradePanelInstrument,
  TradeSide
} from '../../components/trade-panel/trade-panel.component';

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
   FIGMA-COMPLIANT PORTFOLIO PAGE WITH REAL BACKEND DATA
â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

export interface Holding {
  instrumentId: string;
  symbol: string;
  name: string;
  currency: string;
  exchangeId?: string;
  shares: number;
  currentPrice: number;
  currentValue: number;
  gain: number;
  gainPct: number;
  avgCostBasis: number;
}

type SortKey = 'shares' | 'currentPrice' | 'currentValue' | 'gain';
type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'All';

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
  imports: [CommonModule, FormsModule, TranslatePipe, GainIndicatorComponent, SparklineComponent, ConnectionStatusComponent, TradePanelComponent, PortfolioChartComponent],
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

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // UI STATE (Signals for reactive updates)
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  searchQuery = signal<string>('');
  filterType = signal<'all' | 'gain' | 'loss'>('all');
  sortBy = signal<SortKey>('currentValue');
  sortDir = signal<'asc' | 'desc'>('desc');
  selectedPeriod = signal<Period>('1M');
  periodOptions: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'All'];
  chartPoints = signal<PortfolioHistoryPoint[]>([]);
  chartLoading = signal(false);
  accountCurrency = signal('USD');

  // Trade panel state
  isModalOpen = signal<boolean>(false);
  modalMode = signal<TradeSide>('BUY');
  selectedHolding = signal<Holding | null>(null);
  private quoteSubscription?: Subscription;
  private liveChartSubscription?: Subscription;
  private subscribedTickers = new Set<string>();
  @ViewChild(PortfolioChartComponent)
  private portfolioChart?: PortfolioChartComponent;
  private heldInstruments: Array<{ ticker: string; quantity: number }> = [];
  private lastBarCloseByTicker = new Map<string, number>();
  private lastBarValue: number | null = null;
  private allTrades: TradeResponse[] = [];
  private readonly periodMap: Record<Period, string> = {
    '1D': '1d', '1W': '1mo', '1M': '1mo', '3M': '3mo', '1Y': '1y', 'All': 'max'
  };

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
          exchangeId: instrumentToTrade.exchangeId,
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
              next: (trades) => {
                this.allTrades = trades;
                this.buildHoldingsUI(holdingsData, instruments, trades);
              },
              error: (err) => {
                console.error('Failed to load trades:', err);
                // Still show holdings even if gain/loss data can't be computed
                this.allTrades = [];
                this.buildHoldingsUI(holdingsData, instruments, []);
              }
            });
          },
          error: (err) => {
            console.error('Failed to load instruments:', err);
            this.error.set(this.translationService.t('portfolio.failed_instruments'));
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load holdings:', err);
        this.error.set(this.translationService.t('portfolio.failed_holdings'));
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
          exchangeId: instrument.exchangeId,
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
    this.heldInstruments = buildHoldings.map(holding => ({ ticker: holding.symbol, quantity: holding.shares }));
    this.subscribeToLiveQuotes(buildHoldings);
    this.loadSparklines(buildHoldings);
    this.loadChartData();

    // Load account for cash balance
    this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
      next: (acc) => {
        this.account.set(acc);
        this.accountCurrency.set(acc.currency || 'USD');
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load account:', err);
        this.error.set(this.translationService.t('portfolio.failed_account'));
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
    const selectedTicker = this.selectedHolding()?.symbol;
    if (selectedTicker) {
      tickers.add(selectedTicker);
    }

    this.subscribedTickers.forEach(ticker => {
      if (!tickers.has(ticker)) {
        this.marketQuoteService.unsubscribe(ticker);
      }
    });

    tickers.forEach(ticker => {
      if (!this.subscribedTickers.has(ticker)) {
        this.marketQuoteService.subscribe(ticker);
      }
    });
    this.subscribedTickers = tickers;

    if (!this.quoteSubscription) {
      this.quoteSubscription = this.marketQuoteService.quotes$
        .pipe(throttleTime(2000, undefined, { leading: true, trailing: true }))
        .subscribe(quote => {
          this.allHoldings.update(holdings =>
            holdings.map(h => {
              if (h.symbol !== quote.ticker) return h;
              const { gain, gainPct } = calculateHoldingGain(h.shares, quote.price, h.avgCostBasis);
              return { ...h, currentPrice: quote.price, currentValue: h.shares * quote.price, gain, gainPct };
            })
          );
          this.selectedHolding.update(holding =>
            holding?.symbol === quote.ticker
              ? { ...holding, currentPrice: quote.price }
              : holding
          );
          this.updateLiveChartPoint();
        });
    }
  }

  setPeriod(period: Period): void {
    this.selectedPeriod.set(period);
    this.loadChartData();
    this.refreshLiveChartTimer();
  }

  private refreshLiveChartTimer(): void {
    this.liveChartSubscription?.unsubscribe();
    this.liveChartSubscription = undefined;

    if (this.selectedPeriod() !== '1D' || this.heldInstruments.length === 0) return;

    this.updateLiveChartPoint();
    const millisecondsUntilNextFiveSeconds = 5_000 - (Date.now() % 5_000);
    this.liveChartSubscription = timer(millisecondsUntilNextFiveSeconds, 5_000).subscribe(() => {
      this.updateLiveChartPoint();
    });
  }

  private updateLiveChartPoint(): void {
    if (this.selectedPeriod() !== '1D' || this.lastBarValue === null) return;

    const livePriceBySymbol = new Map(this.allHoldings().map(holding => [holding.symbol, holding.currentPrice]));
    let value = this.lastBarValue;
    this.heldInstruments.forEach(({ ticker, quantity }) => {
      const barClose = this.lastBarCloseByTicker.get(ticker);
      const livePrice = livePriceBySymbol.get(ticker);
      if (barClose === undefined || livePrice === undefined || !Number.isFinite(livePrice)) return;
      value += quantity * (livePrice - barClose);
    });

    const date = new Date();
    date.setSeconds(0, 0);
    this.portfolioChart?.updateLivePoint(date.toISOString(), value);
  }

  private loadChartData(): void {
    if (this.allTrades.length === 0) {
      this.chartPoints.set([]);
      this.chartLoading.set(false);
      return;
    }

    this.chartLoading.set(true);
    const period = this.selectedPeriod();
    const isIntraday = period === '1D';
    const backendPeriod = this.periodMap[period];
    const tickers = Array.from(new Set(this.allTrades.map(trade => trade.ticker)));
    const batch$ = isIntraday
      ? this.marketDataService.getIntradayHistoryBatch(tickers)
      : this.marketDataService.getHistoryBatch(tickers, backendPeriod);

    batch$.pipe(
      map(byTicker => tickers.map(ticker => {
        const history = byTicker[ticker] ?? emptyHistory(ticker, backendPeriod);
        return isIntraday ? normalizeIntradayBaseline(history) : history;
      })),
      catchError(err => {
        console.error('Failed to load chart history:', err);
        return of(tickers.map(ticker => emptyHistory(ticker, backendPeriod)));
      })
    ).subscribe({
      next: (histories) => {
        let points = combinePortfolioHistory(this.allTrades, histories);
        this.lastBarCloseByTicker = new Map(
          histories
            .filter(history => history.bars.length > 0)
            .map(history => [history.ticker, history.bars[history.bars.length - 1].close])
        );
        this.lastBarValue = isIntraday && points.length > 0 ? points[points.length - 1].value : null;

        if (period === 'All') {
          const oldestTrade = this.allTrades.reduce((oldest, trade) =>
            new Date(trade.executedAt).getTime() < new Date(oldest.executedAt).getTime() ? trade : oldest
          );
          points = points.filter(point => point.date.slice(0, 10) >= oldestTrade.executedAt.slice(0, 10));
        } else if (period === '1W') {
          const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
          points = points.filter(point => new Date(
            point.date.length === 10 ? `${point.date}T00:00:00Z` : point.date
          ).getTime() >= cutoff);
        }

        this.chartPoints.set(points);
        this.chartLoading.set(false);
        this.refreshLiveChartTimer();
      },
      error: (err) => {
        console.error('Failed to load chart history:', err);
        this.chartPoints.set([]);
        this.chartLoading.set(false);
      }
    });
  }

  ngOnDestroy(): void {
    this.quoteSubscription?.unsubscribe();
    this.liveChartSubscription?.unsubscribe();
    this.subscribedTickers.forEach(ticker => this.marketQuoteService.unsubscribe(ticker));
  }


  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // COMPUTED VALUES (Derived state)
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
  panelInstrument = computed<TradePanelInstrument | null>(() => {
    const holding = this.selectedHolding();
    if (!holding) return null;
    return {
      instrumentId: holding.instrumentId,
      ticker: holding.symbol,
      name: holding.name,
      currency: holding.currency,
      exchangeId: holding.exchangeId
    };
  });

  // Live row for the selected holding, so the panel tracks WebSocket price updates.
  private panelHolding = computed(() => {
    const selected = this.selectedHolding();
    if (!selected) return null;
    return this.allHoldings().find(h => h.instrumentId === selected.instrumentId) ?? null;
  });

  panelPrice = computed(() => this.panelHolding()?.currentPrice ?? this.selectedHolding()?.currentPrice ?? 0);
  panelOwnedShares = computed(() => (this.loading() || this.error() ? null : this.panelHolding()?.shares ?? 0));
  panelHoldingsStatus = computed<LoadStatus>(() => (this.loading() ? 'loading' : this.error() ? 'error' : 'ready'));
  panelPreviousClose = computed(() => {
    const symbol = this.selectedHolding()?.symbol;
    const bars = symbol ? this.holdingSparklines()[symbol] : undefined;
    return bars && bars.length >= 2 ? bars[bars.length - 2] : null;
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

    this.translationService.currentLanguage();
    return [
      { label: 'portfolio.total_portfolio_value', value: `$${totalValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: true },
      { label: 'portfolio.todays_gain_loss', value: fmt(todaysGain), isPositive: todaysGain >= 0 },
      { label: 'portfolio.overall_gain_loss', value: fmt(totalGain), isPositive: totalGain >= 0 },
      { label: 'portfolio.cash_available', value: `$${cashBalance.toLocaleString(undefined, { maximumFractionDigits: 2 })}`, isPositive: undefined },
    ];
  });


  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // METHODS
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

  openTradeModal(holding: Holding, mode: TradeSide): void {
    // The frozen reason is already shown by the page-level notice.
    if (this.isAccountFrozen()) return;

    this.selectedHolding.set(holding);
    if (!this.subscribedTickers.has(holding.symbol)) {
      this.marketQuoteService.subscribe(holding.symbol);
      this.subscribedTickers.add(holding.symbol);
    }
    this.modalMode.set(mode);
    this.isModalOpen.set(true);
  }

  closeModal(): void {
    this.isModalOpen.set(false);
    // Drop the live quote subscription if the ticker is no longer held.
    const ticker = this.selectedHolding()?.symbol;
    if (ticker && !this.allHoldings().some(holding => holding.symbol === ticker)) {
      this.marketQuoteService.unsubscribe(ticker);
      this.subscribedTickers.delete(ticker);
    }
    this.selectedHolding.set(null);
  }

  onTradeCompleted(): void {
    // Panel already signals TradeModalService.notifyTradeCompleted().
    this.loadData();
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

  isAccountFrozen(): boolean {
    return this.account()?.status === 'FROZEN';
  }

  // Expose Math for template
  Math = Math;
  parseFloat = parseFloat;
  Object = Object;
}

function emptyHistory(ticker: string, period: string): HistoryResponse {
  return { ticker, period, bars: [] };
}
  // Expose parseFloat for template use (remove last line and add this)
