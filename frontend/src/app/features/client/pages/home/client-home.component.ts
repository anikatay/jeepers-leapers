import { Component, OnInit, OnDestroy, ViewChild, signal, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AccountService } from '../../../../core/services/account.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { TradeResponse } from '../../../../core/models/trade.model';
import { Subscription, from, forkJoin, timer } from 'rxjs';
import { mergeMap, throttleTime, toArray } from 'rxjs/operators';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';
import { calculateAverageCostBasis, calculateHoldingGain } from '../../../../shared/utils/portfolio-calculation';
import { combinePortfolioHistory, PortfolioHistoryPoint } from '../../../../shared/utils/portfolio-history';
import { PortfolioChartComponent } from '../../../../shared/components/portfolio-chart.component';


export interface StockHolding {
  symbol: string;
  name: string;
  currency: string;
  pct: number;
  value: number;
  price: number;
  avgCostBasis: number;
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

@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule, TranslatePipe, ConnectionStatusComponent, PortfolioChartComponent],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})

export class ClientHomeComponent implements OnInit {
  private auth = inject(AuthService);
  private get ACCOUNT_ID() { return this.auth.session()?.accountId ?? ''; }
  printname() {
    return {
      userInitial: this.auth.userName.charAt(0),
      userName: this.auth.userName
    };
  }

  user = this.printname();


  holdings = signal<StockHolding[]>([]);
  completedTrades = signal<Trade[]>([]);
  pieSlices = signal<PieSlice[]>([]);

  selectedPeriod = signal<Period>('1M');
  activePieIndex = signal<number | undefined>(undefined);
  periodOptions: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'All'];

  portfolioValue = signal<string>('N/A');
  todaysGain = signal<string>('N/A');
  overallGain = signal<string>('N/A');
  cashRemaining = signal<string>('N/A');

  chartPoints = signal<PortfolioHistoryPoint[]>([]);
  chartLoading = signal<boolean>(false);

  @ViewChild(PortfolioChartComponent)
  private portfolioChart?: PortfolioChartComponent;

  private heldInstruments: Array<{ ticker: string; quantity: number }> = [];
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

  private pieColors = [
    '#0ea5e9', // Cyan
    '#ec4899', // Pink
    '#f59e0b', // Amber
    '#8b5cf6', // Violet
    '#6366f1'  // Indigo
  ];

  private loadAllData() {
    // Load account info for cash remaining
    this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
      next: (account) => {
        const balance = account.balance ?? 0;
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
      this.todaysGain.set('N/A');
      return;
    }

    const requests = this.heldInstruments.map(h => this.marketDataService.getHistory(h.ticker, '1mo'));
    forkJoin(requests).subscribe({
      next: (histories) => {
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
        this.todaysGain.set('N/A');
      }
    });
  }


  private generatePieSlices(holdings: StockHolding[], totalValue: number) {
    const slices: PieSlice[] = [];
    let currentAngle = -90; // Start at top (negative 90 in SVG coordinates)

    holdings.forEach((holding, index) => {
      const percentage = totalValue > 0 ? (holding.value / totalValue) * 100 : 0;
      const angle = (percentage / 100) * 360; // Convert percentage to degrees

      const slice: PieSlice = {
        symbol: holding.symbol,
        percentage: Math.round(percentage),
        angle: angle,
        startAngle: currentAngle,
        color: this.pieColors[index % this.pieColors.length],
        d: this.calculatePiePath(currentAngle, angle)
      };

      slices.push(slice);
      currentAngle += angle;
    });

    this.pieSlices.set(slices);
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

    // Create path: M (move to center), L (line to start), A (arc), Z (close)
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

    const totalValue = this.holdings().reduce((sum, holding) => sum + holding.value, 0);
    this.updateLiveChartPoint(totalValue);

    const millisecondsUntilNextFiveSeconds = 5_000 - (Date.now() % 5_000);
    this.liveChartSubscription = timer(millisecondsUntilNextFiveSeconds, 5_000).subscribe(() => {
      const totalValue = this.holdings().reduce((sum, holding) => sum + holding.value, 0);
      this.updateLiveChartPoint(totalValue);
    });
  }

  private updateLiveChartPoint(value: number): void {
    if (this.selectedPeriod() !== '1D') {
      return;
    }

    const date = new Date();
    date.setSeconds(0, 0);
    this.portfolioChart?.updateLivePoint(date.toISOString(), value);
  }

  private subscribeToLiveQuotes(): void {
    const tickers = new Set(this.heldInstruments.map(h => h.ticker));

    this.subscribedTickers.forEach(ticker => {
      if (!tickers.has(ticker)) {
        this.marketQuoteService.unsubscribe(ticker);
      }
    });
    tickers.forEach(ticker => this.marketQuoteService.subscribe(ticker));
    this.subscribedTickers = tickers;

    if (!this.quoteSubscription) {
      // Throttle so bursts of ticker pushes don't repaint the UI on every tick
      this.quoteSubscription = this.marketQuoteService.quotes$
        .pipe(throttleTime(2000, undefined, { leading: true, trailing: true }))
        .subscribe(quote => {
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
    const isIntraday = this.selectedPeriod() === '1D';
    const backendPeriod = this.periodMap[this.selectedPeriod()];
    const tickers = Array.from(new Set(this.allTrades.map(trade => trade.ticker)));

    from(tickers).pipe(
      mergeMap(
        ticker => isIntraday
          ? this.marketDataService.getIntradayHistory(ticker)
          : this.marketDataService.getHistory(ticker, backendPeriod),
        3
      ),
      toArray()
    ).subscribe({
      next: (histories) => {
        let points = combinePortfolioHistory(this.allTrades, histories);
        if (this.selectedPeriod() === 'All') {
          const oldestTrade = this.allTrades.reduce((oldest, trade) =>
            new Date(trade.executedAt).getTime() < new Date(oldest.executedAt).getTime()
              ? trade
              : oldest
          );
          const oldestTradeDate = oldestTrade.executedAt.slice(0, 10);
          points = points.filter(point => point.date.slice(0, 10) >= oldestTradeDate);
        } else if (this.selectedPeriod() === '1W') {
          points = points.slice(-5);
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
    this.subscribedTickers.forEach(ticker => this.marketQuoteService.unsubscribe(ticker));
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
    this.router.navigate(['/client/portfolio']);
  }

  handleProfileOption(option: string) {
    console.log('Profile option:', option);
  }
}
