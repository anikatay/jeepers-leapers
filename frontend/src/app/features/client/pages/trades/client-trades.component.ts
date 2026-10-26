import { Component, inject , OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, from } from 'rxjs';
import { mergeMap, throttleTime, toArray } from 'rxjs/operators';
import { TradesService } from '../../../../core/services/trades.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { TradePageFilters, TradeResponse } from '../../../../core/models/trade.model';
import { AuthService } from '../../../../core/services/auth.service';
import { HoldingResponse } from '../../../../core/models/holding.model';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { calculateAverageCostBasis, calculateHoldingGain } from '../../../../shared/utils/portfolio-calculation';
import { combinePortfolioHistory } from '../../../../shared/utils/portfolio-history';
import { GainIndicatorComponent } from '../../../../shared/components/gain-indicator.component';
import { SparklineComponent } from '../../../../shared/components/sparkline.component';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';


interface Trade {
  id?: string;
  date: string;
  time: string;
  symbol: string;
  name: string;
  currency: string;
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
  imports: [CommonModule, FormsModule, TranslatePipe, GainIndicatorComponent, SparklineComponent, ConnectionStatusComponent],
  templateUrl: './client-trades.component.html',
  styleUrl: './client-trades.component.css'
})
export class ClientTradesComponent implements OnInit {
  private auth = inject(AuthService);
  private get ACCOUNT_ID(): string {
    return this.auth.session()?.accountId ?? '';
  }

  // Real trade data from backend
  allTrades = signal<Trade[]>([]);
  pagedTrades = signal<Trade[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  pageLoading = signal(false);
  pageError = signal<string | null>(null);
  tradePage = signal(0);
  tradeTotalPages = signal(0);
  tradeTotalItems = signal(0);
  readonly tradePageSize = 10;

  // Filter controls
  search = signal('');
  typeFilter = signal<TypeFilter>('all');
  fromDate = signal('');
  toDate = signal('');
  minQuantity = signal('');
  maxQuantity = signal('');
  minPrice = signal('');
  maxPrice = signal('');
  minTotal = signal('');
  maxTotal = signal('');
  currencyFilter = signal('all');
  sortOrder = signal<SortOrder>('newest');
  showFilterPanel = signal(false);
  overallGain = signal<number>(0);
  overallGainPct = signal<number>(0);
  portfolioSparkline = signal<number[]>([]);

  private heldPositions: Array<{ ticker: string; quantity: number; currentPrice: number; avgCostBasis: number }> = [];
  private allTradeResponses: TradeResponse[] = [];
  private quoteSubscription?: Subscription;
  private subscribedTickers = new Set<string>();

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


  constructor(
    private authService: AuthService,
    private tradesService: TradesService,
    private instrumentService: InstrumentService,
    private portfolioService: PortfolioService,
    private marketDataService: MarketDataService,
    private marketQuoteService: MarketQuoteService,
    public translationService: TranslationService
  ) {}

  ngOnInit() {
    this.loadTrades();
    this.loadPagedTrades();
    this.loadOverallPnl();
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

  private loadPagedTrades(page = this.tradePage()): void {
    this.pageLoading.set(true);
    this.pageError.set(null);
    this.tradesService.getTradesPage(
      this.ACCOUNT_ID,
      page,
      this.tradePageSize,
      this.getTradePageFilters()
    ).subscribe({
      next: response => {
        this.pagedTrades.set(response.items.map(trade => this.mapToTrade(trade)));
        this.tradePage.set(response.page);
        this.tradeTotalPages.set(response.totalPages);
        this.tradeTotalItems.set(response.totalItems);
        this.pageLoading.set(false);
      },
      error: err => {
        console.error('Failed to load paged trades:', err);
        this.pageError.set('Failed to load trades');
        this.pagedTrades.set([]);
        this.pageLoading.set(false);
      }
    });
  }

  private getTradePageFilters(): TradePageFilters {
    return {
      search: this.search(),
      side: this.typeFilter() === 'all' ? undefined : this.typeFilter().toUpperCase(),
      currency: this.currencyFilter() === 'all' ? undefined : this.currencyFilter(),
      fromDate: this.fromDate() || undefined,
      toDate: this.toDate() || undefined,
      minQuantity: this.parseFilterValue(this.minQuantity()) ?? undefined,
      maxQuantity: this.parseFilterValue(this.maxQuantity()) ?? undefined,
      minPrice: this.parseFilterValue(this.minPrice()) ?? undefined,
      maxPrice: this.parseFilterValue(this.maxPrice()) ?? undefined,
      minTotal: this.parseFilterValue(this.minTotal()) ?? undefined,
      maxTotal: this.parseFilterValue(this.maxTotal()) ?? undefined,
      sortOrder: this.sortOrder()
    };
  }

  private resetTradePage(): void {
    this.tradePage.set(0);
    this.loadPagedTrades(0);
  }

  previousTradesPage(): void {
    if (this.tradePage() > 0) {
      this.loadPagedTrades(this.tradePage() - 1);
    }
  }

  nextTradesPage(): void {
    if (this.tradePage() + 1 < this.tradeTotalPages()) {
      this.loadPagedTrades(this.tradePage() + 1);
    }
  }

    private loadOverallPnl(): void {
    this.portfolioService.getPortfolio(this.ACCOUNT_ID).subscribe({
      next: (holdingsData) => {
        this.instrumentService.getAllInstruments().subscribe({
          next: (instruments) => {
            this.tradesService.getTradesByAccount(this.ACCOUNT_ID).subscribe({
              next: (trades) => this.computeOverallPnl(holdingsData, instruments, trades),
              error: (err) => console.error('Failed to load trades for P&L:', err)
            });
          },
          error: (err) => console.error('Failed to load instruments for P&L:', err)
        });
      },
      error: (err) => console.error('Failed to load holdings for P&L:', err)
    });
  }

  private computeOverallPnl(holdingsData: HoldingResponse[], instruments: InstrumentResponse[], trades: TradeResponse[]): void {
    const instrumentMap = new Map(instruments.map(i => [i.instrumentId, i]));
    this.allTradeResponses = trades;
    this.heldPositions = [];

    holdingsData.forEach(h => {
      if (h.quantity <= 0) return;
      const instrument = instrumentMap.get(h.instrumentId);
      if (!instrument) return;

      const avgCostBasis = calculateAverageCostBasis(h.instrumentId, trades);
      this.heldPositions.push({
        ticker: instrument.ticker,
        quantity: h.quantity,
        currentPrice: instrument.currentPrice,
        avgCostBasis
      });
    });

    this.recomputeOverallGain();
    this.subscribeToLiveQuotes();
    this.loadPortfolioSparkline();
  }

  private recomputeOverallGain(): void {
    let totalGain = 0;
    let totalCostBasis = 0;

    this.heldPositions.forEach(p => {
      const { gain } = calculateHoldingGain(p.quantity, p.currentPrice, p.avgCostBasis);
      totalGain += gain;
      totalCostBasis += p.avgCostBasis * p.quantity;
    });

    this.overallGain.set(totalGain);
    this.overallGainPct.set(totalCostBasis > 0 ? (totalGain / totalCostBasis) * 100 : 0);
  }

  private subscribeToLiveQuotes(): void {
    const tickers = new Set(this.heldPositions.map(p => p.ticker));

    this.subscribedTickers.forEach(ticker => {
      if (!tickers.has(ticker)) {
        this.marketQuoteService.unsubscribe(ticker);
      }
    });
    tickers.forEach(ticker => this.marketQuoteService.subscribe(ticker));
    this.subscribedTickers = tickers;

    if (!this.quoteSubscription) {
      this.quoteSubscription = this.marketQuoteService.quotes$
        .pipe(throttleTime(2000, undefined, { leading: true, trailing: true }))
        .subscribe(quote => {
          this.heldPositions = this.heldPositions.map(p =>
            p.ticker === quote.ticker ? { ...p, currentPrice: quote.price } : p
          );
          this.recomputeOverallGain();
        });
    }
  }

  private loadPortfolioSparkline(): void {
    if (this.allTradeResponses.length === 0) {
      this.portfolioSparkline.set([]);
      return;
    }

    const tickers = Array.from(new Set(this.allTradeResponses.map(trade => trade.ticker)));
    from(tickers).pipe(
      mergeMap(
        ticker => this.marketDataService.getHistory(ticker, '1mo'),
        3
      ),
      toArray()
    ).subscribe({
      next: (histories) => {
        const points = combinePortfolioHistory(this.allTradeResponses, histories);
        this.portfolioSparkline.set(points.map(p => p.value));
      },
      error: (err) => {
        console.error('Failed to load portfolio sparkline:', err);
        this.portfolioSparkline.set([]);
      }
    });
  }

  ngOnDestroy(): void {
    this.quoteSubscription?.unsubscribe();
    this.subscribedTickers.forEach(ticker => this.marketQuoteService.unsubscribe(ticker));
  }


  private mapToTrade(response: TradeResponse): Trade {
    const dateTime = new Date(response.executedAt);
    return {
      id: response.tradeId,
      date: dateTime.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }),
      time: dateTime.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true }),
      symbol: response.ticker,
      name: response.name,
      currency: response.instrumentCurrency || 'USD',
      type: response.side === 'BUY' ? 'Buy' : 'Sell',
      qty: response.quantity,
      price: response.executionPrice,
      total: response.tradeValue
    };
  }

  formatCurrency(value: number, currency: string): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(value);
  }

  getStockIcon(symbol: string) {
    return this.stockIconStyles[symbol] ?? {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  get availableCurrencies(): string[] {
    return Array.from(new Set(this.allTrades().map(trade => trade.currency))).sort();
  }

  private parseFilterValue(value: string): number | null {
    if (!value.trim()) return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  get filteredTrades(): Trade[] {
    return this.pagedTrades();
  }

  get hasActiveFilters(): boolean {
    return this.search().trim() !== '' || 
           this.typeFilter() !== 'all' || 
           this.fromDate() !== '' || 
           this.toDate() !== '' || 
           this.minQuantity() !== '' ||
           this.maxQuantity() !== '' ||
           this.minPrice() !== '' ||
           this.maxPrice() !== '' ||
           this.minTotal() !== '' ||
           this.maxTotal() !== '' ||
           this.currencyFilter() !== 'all';
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
    this.minQuantity.set('');
    this.maxQuantity.set('');
    this.minPrice.set('');
    this.maxPrice.set('');
    this.minTotal.set('');
    this.maxTotal.set('');
    this.currencyFilter.set('all');
    this.sortOrder.set('newest');
    this.showFilterPanel.set(false);
    this.resetTradePage();
  }

  toggleFilterPanel() {
    this.showFilterPanel.update(v => !v);
  }

  onSearchChange(value: string) {
    this.search.set(value);
    this.resetTradePage();
  }

  onTypeFilterChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.typeFilter.set(value as TypeFilter);
    this.resetTradePage();
  }

  onCurrencyFilterChange(event: Event) {
    this.currencyFilter.set((event.target as HTMLSelectElement).value);
    this.resetTradePage();
  }

  onMinQuantityChange(event: Event) {
    this.minQuantity.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onMaxQuantityChange(event: Event) {
    this.maxQuantity.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onMinPriceChange(event: Event) {
    this.minPrice.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onMaxPriceChange(event: Event) {
    this.maxPrice.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onMinTotalChange(event: Event) {
    this.minTotal.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onMaxTotalChange(event: Event) {
    this.maxTotal.set((event.target as HTMLInputElement).value);
    this.resetTradePage();
  }

  onSortChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.sortOrder.set(value as SortOrder);
    this.resetTradePage();
  }

  onFromDateChange(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.fromDate.set(value);
    this.resetTradePage();
  }

  onToDateChange(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.toDate.set(value);
    this.resetTradePage();
  }

  openNewTrade() {
    // Redirect to Portfolio to use its trade modal
    window.location.href = '/client/portfolio';
  }

}
