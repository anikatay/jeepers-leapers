import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, map, shareReplay, throwError } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import { HoldingService } from '../../../../core/services/holding.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { PriceBar, QuoteStatus } from '../../../../core/models/market.model';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';
import { getStockIcon } from '../../../../shared/utils/stock-icon';
import {
  LoadStatus,
  TradePanelComponent,
  TradePanelInstrument,
  TradeSide
} from '../../components/trade-panel/trade-panel.component';

type ChartRange = '1D' | '5D' | '1M' | '1Y' | 'All';

interface RangeConfig {
  label: string;
  /** Backend history period; null when the API has no data for this range. */
  period: string | null;
  /** Keep only the most recent N daily bars. */
  take?: number;
}

// /api/market/{ticker}/history only serves daily bars for 1mo, 3mo, 6mo, 1y, 2y and max.
const RANGES: Record<ChartRange, RangeConfig> = {
  '1D': { label: 'today', period: null },
  '5D': { label: 'past 5 days', period: '1mo', take: 5 },
  '1M': { label: 'past month', period: '1mo' },
  '1Y': { label: 'past year', period: '1y' },
  All: { label: 'all time', period: 'max' }
};

interface ChartPoint {
  x: number;
  y: number;
  bar: PriceBar;
}

const CHART_PAD = { top: 16, right: 64, bottom: 28, left: 8 };

@Component({
  selector: 'app-stock-details',
  standalone: true,
  imports: [CommonModule, RouterLink, ConnectionStatusComponent, TradePanelComponent],
  templateUrl: './stock-details.component.html',
  styleUrl: './stock-details.component.css'
})
export class StockDetailsComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly holdingService = inject(HoldingService);
  private readonly marketDataService = inject(MarketDataService);
  private readonly marketQuoteService = inject(MarketQuoteService);

  @ViewChild('chartBox') private chartBox?: ElementRef<HTMLElement>;

  readonly ranges = Object.keys(RANGES) as ChartRange[];
  readonly Math = Math;

  readonly ticker = signal('');
  readonly instrument = signal<InstrumentResponse | null>(null);
  readonly instrumentStatus = signal<LoadStatus>('loading');

  readonly livePrice = signal<number | null>(null);
  readonly quoteStatus = signal<QuoteStatus | null>(null);
  readonly previousClose = signal<number | null>(null);

  readonly range = signal<ChartRange>('1M');
  readonly bars = signal<PriceBar[]>([]);
  readonly chartStatus = signal<'loading' | 'ready' | 'error' | 'unsupported'>('loading');
  readonly chartSize = signal({ width: 0, height: 0 });
  readonly hoverIndex = signal<number | null>(null);

  readonly holdingsStatus = signal<LoadStatus>('loading');
  readonly ownedShares = signal<number | null>(null);

  readonly panelOpen = signal(false);
  readonly panelSide = signal<TradeSide>('BUY');

  private readonly accountId = this.auth.session()?.accountId ?? '';
  private readonly historyCache = new Map<string, Observable<PriceBar[]>>();
  private routeSub?: Subscription;
  private quoteSub?: Subscription;
  private tickerSubs = new Subscription();
  private historySub?: Subscription;
  private resizeObserver?: ResizeObserver;
  private subscribedTicker: string | null = null;

  readonly icon = computed(() => getStockIcon(this.ticker()));
  readonly currency = computed(() => (this.instrument()?.currency || 'USD').toUpperCase());
  readonly price = computed(() => this.livePrice() ?? this.instrument()?.currentPrice ?? 0);

  readonly todayChange = computed(() => {
    const prev = this.previousClose();
    const price = this.price();
    if (!prev || prev <= 0 || price <= 0) return null;
    const amount = price - prev;
    return { amount, pct: (amount / prev) * 100 };
  });

  readonly canSell = computed(() => this.holdingsStatus() === 'ready' && (this.ownedShares() ?? 0) > 0);
  readonly sellDisabledReason = computed(() => {
    if (this.holdingsStatus() === 'loading') return 'Checking your holdings…';
    if (this.holdingsStatus() === 'error') return "Selling is unavailable because your holdings couldn't be loaded.";
    if (!this.canSell()) return `You don't own any shares of ${this.ticker()}.`;
    return null;
  });

  readonly positionValue = computed(() => (this.ownedShares() ?? 0) * this.price());

  readonly panelInstrument = computed<TradePanelInstrument | null>(() => {
    const inst = this.instrument();
    if (!inst) return null;
    return { instrumentId: inst.instrumentId, ticker: inst.ticker, name: inst.name, currency: inst.currency || 'USD' };
  });

  readonly rangeLabel = computed(() => RANGES[this.range()].label);

  readonly periodChange = computed(() => {
    const bars = this.bars();
    if (bars.length < 2) return null;
    const first = bars[0].close;
    const last = bars[bars.length - 1].close;
    if (first <= 0) return null;
    return { amount: last - first, pct: ((last - first) / first) * 100 };
  });

  readonly trendUp = computed(() => (this.periodChange()?.amount ?? 0) >= 0);

  readonly chart = computed(() => {
    const bars = this.bars();
    const { width, height } = this.chartSize();
    const plotW = Math.max(0, width - CHART_PAD.left - CHART_PAD.right);
    const plotH = Math.max(0, height - CHART_PAD.top - CHART_PAD.bottom);
    if (bars.length === 0 || plotW <= 0 || plotH <= 0) return null;

    const closes = bars.map(b => b.close);
    const min = Math.min(...closes);
    const max = Math.max(...closes);
    const spread = max - min || Math.max(1, max * 0.01);
    const lo = min - spread * 0.08;
    const hi = max + spread * 0.08;
    const yFor = (v: number) => CHART_PAD.top + (1 - (v - lo) / (hi - lo)) * plotH;
    const step = bars.length > 1 ? plotW / (bars.length - 1) : 0;

    const points: ChartPoint[] = bars.map((bar, i) => ({
      x: CHART_PAD.left + (bars.length > 1 ? i * step : plotW / 2),
      y: yFor(bar.close),
      bar
    }));

    const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
    const baseY = CHART_PAD.top + plotH;
    const first = points[0];
    const last = points[points.length - 1];
    const area = `${line} L${last.x.toFixed(1)},${baseY} L${first.x.toFixed(1)},${baseY} Z`;

    const yTicks = [max, (max + min) / 2, min].map(v => ({ value: v, y: yFor(v) }));
    const xIdx = bars.length > 2 ? [0, Math.floor((bars.length - 1) / 2), bars.length - 1] : bars.map((_, i) => i);
    const xTicks = xIdx.map(i => ({ label: this.formatAxisDate(bars[i].date), x: points[i].x }));

    return { points, line, area, yTicks, xTicks, baseY, plotRight: CHART_PAD.left + plotW, width, height };
  });

  readonly hoverPoint = computed(() => {
    const idx = this.hoverIndex();
    const chart = this.chart();
    if (idx === null || !chart) return null;
    return chart.points[idx] ?? null;
  });

  readonly chartSummary = computed(() => {
    const bars = this.bars();
    if (bars.length === 0) return `No price history for ${this.ticker()}`;
    const first = bars[0];
    const last = bars[bars.length - 1];
    return `${this.ticker()} price history, ${this.rangeLabel()}: ${this.formatCurrency(first.close)} on ` +
      `${this.formatFullDate(first.date)} to ${this.formatCurrency(last.close)} on ${this.formatFullDate(last.date)}. ` +
      'Use the left and right arrow keys to inspect daily closes.';
  });

  ngOnInit(): void {
    this.quoteSub = this.marketQuoteService.quotes$.subscribe(quote => {
      if (quote.ticker !== this.ticker()) return;
      this.quoteStatus.set(quote.status);
      if (quote.price > 0) this.livePrice.set(quote.price);
    });

    this.routeSub = this.route.paramMap.subscribe(params => {
      this.loadTicker((params.get('ticker') ?? '').trim().toUpperCase());
    });
  }

  ngAfterViewInit(): void {
    this.observeChartBox();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    this.quoteSub?.unsubscribe();
    this.tickerSubs.unsubscribe();
    this.historySub?.unsubscribe();
    this.resizeObserver?.disconnect();
    this.releaseQuoteSubscription();
  }

  // ── Data loading ───────────────────────────────────────────────

  private loadTicker(ticker: string): void {
    this.tickerSubs.unsubscribe();
    this.tickerSubs = new Subscription();
    this.historySub?.unsubscribe();
    this.historyCache.clear();
    this.releaseQuoteSubscription();

    this.ticker.set(ticker);
    this.instrument.set(null);
    this.instrumentStatus.set('loading');
    this.livePrice.set(null);
    this.quoteStatus.set(null);
    this.previousClose.set(null);
    this.bars.set([]);
    this.hoverIndex.set(null);
    this.holdingsStatus.set('loading');
    this.ownedShares.set(null);
    this.panelOpen.set(false);

    if (!ticker) {
      this.instrumentStatus.set('error');
      return;
    }

    this.tickerSubs.add(
      this.instrumentService.getInstrumentByTicker(ticker).subscribe({
        next: instrument => {
          this.instrument.set(instrument);
          this.instrumentStatus.set('ready');
          this.marketQuoteService.subscribe(instrument.ticker);
          this.subscribedTicker = instrument.ticker.trim().toUpperCase();
          this.loadPreviousClose();
          this.loadRange(this.range());
          this.loadHoldings();
          setTimeout(() => this.observeChartBox());
        },
        error: err => {
          console.error(`Failed to load instrument ${ticker}:`, err);
          this.instrumentStatus.set('error');
        }
      })
    );
  }

  loadHoldings(): void {
    const instrumentId = this.instrument()?.instrumentId;
    if (!this.accountId || !instrumentId) {
      this.holdingsStatus.set('error');
      this.ownedShares.set(null);
      return;
    }

    this.holdingsStatus.set('loading');
    this.tickerSubs.add(
      this.holdingService.getHoldings(this.accountId).subscribe({
        next: holdings => {
          const holding = holdings.find(h => h.instrumentId === instrumentId);
          this.ownedShares.set(holding ? Number(holding.quantity) : 0);
          this.holdingsStatus.set('ready');
        },
        error: err => {
          console.error('Failed to load holdings:', err);
          this.ownedShares.set(null);
          this.holdingsStatus.set('error');
        }
      })
    );
  }

  private loadPreviousClose(): void {
    // Same "previous daily close" approximation the portfolio and home pages use.
    this.tickerSubs.add(
      this.getBars('1mo').subscribe({
        next: bars => this.previousClose.set(bars.length >= 2 ? bars[bars.length - 2].close : null),
        error: () => this.previousClose.set(null)
      })
    );
  }

  selectRange(range: ChartRange): void {
    if (range === this.range() && this.chartStatus() !== 'error') return;
    this.range.set(range);
    this.loadRange(range);
  }

  retryChart(): void {
    const period = RANGES[this.range()].period;
    if (period) this.historyCache.delete(period);
    this.loadRange(this.range());
  }

  private loadRange(range: ChartRange): void {
    const config = RANGES[range];
    this.historySub?.unsubscribe();
    this.hoverIndex.set(null);

    if (!config.period) {
      this.bars.set([]);
      this.chartStatus.set('unsupported');
      return;
    }

    this.chartStatus.set('loading');
    this.historySub = this.getBars(config.period).subscribe({
      next: bars => {
        if (this.range() !== range) return;
        this.bars.set(config.take ? bars.slice(-config.take) : bars);
        this.chartStatus.set('ready');
      },
      error: err => {
        console.error(`Failed to load ${config.period} history for ${this.ticker()}:`, err);
        if (this.range() !== range) return;
        this.bars.set([]);
        this.chartStatus.set('error');
      }
    });
  }

  private getBars(period: string): Observable<PriceBar[]> {
    let request = this.historyCache.get(period);
    if (!request) {
      request = this.marketDataService.getHistory(this.ticker(), period).pipe(
        map(response => response.bars ?? []),
        catchError(err => {
          this.historyCache.delete(period);
          return throwError(() => err);
        }),
        shareReplay(1)
      );
      this.historyCache.set(period, request);
    }
    return request;
  }

  private releaseQuoteSubscription(): void {
    if (this.subscribedTicker) {
      this.marketQuoteService.unsubscribe(this.subscribedTicker);
      this.subscribedTicker = null;
    }
  }

  // ── Trading ────────────────────────────────────────────────────

  openTrade(side: TradeSide): void {
    if (!this.instrument()) return;
    if (side === 'SELL' && !this.canSell()) return;
    this.panelSide.set(side);
    this.panelOpen.set(true);
  }

  closeTrade(): void {
    this.panelOpen.set(false);
  }

  onTradeCompleted(): void {
    this.loadHoldings();
  }

  // ── Chart interaction ──────────────────────────────────────────

  onChartPointer(event: PointerEvent): void {
    const chart = this.chart();
    const box = this.chartBox?.nativeElement;
    if (!chart || !box || chart.points.length === 0) return;
    const x = event.clientX - box.getBoundingClientRect().left;
    this.hoverIndex.set(this.nearestIndex(x, chart.points));
  }

  clearHover(): void {
    this.hoverIndex.set(null);
  }

  onChartKey(event: KeyboardEvent): void {
    const count = this.chart()?.points.length ?? 0;
    if (count === 0) return;
    const current = this.hoverIndex();
    if (event.key === 'ArrowLeft') {
      this.hoverIndex.set(current === null ? count - 1 : Math.max(0, current - 1));
    } else if (event.key === 'ArrowRight') {
      this.hoverIndex.set(current === null ? count - 1 : Math.min(count - 1, current + 1));
    } else if (event.key === 'Escape') {
      this.hoverIndex.set(null);
    } else {
      return;
    }
    event.preventDefault();
  }

  private nearestIndex(x: number, points: ChartPoint[]): number {
    if (points.length === 1) return 0;
    const step = points[1].x - points[0].x;
    const idx = Math.round((x - points[0].x) / step);
    return Math.min(points.length - 1, Math.max(0, idx));
  }

  private observeChartBox(): void {
    const el = this.chartBox?.nativeElement;
    if (!el || typeof ResizeObserver === 'undefined') return;
    this.resizeObserver?.disconnect();
    this.resizeObserver = new ResizeObserver(entries => {
      const rect = entries[0]?.contentRect;
      if (rect) this.chartSize.set({ width: rect.width, height: rect.height });
    });
    this.resizeObserver.observe(el);
  }

  // ── Formatting ─────────────────────────────────────────────────

  formatCurrency(value: number | null | undefined, maxDigits = 2): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: this.currency(),
      minimumFractionDigits: 2,
      maximumFractionDigits: Math.max(2, maxDigits)
    }).format(value ?? 0);
  }

  formatShares(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(value ?? 0);
  }

  formatFullDate(date: string): string {
    return this.parseDate(date).toLocaleDateString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric', year: 'numeric'
    });
  }

  private formatAxisDate(date: string): string {
    const longRange = this.range() === '1Y' || this.range() === 'All';
    return this.parseDate(date).toLocaleDateString('en-US',
      longRange ? { month: 'short', year: 'numeric' } : { month: 'short', day: 'numeric' });
  }

  private parseDate(date: string): Date {
    // Bars are calendar dates (YYYY-MM-DD); parse as local to avoid an off-by-one day.
    const [y, m, d] = date.slice(0, 10).split('-').map(Number);
    return new Date(y, (m || 1) - 1, d || 1);
  }
}
