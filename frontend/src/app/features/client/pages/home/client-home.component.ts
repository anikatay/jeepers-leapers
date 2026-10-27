import { Component, OnInit, ViewChild, computed, signal, effect, inject, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AccountService } from '../../../../core/services/account.service';
import { AppearanceService } from '../../../../core/services/appearance.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { TradeResponse } from '../../../../core/models/trade.model';
import { Subscription, forkJoin, of, timer } from 'rxjs';
import { catchError, map, throttleTime } from 'rxjs/operators';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { HistoryResponse } from '../../../../core/models/market.model';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';
import { calculateAverageCostBasis, calculateHoldingGain } from '../../../../shared/utils/portfolio-calculation';
import {
  combinePortfolioHistory,
  normalizeIntradayBaseline,
  PortfolioHistoryPoint
} from '../../../../shared/utils/portfolio-history';
import { PortfolioChartComponent } from '../../../../shared/components/portfolio-chart.component';
import { MarketNewsComponent } from '../../../../shared/components/market-news.component';
import { SparklineComponent } from '../../../../shared/components/sparkline.component';


export interface StockHolding {
  symbol: string;
  name: string;
  currency: string;
  pct: number;
  value: number;
  price: number;
  shares: number;
  avgCostBasis: number;
}

export interface PieTooltip {
  name: string;
  ticker: string;
  shares: string;
  marketValue: string;
  allocation: string;
  pnl: string;
  pnlTone: 'positive' | 'negative' | 'na';
}

export interface Trade {
  time: string;
  symbol: string;
  type: 'Buy' | 'Sell';
  qty: number;
  price: number;
  total: number;
  currency: string;
}

type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'All';

export interface PieSlice {
  symbol: string;
  percentage: number;
  angle: number;
  startAngle: number;
  color: string;
  d: string;
}

export interface StockInsight {
  symbol: string;
  changePct: number | null;
  history: number[];
}

@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule, TranslatePipe, ConnectionStatusComponent, PortfolioChartComponent, MarketNewsComponent, SparklineComponent],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})

export class ClientHomeComponent implements OnInit {
  private auth = inject(AuthService);
  private readonly appearance = inject(AppearanceService);
  private get ACCOUNT_ID() { return this.auth.session()?.accountId ?? ''; }
  get firstName(): string {
    return this.auth.userName.trim().split(/\s+/)[0] ?? '';
  }

  greetingKey(): string {
    const hour = new Date().getHours();
    if (hour >= 5 && hour < 12) return 'home.greeting_morning';
    if (hour >= 12 && hour < 17) return 'home.greeting_afternoon';
    return 'home.greeting_evening';
  }


  holdings = signal<StockHolding[]>([]);
  completedTrades = signal<Trade[]>([]);
  pieSlices = signal<PieSlice[]>([]);
  /** True after the first non-empty donut render; stays on so live quotes do not replay the entrance. */
  readonly donutEntrance = signal(false);

  selectedPeriod = signal<Period>('1M');
  activePieIndex = signal<number | undefined>(undefined);
  periodOptions: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'All'];
  private pieFromTouch = false;

  readonly pieTooltip = computed((): PieTooltip | null => {
    const index = this.activePieIndex();
    if (index === undefined) return null;
    const holding = this.holdings()[index];
    return holding ? this.buildPieTooltip(holding) : null;
  });

  portfolioValue = signal<string>('N/A');
  todaysGain = signal<string>('N/A');
  overallGain = signal<string>('N/A');
  cashRemaining = signal<string>('N/A');

  chartPoints = signal<PortfolioHistoryPoint[]>([]);
  chartLoading = signal<boolean>(false);
  accountCurrency = signal<string>('USD');
  stockInsights = signal<StockInsight[]>([]);

  /** Holdings drive the news panel; it falls back to a default set when the account is empty. */
  newsTickers = computed(() => this.holdings().map(holding => holding.symbol));

  @ViewChild(PortfolioChartComponent)
  private portfolioChart?: PortfolioChartComponent;

  private heldInstruments: Array<{ ticker: string; quantity: number }> = [];

  private lastBarCloseByTicker = new Map<string, number>();
  private lastBarValue: number | null = null;
  private allTrades: TradeResponse[] = [];
  private quoteSubscription?: Subscription;
  private liveChartSubscription?: Subscription;
  private subscribedTickers = new Set<string>();

  // 1D uses minute history; 1W is approximated by slicing the 1mo result.
  private readonly periodMap: Record<Period, string> = {
    '1D': '1d', '1W': '1mo', '1M': '1mo', '3M': '3mo', '1Y': '1y', 'All': 'max'
  };


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

  constructor(
    private router: Router,
    private accountService: AccountService,
    private portfolioService: PortfolioService,
    private instrumentService: InstrumentService,
    private tradesService: TradesService,
    private tradeModalService: TradeModalService,
    private marketDataService: MarketDataService,
    private marketQuoteService: MarketQuoteService,
    public translationService: TranslationService
  ) {
    // Reload chart data when a trade is completed
    effect(() => {
      const _tradeSignal = this.tradeModalService.tradeCompleted();
      // When tradeCompleted signal changes, reload data
      this.loadAllData();
    });
  }

  ngOnInit() {
    this.loadAllData();
  }

  /** Stable symbol → palette index so live quotes do not reshuffle slice colors. */
  private readonly donutColorBySymbol = new Map<string, number>();

  private loadAllData() {
    // Load account info for cash remaining
    this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
      next: (account) => {
        const balance = account.balance ?? 0;
        this.accountCurrency.set(account.currency || 'USD');
        this.cashRemaining.set(
          `$${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
        );
      },
      error: (err) => {
        console.error('Failed to load account:', err);
        this.cashRemaining.set('N/A');
      }
    });

    // Load instruments, then holdings, then trades (trades feed both the recent-trades list and gain math)
    this.instrumentService.getAllInstruments().subscribe({
      next: (instruments) => {
        const instrumentMap = new Map<string, InstrumentResponse>();
        instruments.forEach(inst => instrumentMap.set(inst.instrumentId, inst));

        this.portfolioService.getPortfolio(this.ACCOUNT_ID).subscribe({
          next: (holdingsData) => {
            this.tradesService.getTradesByAccount(this.ACCOUNT_ID).subscribe({
              next: (trades) => {
                this.allTrades = trades;
                this.computeTradesUI(trades);
                this.computeHoldingsUI(holdingsData, instrumentMap, trades);
              },
              error: (err) => {
                console.error('Failed to load trades:', err);
                this.allTrades = [];
                this.completedTrades.set([]);
                this.computeHoldingsUI(holdingsData, instrumentMap, []);
              }
            });
          },
          error: (err) => {
            console.error('Failed to load holdings:', err);
            this.holdings.set([]);
            this.pieSlices.set([]);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load instruments:', err);
      }
    });
  }


  private computeHoldingsUI(holdingsData: any[], instrumentMap: Map<string, InstrumentResponse>, trades: TradeResponse[]) {
    const computedHoldings: StockHolding[] = [];
    let totalValue = 0;
    let totalGain = 0;
    this.heldInstruments = [];

    for (const h of holdingsData) {
      // Only include holdings with positive quantity
      if (h.quantity <= 0) continue;
      
      const instrument = instrumentMap.get(h.instrumentId);
      if (instrument) {
        const value = h.quantity * (instrument.currentPrice ?? 0);
        const avgCostBasis = calculateAverageCostBasis(h.instrumentId, trades);
        const { gain } = calculateHoldingGain(h.quantity, instrument.currentPrice ?? 0, avgCostBasis);

        computedHoldings.push({
          symbol: instrument.ticker,
          name: instrument.name,
          currency: instrument.currency || 'USD',
          pct: 0, // Will recalculate below
          value: value,
          price: instrument.currentPrice ?? 0,
          shares: h.quantity,
          avgCostBasis
        });
        totalValue += value;
        totalGain += gain;
        this.heldInstruments.push({ticker: instrument.ticker,quantity: h.quantity});
      }
    }

    // Recalculate percentages
    if (totalValue > 0) {
      computedHoldings.forEach(h => {
        h.pct = Math.round((h.value / totalValue) * 100);
      });
    }

    this.holdings.set(computedHoldings);
    this.portfolioValue.set(
      `$${totalValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    );
    this.setGainSignal(this.overallGain, totalGain);

    // Generate pie chart slices dynamically
    this.generatePieSlices(computedHoldings, totalValue);
    this.subscribeToLiveQuotes();
    this.loadChartData();
    this.loadTodaysGain();
  }

  private setGainSignal(target: ReturnType<typeof signal<string>>, value: number): void {
    const sign = value >= 0 ? '+' : '-';
    target.set(`${sign}$${Math.abs(value).toLocaleString(undefined, { maximumFractionDigits: 2 })}`);
  }

  private loadTodaysGain(): void {
    if (this.heldInstruments.length === 0) {
      this.stockInsights.set([]);
      this.todaysGain.set('N/A');
      return;
    }

    const requests = this.heldInstruments.map(h => this.marketDataService.getHistory(h.ticker, '1mo'));
    forkJoin(requests).subscribe({
      next: (histories) => {
        this.stockInsights.set(histories.map((history, idx) => this.buildStockInsight(
          this.heldInstruments[idx].ticker,
          history
        )));
        let totalTodaysGain = 0;
        histories.forEach((history, idx) => {
          const bars = history.bars;
          if (bars.length < 2) return;
          const lastClose = bars[bars.length - 2].close;
          const holding = this.holdings().find(h => h.symbol === this.heldInstruments[idx].ticker);
          const currentPrice = holding?.price ?? bars[bars.length - 1].close;
          totalTodaysGain += (currentPrice - lastClose) * this.heldInstruments[idx].quantity;
        });
        this.setGainSignal(this.todaysGain, totalTodaysGain);
      },
      error: (err) => {
        console.error("Failed to load today's gain:", err);
        this.stockInsights.set([]);
        this.todaysGain.set('N/A');
      }
    });
  }

  private buildStockInsight(symbol: string, history: HistoryResponse): StockInsight {
    const closes = history.bars
      .map(bar => bar.close)
      .filter(value => Number.isFinite(value));
    const previousClose = closes.length > 1 ? closes[closes.length - 2] : undefined;
    const currentPrice = this.holdings().find(holding => holding.symbol === symbol)?.price;
    const changePct = previousClose && previousClose !== 0 && currentPrice !== undefined
      ? ((currentPrice - previousClose) / previousClose) * 100
      : null;

    return { symbol, changePct, history: closes };
  }


  private sliceColor(symbol: string): string {
    let index = this.donutColorBySymbol.get(symbol);
    if (index === undefined) {
      index = this.donutColorBySymbol.size;
      this.donutColorBySymbol.set(symbol, index);
    }
    return this.appearance.donutSliceColor(index);
  }

  private generatePieSlices(holdings: StockHolding[], totalValue: number) {
    const slices: PieSlice[] = [];
    let currentAngle = -90; // Start at top (negative 90 in SVG coordinates)

    holdings.forEach((holding) => {
      const percentage = totalValue > 0 ? (holding.value / totalValue) * 100 : 0;
      const angle = (percentage / 100) * 360; // Convert percentage to degrees

      const slice: PieSlice = {
        symbol: holding.symbol,
        percentage: Math.round(percentage),
        angle: angle,
        startAngle: currentAngle,
        color: this.sliceColor(holding.symbol),
        d: this.calculatePiePath(currentAngle, angle)
      };

      slices.push(slice);
      currentAngle += angle;
    });

    this.pieSlices.set(slices);
    if (slices.length > 0) {
      this.donutEntrance.set(true);
    }
  }

  private calculatePiePath(startAngle: number, angle: number): string {
    const radius = 90;
    const largeArc = angle > 180 ? 1 : 0;

    // Center of pie chart
    const cx = 100;
    const cy = 100;

    // Calculate start point
    const startRad = (startAngle * Math.PI) / 180;
    const x1 = cx + radius * Math.cos(startRad);
    const y1 = cy + radius * Math.sin(startRad);

    // Calculate end point
    const endRad = ((startAngle + angle) * Math.PI) / 180;
    const x2 = cx + radius * Math.cos(endRad);
    const y2 = cy + radius * Math.sin(endRad);

    return `M ${cx} ${cy} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`;
  }

  private computeTradesUI(tradeResponses: TradeResponse[]) {
    const trades: Trade[] = tradeResponses.slice(0, 4).map(t => ({
      time: t.executedAt ? new Date(t.executedAt).toLocaleTimeString() : 'N/A',
      symbol: t.ticker ?? 'N/A',
      type: t.side === 'BUY' ? 'Buy' : 'Sell',
      qty: t.quantity ?? 0,
      price: t.executionPrice ?? 0,
      total: t.tradeValue ?? 0,
      currency: t.instrumentCurrency || 'USD'
    }));
    this.completedTrades.set(trades);
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

  insightFor(symbol: string): StockInsight | undefined {
    return this.stockInsights().find(insight => insight.symbol === symbol);
  }

  scrollInsights(direction: 'previous' | 'next', container: HTMLElement): void {
    const firstCard = container.querySelector<HTMLElement>('.insight-card');
    if (!firstCard) return;

    const gap = Number.parseFloat(getComputedStyle(container).columnGap) || 0;
    const cardStep = firstCard.offsetWidth + gap;
    const visibleCards = Math.max(1, Math.round(container.clientWidth / cardStep));

    container.scrollBy({
      left: direction === 'next' ? cardStep * visibleCards : -cardStep * visibleCards,
      behavior: 'smooth'
    });
  }

  setPeriod(period: Period) {
    this.selectedPeriod.set(period);
    this.loadChartData();
    this.refreshLiveChartTimer();
  }

  private refreshLiveChartTimer(): void {
    this.liveChartSubscription?.unsubscribe();
    this.liveChartSubscription = undefined;

    if (this.selectedPeriod() !== '1D' || this.heldInstruments.length === 0) {
      return;
    }

    this.updateLiveChartPoint();

    const millisecondsUntilNextFiveSeconds = 5_000 - (Date.now() % 5_000);
    this.liveChartSubscription = timer(millisecondsUntilNextFiveSeconds, 5_000).subscribe(() => {
      this.updateLiveChartPoint();
    });
  }

  private updateLiveChartPoint(): void {
    if (this.selectedPeriod() !== '1D' || this.lastBarValue === null) {
      return;
    }

    const livePriceBySymbol = new Map(this.holdings().map(holding => [holding.symbol, holding.price]));
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

  private subscribeToLiveQuotes(): void {
    const tickers = new Set(this.heldInstruments.map(h => h.ticker));

    this.subscribedTickers.forEach(ticker => {
      if (!tickers.has(ticker)) {
        this.marketQuoteService.unsubscribe(ticker, this);
      }
    });
    tickers.forEach(ticker => {
      if (!this.subscribedTickers.has(ticker)) {
        this.marketQuoteService.subscribe(ticker, this);
      }
    });
    this.subscribedTickers = tickers;

    if (!this.quoteSubscription) {
      // Throttle so bursts of ticker pushes don't repaint the UI on every tick
      this.quoteSubscription = this.marketQuoteService.quotes$
        .pipe(throttleTime(2000, undefined, { leading: true, trailing: true }))
        .subscribe(quote => {
          if (!this.subscribedTickers.has(quote.ticker)) return;
          const updated = this.holdings().map(h =>
            h.symbol === quote.ticker
              ? { ...h, price: quote.price, value: (h.value / h.price) * quote.price }
              : h
          );
          const totalValue = updated.reduce((sum, h) => sum + h.value, 0);
          const totalGain = updated.reduce((sum, h) => {
            const shares = h.price > 0 ? h.value / h.price : 0;
            return sum + (h.price - h.avgCostBasis) * shares;
          }, 0);
          const withPct = updated.map(h => ({
            ...h,
            pct: totalValue > 0 ? Math.round((h.value / totalValue) * 100) : 0
          }));

          this.holdings.set(withPct);
          this.portfolioValue.set(
            `$${totalValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
          );
          this.setGainSignal(this.overallGain, totalGain);
          this.generatePieSlices(withPct, totalValue);
        });
    }

    this.refreshLiveChartTimer();
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

    const histories$ = batch$.pipe(
      map(byTicker => tickers.map(ticker => {
        const history = byTicker[ticker] ?? emptyHistory(ticker, backendPeriod);
        return isIntraday ? normalizeIntradayBaseline(history) : history;
      })),
      catchError(err => {
        console.error('Failed to load chart history:', err);
        return of(tickers.map(ticker => emptyHistory(ticker, backendPeriod)));
      })
    );

    histories$.subscribe({
      next: (histories) => {
        let points = combinePortfolioHistory(this.allTrades, histories);

        // Anchor for the live tail: the closing price each ticker was last valued at.
        this.lastBarCloseByTicker = new Map(
          histories
            .filter(history => history.bars.length > 0)
            .map(history => [history.ticker, history.bars[history.bars.length - 1].close])
        );
        this.lastBarValue = isIntraday && points.length > 0
          ? points[points.length - 1].value
          : null;

        if (period === 'All') {
          const oldestTrade = this.allTrades.reduce((oldest, trade) =>
            new Date(trade.executedAt).getTime() < new Date(oldest.executedAt).getTime()
              ? trade
              : oldest
          );
          const oldestTradeDate = oldestTrade.executedAt.slice(0, 10);
          points = points.filter(point => point.date.slice(0, 10) >= oldestTradeDate);
        } else if (period === '1W') {
          // A trading week by elapsed time, not by bar count, which drifts across holidays.
          const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
          points = points.filter(point => new Date(
            point.date.length === 10 ? `${point.date}T00:00:00Z` : point.date
          ).getTime() >= cutoff);
        }

        this.chartPoints.set(points);
        this.chartLoading.set(false);
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
    this.subscribedTickers.forEach(ticker => this.marketQuoteService.unsubscribe(ticker, this));
  }


  setPieActive(index: number) {
    this.activePieIndex.set(index);
  }

  clearPieActive() {
    this.activePieIndex.set(undefined);
  }

  onPieEnter(index: number, event: PointerEvent): void {
    if (event.pointerType === 'mouse') {
      this.pieFromTouch = false;
      this.setPieActive(index);
    }
  }

  onPieLeave(event: PointerEvent): void {
    if (event.pointerType === 'mouse' && !this.pieFromTouch) {
      this.clearPieActive();
    }
  }

  onPieUp(index: number, event: PointerEvent): void {
    if (event.pointerType === 'mouse') return;
    event.preventDefault();
    this.pieFromTouch = true;
    this.activePieIndex.update(current => current === index ? undefined : index);
  }

  @HostListener('document:pointerdown', ['$event'])
  onDocumentPointerDown(event: PointerEvent): void {
    if (this.activePieIndex() === undefined || event.pointerType === 'mouse') return;
    const target = event.target as HTMLElement | null;
    if (target?.closest('.pie-slice')) return;
    this.clearPieActive();
    this.pieFromTouch = false;
  }

  onStockClick(holding: StockHolding) {
    if (holding.symbol !== 'Others') {
      console.log('Navigate to stock:', holding.symbol);
    }
  }

  private buildPieTooltip(holding: StockHolding): PieTooltip {
    const name = holding.name?.trim() || 'N/A';
    const ticker = holding.symbol?.trim() || 'N/A';
    const shares = holding.shares > 0 ? this.formatShares(holding.shares) : 'N/A';
    const marketValue = Number.isFinite(holding.value)
      ? this.formatCurrency(holding.value, holding.currency || 'USD')
      : 'N/A';
    const allocation = Number.isFinite(holding.pct) ? `${holding.pct}%` : 'N/A';

    let pnl = 'N/A';
    let pnlTone: PieTooltip['pnlTone'] = 'na';
    if (holding.avgCostBasis > 0 && holding.shares > 0 && holding.price > 0) {
      const { gain, gainPct } = calculateHoldingGain(holding.shares, holding.price, holding.avgCostBasis);
      const sign = gain >= 0 ? '+' : '-';
      const amount = this.formatCurrency(Math.abs(gain), holding.currency || 'USD');
      pnl = `${sign}${amount} (${sign}${Math.abs(gainPct).toFixed(2)}%)`;
      pnlTone = gain > 0 ? 'positive' : gain < 0 ? 'negative' : 'na';
    }

    return { name, ticker, shares, marketValue, allocation, pnl, pnlTone };
  }

  private formatShares(value: number): string {
    return value.toLocaleString('en-US', { maximumFractionDigits: 4 });
  }

  goToPortfolio() {
    this.router.navigate(['/client/portfolio']);
  }

  goToTrades() {
    this.router.navigate(['/client/trades']);
  }

  handleProfileOption(option: string) {
    console.log('Profile option:', option);
  }
}

/** Stand-in for a ticker whose history could not be loaded, so it values at zero. */
function emptyHistory(ticker: string, period: string): HistoryResponse {
  return { ticker, period, bars: [] };
}
