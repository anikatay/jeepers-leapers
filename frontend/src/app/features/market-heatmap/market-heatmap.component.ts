import { AfterViewInit, Component, DestroyRef, ElementRef, HostListener, computed, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription, bufferTime, filter, interval } from 'rxjs';
import { ConnectionStatusComponent } from '../../shared/components/connection.status.component';
import { MarketDataService } from '../../core/services/market-data.service';
import { MarketQuoteService } from '../../core/services/market-quote.service';
import { MarketHeatmapResponse, MarketHeatmapSector, MarketHeatmapTicker, MarketQuote, PriceBar } from '../../core/models/market.model';
import { HISTORY_TTL_MS, HeatmapHistoryCache, PerformancePeriod, percentChange, sparkline } from './market-performance';
import { Rect, squarify } from './treemap';

type Sizing = 'rank' | 'equal';
type LiveQuote = Pick<MarketHeatmapTicker, 'price' | 'quoteTime' | 'receivedTime' | 'source' | 'status'>;

interface Stock extends MarketHeatmapTicker {
  sectorId: string;
  sectorName: string;
  sectorSize: number;
  /** Snapshot price used to size tile text, so live ticks do not reflow labels. */
  layoutPrice: number | null;
  change: number | null;
  spark: number[] | null;
  historyState: 'pending' | 'ready' | 'failed';
}

interface TileText {
  ticker: number;
  detail: number;
  showPrice: boolean;
  showChange: boolean;
}

interface Cell extends Rect {
  ticker: string;
}

interface Tile extends Rect {
  stock: Stock;
  text: TileText | null;
  background: string;
  strong: boolean;
}

interface Active {
  ticker: string;
  x: number;
  y: number;
}

const SECTOR_HEADER = 20;
const SECTOR_GAP = 6;
// Divider between neighbouring tiles; tile edges are snapped to whole pixels so every divider is crisp.
const TILE_GAP = 1;
const TILE_PADDING = 6;
const PANEL_WIDTH = 340;
const PANEL_HEIGHT = 500;
// Percent move that reaches full color intensity for each period.
const COLOR_CAP: Record<PerformancePeriod, number> = { '1D': 3, '1W': 6, '1M': 10 };
// Widest expected change label; fitting against it keeps tiles from switching layout as values change.
const CHANGE_TEMPLATE = '+00.00%';
const QUOTE_FLUSH_MS = 500;

const price = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

@Component({
  selector: 'app-market-heatmap',
  standalone: true,
  imports: [FormsModule, ConnectionStatusComponent],
  templateUrl: './market-heatmap.component.html',
  styleUrl: './market-heatmap.component.css'
})
export class MarketHeatmapComponent implements AfterViewInit {
  readonly periods: PerformancePeriod[] = ['1D', '1W', '1M'];

  readonly heatmap = signal<MarketHeatmapResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly histories = signal(new Map<string, PriceBar[] | null>());
  readonly live = signal(new Map<string, LiveQuote>());
  readonly lastLive = signal<string | null>(null);
  readonly period = signal<PerformancePeriod>('1D');
  readonly sizing = signal<Sizing>('rank');
  readonly sectorId = signal('ALL');
  readonly query = signal('');
  readonly width = signal(0);
  readonly viewport = signal({ width: window.innerWidth, height: window.innerHeight });
  readonly active = signal<Active | null>(null);
  readonly pinned = signal(false);

  private historySub?: Subscription;
  private subscribed = new Set<string>();

  readonly sectors = computed(() => this.heatmap()?.sectors ?? []);

  readonly stocks = computed<Stock[]>(() => {
    const histories = this.histories();
    const live = this.live();
    const period = this.period();
    return this.sectors().flatMap(sector => sector.tickers.map(base => {
      const ticker = newest(base, live.get(base.ticker));
      const bars = histories.get(ticker.ticker);
      return {
        ...ticker,
        sectorId: sector.id,
        sectorName: sector.name,
        sectorSize: sector.tickers.length,
        layoutPrice: base.price ?? ticker.price,
        change: percentChange(ticker, bars, period),
        spark: sparkline(ticker, bars),
        historyState: !histories.has(ticker.ticker) ? 'pending' : bars ? 'ready' : 'failed'
      } satisfies Stock;
    }));
  });

  readonly updatedAt = computed(() => {
    const asOf = this.heatmap()?.asOf ?? null;
    const live = this.lastLive();
    if (!asOf || !live) return asOf ?? live;
    return Date.parse(live) > Date.parse(asOf) ? live : asOf;
  });

  readonly coverage = computed(() => {
    const stocks = this.stocks();
    return {
      total: stocks.length,
      loaded: stocks.filter(stock => stock.historyState !== 'pending').length,
      calculated: stocks.filter(stock => stock.change !== null).length
    };
  });

  readonly matches = computed(() => {
    const query = this.query().trim().toUpperCase();
    return query ? new Set(this.stocks().filter(stock => stock.ticker.includes(query)).map(stock => stock.ticker)) : null;
  });

  readonly height = computed(() => {
    const width = this.width();
    const single = this.sectorId() !== 'ALL';
    if (width < 700) return Math.round(Math.min(1100, Math.max(480, width * (single ? 1.1 : 1.9))));
    return Math.round(Math.min(820, Math.max(440, width * (single ? 0.45 : 0.58))));
  });

  /** Tile positions depend only on sectors, ranks, sizing and size, never on prices. */
  readonly geometry = computed(() => {
    const width = this.width();
    const height = this.height();
    const sectorId = this.sectorId();
    const weight = (rank: number) => (this.sizing() === 'rank' ? rank ** -0.75 : 1);
    const sectors = this.sectors().filter(sector => sector.tickers.length && (sectorId === 'ALL' || sector.id === sectorId));

    const placedSectors = squarify<MarketHeatmapSector>(
      sectors.map(sector => ({ data: sector, value: sector.tickers.reduce((sum, ticker) => sum + weight(ticker.rank), 0) })),
      { x: 0, y: 0, w: width, h: height }
    );

    return placedSectors.map(({ data: sector, ...outer }) => {
      const rect = snap(inset(outer, SECTOR_GAP / 2));
      // Body starts one divider in from the sector outline; each tile gives up one divider on its right and bottom.
      const body = {
        x: rect.x + TILE_GAP,
        y: rect.y + SECTOR_HEADER,
        w: Math.max(0, rect.w - TILE_GAP),
        h: Math.max(0, rect.h - SECTOR_HEADER)
      };
      const cells: Cell[] = squarify(sector.tickers.map(ticker => ({ data: ticker.ticker, value: weight(ticker.rank) })), body)
        .map(({ data, ...placed }) => {
          const cell = snap(placed);
          return { ticker: data, ...cell, w: Math.max(0, cell.w - TILE_GAP), h: Math.max(0, cell.h - TILE_GAP) };
        });
      return { id: sector.id, name: sector.name, rect, cells };
    });
  });

  readonly layout = computed(() => {
    const period = this.period();
    const stocks = new Map(this.stocks().map(stock => [stock.ticker, stock]));
    return this.geometry().map(({ cells, ...sector }) => ({
      ...sector,
      tiles: cells.flatMap(cell => {
        const stock = stocks.get(cell.ticker);
        return stock ? [this.tile(cell, stock, period)] : [];
      })
    }));
  });

  readonly legend = computed(() => {
    const cap = COLOR_CAP[this.period()];
    return [-cap, (-cap * 2) / 3, -cap / 3, 0, cap / 3, (cap * 2) / 3, cap].map(value => ({
      label: `${value > 0 ? '+' : ''}${Number(value.toFixed(1))}%`,
      ...tone(value, this.period())
    }));
  });

  readonly activeStock = computed(() => this.stocks().find(stock => stock.ticker === this.active()?.ticker) ?? null);

  readonly peers = computed(() => {
    const active = this.activeStock();
    return active ? this.stocks().filter(stock => stock.sectorId === active.sectorId).sort((a, b) => a.rank - b.rank).slice(0, 10) : [];
  });

  readonly panelPosition = computed(() => {
    const active = this.active();
    if (!active) return null;
    const { width: vw, height: vh } = this.viewport();
    const width = Math.min(PANEL_WIDTH, vw - 16);
    const right = active.x + 18;
    const left = right + width <= vw - 8 ? right : active.x - 18 - width;
    return {
      left: Math.max(8, Math.min(left, vw - width - 8)),
      top: Math.max(8, Math.min(active.y - 40, vh - Math.min(PANEL_HEIGHT, vh - 16) - 8)),
      width
    };
  });

  constructor(
    private market: MarketDataService,
    private quotes: MarketQuoteService,
    private historyCache: HeatmapHistoryCache,
    private destroyRef: DestroyRef,
    private host: ElementRef<HTMLElement>
  ) {
    this.watchQuotes();
    this.load();

    interval(HISTORY_TTL_MS + 60_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.subscribed.size) this.loadHistories([...this.subscribed], true);
    });

    this.destroyRef.onDestroy(() => {
      this.subscribed.forEach(ticker => this.quotes.unsubscribe(ticker, this));
      this.subscribed.clear();
    });
  }

  ngAfterViewInit(): void {
    const observer = new ResizeObserver(([entry]) => this.width.set(Math.floor(entry.contentRect.width)));
    observer.observe(this.host.nativeElement.querySelector('.frame')!);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.market.getHeatmap().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: heatmap => {
        this.heatmap.set(heatmap);
        this.loading.set(false);
        const tickers = tickersOf(heatmap);
        this.syncSubscriptions(tickers);
        this.loadHistories(tickers);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Market data could not be loaded. The market service may be unavailable.');
      }
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    this.viewport.set({ width: window.innerWidth, height: window.innerHeight });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (this.pinned() && !target.closest('.tile, .panel')) this.close();
  }

  @HostListener('document:keydown.escape')
  close(): void {
    this.pinned.set(false);
    this.active.set(null);
  }

  hover(event: MouseEvent, ticker: string): void {
    if (!this.pinned()) this.active.set({ ticker, x: event.clientX, y: event.clientY });
  }

  leave(): void {
    if (!this.pinned()) this.active.set(null);
  }

  focus(event: FocusEvent, ticker: string): void {
    if (this.pinned()) return;
    const rect = (event.target as HTMLElement).getBoundingClientRect();
    this.active.set({ ticker, x: rect.right - 8, y: rect.top + 24 });
  }

  pin(event: MouseEvent, ticker: string): void {
    const same = this.pinned() && this.active()?.ticker === ticker;
    if (same) return this.close();
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const x = event.clientX || rect.right - 8;
    const y = event.clientY || rect.top + 24;
    this.active.set({ ticker, x, y });
    this.pinned.set(true);
  }

  dimmed(ticker: string): boolean {
    const matches = this.matches();
    return !!matches && !matches.has(ticker);
  }

  tone(change: number | null): { background: string; strong: boolean } {
    return tone(change, this.period());
  }

  price(value: number | null): string {
    return value === null ? '—' : price.format(value);
  }

  change(value: number | null): string {
    if (value === null) return 'N/A';
    return `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value).toFixed(2)}%`;
  }

  time(iso: string | null): string {
    if (!iso) return '—';
    return `${new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: 'America/New_York' })} ET`;
  }

  label(stock: Stock): string {
    return `${stock.ticker}, ${this.price(stock.price)}, ${this.period()} ${this.change(stock.change)}`;
  }

  sparkPoints(values: number[]): string {
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    return values.map((value, i) => `${((i / (values.length - 1)) * 100).toFixed(2)},${(26 - ((value - min) / span) * 24).toFixed(2)}`).join(' ');
  }

  private watchQuotes(): void {
    this.quotes.quotes$.pipe(
      filter(quote => this.subscribed.has(quote.ticker)),
      bufferTime(QUOTE_FLUSH_MS),
      filter(batch => batch.length > 0),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(batch => this.applyQuotes(batch));

    // Quotes sent while the socket was down are lost; re-read the cached snapshot once it is back.
    let dropped = false;
    toObservable(this.quotes.status).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(status => {
      if (status === 'reconnecting') dropped = true;
      if (status === 'connected' && dropped) {
        dropped = false;
        this.syncSnapshot();
      }
    });
  }

  private applyQuotes(batch: MarketQuote[]): void {
    const next = new Map(this.live());
    let latest = this.lastLive();
    for (const quote of batch) {
      const time = quote.quoteTime ? Date.parse(quote.quoteTime) : NaN;
      if (!Number.isFinite(quote.price) || quote.price <= 0 || Number.isNaN(time)) continue;
      const current = next.get(quote.ticker);
      if (current?.quoteTime && Date.parse(current.quoteTime) > time) continue;
      next.set(quote.ticker, {
        price: quote.price,
        quoteTime: quote.quoteTime,
        receivedTime: quote.receivedTime ?? null,
        source: quote.source ?? null,
        status: quote.status ?? 'LIVE'
      });
      const received = quote.receivedTime ?? quote.quoteTime;
      if (!latest || Date.parse(received) > Date.parse(latest)) latest = received;
    }
    this.live.set(next);
    this.lastLive.set(latest);
  }

  private syncSnapshot(): void {
    this.market.getHeatmap().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: heatmap => {
        this.heatmap.set(heatmap);
        const tickers = tickersOf(heatmap);
        this.syncSubscriptions(tickers);
        const missing = tickers.filter(ticker => !this.histories().has(ticker));
        if (missing.length) this.loadHistories(missing, true);
      },
      error: () => {}
    });
  }

  /** Holds exactly one claim per heatmap ticker on the shared quote socket. */
  private syncSubscriptions(tickers: string[]): void {
    const next = new Set(tickers);
    this.subscribed.forEach(ticker => {
      if (!next.has(ticker)) this.quotes.unsubscribe(ticker, this);
    });
    next.forEach(ticker => {
      if (!this.subscribed.has(ticker)) this.quotes.subscribe(ticker, this);
    });
    this.subscribed = next;
  }

  /** `keep` refreshes in place: existing bars stay visible and are not replaced by a failed refetch. */
  private loadHistories(tickers: string[], keep = false): void {
    this.historySub?.unsubscribe();
    if (!keep) this.histories.set(new Map());
    this.historySub = this.historyCache.load(tickers).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(([ticker, bars]) =>
      this.histories.update(current => {
        const existing = current.get(ticker);
        if (existing === bars || (bars === null && existing)) return current;
        return new Map(current).set(ticker, bars);
      })
    );
  }

  private tile(cell: Cell, stock: Stock, period: PerformancePeriod): Tile {
    // Size text for a slightly larger price than the snapshot so ordinary live moves never change the layout.
    const priceTemplate = stock.layoutPrice === null ? '' : this.price(stock.layoutPrice * 1.1);
    const text = fitText(stock.ticker, priceTemplate, CHANGE_TEMPLATE, stock.layoutPrice !== null, cell);
    return { x: cell.x, y: cell.y, w: cell.w, h: cell.h, stock, text, ...tone(stock.change, period) };
  }
}

function tickersOf(heatmap: MarketHeatmapResponse): string[] {
  return heatmap.sectors.flatMap(sector => sector.tickers.map(ticker => ticker.ticker));
}

/** The snapshot ticker with a live quote applied, when that quote is at least as recent. */
function newest(base: MarketHeatmapTicker, live: LiveQuote | undefined): MarketHeatmapTicker {
  if (!live?.quoteTime) return base;
  if (base.quoteTime && Date.parse(base.quoteTime) > Date.parse(live.quoteTime)) return base;
  return { ...base, ...live };
}

function inset(rect: Rect, by: number): Rect {
  return { x: rect.x + by, y: rect.y + by, w: Math.max(0, rect.w - by * 2), h: Math.max(0, rect.h - by * 2) };
}

/** Rounds each edge (not the size) so neighbouring rectangles keep sharing exact pixel edges. */
function snap(rect: Rect): Rect {
  const x = Math.round(rect.x);
  const y = Math.round(rect.y);
  return { x, y, w: Math.round(rect.x + rect.w) - x, h: Math.round(rect.y + rect.h) - y };
}

/**
 * Picks the richest content that fits the tile: ticker + price + change, then ticker + change,
 * then ticker only. Font sizes derive from the tile's own width and height.
 */
function fitText(ticker: string, priceText: string, changeText: string, hasPrice: boolean, rect: Rect): TileText | null {
  const width = rect.w - TILE_PADDING;
  const height = rect.h - TILE_PADDING;
  // Approximate advance widths in em: bold sans capitals and monospace digits.
  const tickerEm = ticker.length * 0.68 + 0.1;
  const monoEm = 0.61;

  for (const lines of hasPrice ? [3, 2, 1] : [2, 1]) {
    const showChange = lines >= 2;
    const showPrice = lines === 3;
    const ratio = showPrice ? 0.42 : 0.48;
    const tickerFont = Math.min(36, width / tickerEm, height / (1.12 + (lines - 1) * ratio * 1.3));
    const detail = Math.min(15, Math.max(10, tickerFont * ratio));
    const needed = tickerFont * 1.12 + (lines - 1) * detail * 1.3;
    const detailWidth = Math.max(showPrice ? priceText.length : 0, showChange ? changeText.length : 0) * monoEm * detail;

    if (tickerFont < (lines === 1 ? 8 : 11) || needed > height || detailWidth > width) continue;
    return { ticker: Math.floor(tickerFont), detail: Math.floor(detail), showPrice, showChange };
  }
  return null;
}

function tone(change: number | null, period: PerformancePeriod): { background: string; strong: boolean } {
  if (change === null) return { background: 'var(--hm-na)', strong: false };
  const intensity = Math.min(Math.abs(change) / COLOR_CAP[period], 1);
  if (intensity < 0.01) return { background: 'var(--hm-flat)', strong: false };
  const mix = Math.round(28 + intensity * 72);
  return {
    background: `color-mix(in srgb, var(${change > 0 ? '--hm-up' : '--hm-down'}) ${mix}%, var(--hm-flat))`,
    strong: intensity >= 0.45
  };
}
