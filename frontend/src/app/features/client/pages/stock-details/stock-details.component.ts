import {
  Component,
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
import { ExchangeService } from '../../../../core/services/exchange.service';
import { HoldingService } from '../../../../core/services/holding.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { MarketSession, MarketSessionService } from '../../../../core/services/market-session.service';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { PriceBar, QuoteStatus } from '../../../../core/models/market.model';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { ConnectionStatusComponent } from '../../../../shared/components/connection.status.component';
import { PortfolioChartComponent } from '../../../../shared/components/portfolio-chart.component';
import {
  normalizeIntradayBaseline,
  PortfolioHistoryPoint
} from '../../../../shared/utils/portfolio-history';
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
  /** Backend history period. */
  period: string;
  /** Minute bars from /intraday-history rather than daily bars from /history. */
  intraday?: boolean;
  /** Keep only the most recent N daily bars. */
  take?: number;
}

// /api/market/{ticker}/history serves daily bars for 1mo, 3mo, 6mo, 1y, 2y and max; 1D comes
// from /api/market/{ticker}/intraday-history, the same source the home chart uses.
const RANGES: Record<ChartRange, RangeConfig> = {
  '1D': { label: 'today', period: '1d', intraday: true },
  '5D': { label: 'past 5 days', period: '1mo', take: 5 },
  '1M': { label: 'past month', period: '1mo' },
  '1Y': { label: 'past year', period: '1y' },
  All: { label: 'all time', period: 'max' }
};

@Component({
  selector: 'app-stock-details',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ConnectionStatusComponent,
    TradePanelComponent,
    TranslatePipe,
    PortfolioChartComponent
  ],
  templateUrl: './stock-details.component.html',
  styleUrl: './stock-details.component.css'
})
export class StockDetailsComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly exchangeService = inject(ExchangeService);
  private readonly holdingService = inject(HoldingService);
  private readonly marketDataService = inject(MarketDataService);
  private readonly marketQuoteService = inject(MarketQuoteService);
  private readonly marketSessionService = inject(MarketSessionService);
  private readonly translation = inject(TranslationService);

  @ViewChild(PortfolioChartComponent) private priceChart?: PortfolioChartComponent;

  readonly ranges = Object.keys(RANGES) as ChartRange[];
  readonly Math = Math;

  readonly ticker = signal('');
  readonly instrument = signal<InstrumentResponse | null>(null);
  readonly instrumentStatus = signal<LoadStatus>('loading');

  readonly livePrice = signal<number | null>(null);
  readonly quoteStatus = signal<QuoteStatus | null>(null);
  readonly marketSession = signal<MarketSession | null>(null);
  readonly exchangeName = signal<string | null>(null);
  readonly previousClose = signal<number | null>(null);

  readonly range = signal<ChartRange>('1M');
  readonly bars = signal<PriceBar[]>([]);
  readonly chartStatus = signal<'loading' | 'ready' | 'error'>('loading');

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

  /** e.g. "New York Stock Exchange (NYSE)", or just the instrument's exchange code. */
  readonly exchangeLabel = computed(() => {
    const code = this.instrument()?.exchangeId?.trim();
    if (!code) return null;
    const name = this.exchangeName()?.trim();
    return name && name.toUpperCase() !== code.toUpperCase() ? `${name} (${code})` : code;
  });

  readonly marketClosedReason = computed(() => {
    const session = this.marketSession();
    return session?.state === 'closed' ? session.detail : null;
  });

  readonly canSell = computed(() => this.holdingsStatus() === 'ready' && (this.ownedShares() ?? 0) > 0);
  readonly sellDisabledReason = computed(() => {
    this.translation.currentLanguage();
    if (this.holdingsStatus() === 'loading') return this.translation.t('stock.checking_holdings');
    if (this.holdingsStatus() === 'error') return this.translation.t('stock.sell_unavailable');
    if (!this.canSell()) return this.translation.t('stock.dont_own', { ticker: this.ticker() });
    return null;
  });

  readonly positionValue = computed(() => (this.ownedShares() ?? 0) * this.price());

  readonly panelInstrument = computed<TradePanelInstrument | null>(() => {
    const inst = this.instrument();
    if (!inst) return null;
    return {
      instrumentId: inst.instrumentId,
      ticker: inst.ticker,
      name: inst.name,
      currency: inst.currency || 'USD',
      exchangeId: inst.exchangeId
    };
  });

  readonly rangeLabel = computed(() => {
    this.translation.currentLanguage();
    const keys: Record<ChartRange, string> = {
      '1D': 'stock.range_today',
      '5D': 'stock.range_5d',
      '1M': 'stock.range_1m',
      '1Y': 'stock.range_1y',
      All: 'stock.range_all'
    };
    return this.translation.t(keys[this.range()]);
  });

  readonly periodChange = computed(() => {
    const bars = this.bars();
    if (bars.length < 2) return null;
    const first = bars[0].close;
    const last = bars[bars.length - 1].close;
    if (first <= 0) return null;
    return { amount: last - first, pct: ((last - first) / first) * 100 };
  });

  readonly trendUp = computed(() => (this.periodChange()?.amount ?? 0) >= 0);

  /** Closing price per bar, in the shape the shared chart renders. */
  readonly chartPoints = computed<PortfolioHistoryPoint[]>(() =>
    this.bars().map(bar => ({ date: bar.date, value: bar.close }))
  );

  ngOnInit(): void {
    this.quoteSub = this.marketQuoteService.quotes$.subscribe(quote => {
      if (quote.ticker !== this.ticker()) return;
      this.quoteStatus.set(quote.status);
      if (quote.price > 0) {
        this.livePrice.set(quote.price);
        this.pushLivePriceToChart(quote.price);
      }
    });

    this.routeSub = this.route.paramMap.subscribe(params => {
      this.loadTicker((params.get('ticker') ?? '').trim().toUpperCase());
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    this.quoteSub?.unsubscribe();
    this.tickerSubs.unsubscribe();
    this.historySub?.unsubscribe();
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
    this.marketSession.set(null);
    this.exchangeName.set(null);
    this.previousClose.set(null);
    this.bars.set([]);
    this.holdingsStatus.set('loading');
    this.ownedShares.set(null);
    this.panelOpen.set(false);

    if (!ticker) {
      this.instrumentStatus.set('error');
      return;
    }

    // Resolve rather than fetch: most of the market universe the user can reach from search
    // or the sector movers strip has never been inserted into instruments, and a plain GET
    // 404s for those. Resolve looks the ticker up and persists it, which is what the search
    // bar already does for the same symbols.
    this.tickerSubs.add(
      this.instrumentService.resolveInstrument(ticker).subscribe({
        next: instrument => {
          this.instrument.set(instrument);
          this.instrumentStatus.set('ready');
          this.marketQuoteService.subscribe(instrument.ticker);
          this.subscribedTicker = instrument.ticker.trim().toUpperCase();
          this.tickerSubs.add(
            this.marketSessionService
              .watch(instrument.ticker, instrument.exchangeId)
              .subscribe(session => this.marketSession.set(session))
          );
          if (instrument.exchangeId) {
            this.tickerSubs.add(
              this.exchangeService.getExchangeById(instrument.exchangeId).subscribe({
                next: exchange => this.exchangeName.set(exchange.name),
                error: () => this.exchangeName.set(null)
              })
            );
          }
          this.loadLiveQuote(instrument.ticker);
          this.loadPreviousClose();
          this.loadRange(this.range());
          this.loadHoldings();
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
      this.getBars(RANGES['1M']).subscribe({
        next: bars => this.previousClose.set(bars.length >= 2 ? bars[bars.length - 2].close : null),
        error: () => this.previousClose.set(null)
      })
    );
  }

  private loadLiveQuote(ticker: string): void {
    this.tickerSubs.add(
      this.marketDataService.getQuote(ticker).subscribe({
        next: quote => {
          this.quoteStatus.set(quote.status);
          if (quote.price > 0 && this.livePrice() === null) this.livePrice.set(quote.price);
        },
        error: () => {}
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

    this.chartStatus.set('loading');
    this.historySub = this.getBars(config).subscribe({
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

  private getBars(config: RangeConfig): Observable<PriceBar[]> {
    // Intraday is keyed separately so it never shares a cache entry with the daily 1mo series.
    const cacheKey = config.intraday ? 'intraday' : config.period;
    let request = this.historyCache.get(cacheKey);
    if (!request) {
      const source = config.intraday
        ? this.marketDataService.getIntradayHistory(this.ticker()).pipe(map(normalizeIntradayBaseline))
        : this.marketDataService.getHistory(this.ticker(), config.period);

      request = source.pipe(
        map(response => response.bars ?? []),
        catchError(err => {
          this.historyCache.delete(cacheKey);
          return throwError(() => err);
        }),
        shareReplay(1)
      );
      this.historyCache.set(cacheKey, request);
    }
    return request;
  }

  /**
   * Only the intraday range has a minute grid for a live tick to extend; on a daily range the
   * last bar is a completed close, so appending today's price would misreport it as one.
   */
  private pushLivePriceToChart(price: number): void {
    if (this.range() !== '1D' || this.chartStatus() !== 'ready') return;

    const minute = new Date();
    minute.setSeconds(0, 0);
    this.priceChart?.updateLivePoint(minute.toISOString(), price);
  }

  private releaseQuoteSubscription(): void {
    if (this.subscribedTicker) {
      this.marketQuoteService.unsubscribe(this.subscribedTicker);
      this.subscribedTicker = null;
    }
  }

  // ── Trading ────────────────────────────────────────────────────

  openTrade(side: TradeSide): void {
    if (!this.instrument() || this.marketClosedReason()) return;
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


  private parseDate(date: string): Date {
    // Bars are calendar dates (YYYY-MM-DD); parse as local to avoid an off-by-one day.
    const [y, m, d] = date.slice(0, 10).split('-').map(Number);
    return new Date(y, (m || 1) - 1, d || 1);
  }
}
