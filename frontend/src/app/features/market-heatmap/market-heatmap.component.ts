import { AfterViewInit, Component, DestroyRef, ElementRef, HostListener, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MarketDataService } from '../../core/services/market-data.service';
import { MarketHeatmapResponse, MarketHeatmapTicker, PriceBar } from '../../core/models/market.model';
import { HeatmapHistoryCache, PerformancePeriod, percentChange, sparkline } from './market-performance';
import { Placed, Rect, squarify } from './treemap';

type Sizing = 'rank' | 'equal';
type TileSize = 'lg' | 'md' | 'sm' | 'xs';

interface Stock extends MarketHeatmapTicker {
  sectorId: string;
  sectorName: string;
  sectorSize: number;
  change: number | null;
  spark: number[] | null;
  historyState: 'pending' | 'ready' | 'failed';
}

interface Tile extends Rect {
  stock: Stock;
  size: TileSize;
  font: number;
  background: string;
  strong: boolean;
}

interface Active {
  ticker: string;
  x: number;
  y: number;
}

const SECTOR_HEADER = 20;
const SECTOR_GAP = 4;
const TILE_GAP = 1;
const PANEL_WIDTH = 340;
const PANEL_HEIGHT = 500;
// Percent move that reaches full color intensity for each period.
const COLOR_CAP: Record<PerformancePeriod, number> = { '1D': 3, '1W': 6, '1M': 10 };

const price = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

@Component({
  selector: 'app-market-heatmap',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './market-heatmap.component.html',
  styleUrl: './market-heatmap.component.css'
})
export class MarketHeatmapComponent implements AfterViewInit {
  readonly periods: PerformancePeriod[] = ['1D', '1W', '1M'];

  readonly heatmap = signal<MarketHeatmapResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly histories = signal(new Map<string, PriceBar[] | null>());
  readonly period = signal<PerformancePeriod>('1D');
  readonly sizing = signal<Sizing>('rank');
  readonly sectorId = signal('ALL');
  readonly query = signal('');
  readonly width = signal(0);
  readonly active = signal<Active | null>(null);
  readonly pinned = signal(false);

  private historySub?: Subscription;

  readonly sectors = computed(() => this.heatmap()?.sectors ?? []);

  readonly stocks = computed<Stock[]>(() => {
    const histories = this.histories();
    const period = this.period();
    return this.sectors().flatMap(sector => sector.tickers.map(ticker => {
      const bars = histories.get(ticker.ticker);
      return {
        ...ticker,
        sectorId: sector.id,
        sectorName: sector.name,
        sectorSize: sector.tickers.length,
        change: percentChange(ticker, bars, period),
        spark: sparkline(ticker, bars),
        historyState: !histories.has(ticker.ticker) ? 'pending' : bars ? 'ready' : 'failed'
      } satisfies Stock;
    }));
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

  readonly layout = computed(() => {
    const width = this.width();
    const height = this.height();
    const period = this.period();
    const weight = (rank: number) => (this.sizing() === 'rank' ? rank ** -0.75 : 1);
    const groups = new Map<string, Stock[]>();
    for (const stock of this.stocks()) {
      if (this.sectorId() !== 'ALL' && stock.sectorId !== this.sectorId()) continue;
      groups.set(stock.sectorId, [...(groups.get(stock.sectorId) ?? []), stock]);
    }

    const placedSectors = squarify(
      [...groups.values()].map(stocks => ({ data: stocks, value: stocks.reduce((sum, stock) => sum + weight(stock.rank), 0) })),
      { x: 0, y: 0, w: width, h: height }
    );

    return placedSectors.map(({ data: stocks, ...outer }) => {
      const rect = inset(outer, SECTOR_GAP / 2);
      const body = { x: rect.x, y: rect.y + SECTOR_HEADER, w: rect.w, h: Math.max(0, rect.h - SECTOR_HEADER) };
      const tiles = squarify(stocks.map(stock => ({ data: stock, value: weight(stock.rank) })), body)
        .map(placed => this.tile(placed, period));
      return { id: stocks[0].sectorId, name: stocks[0].sectorName, rect, tiles };
    });
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
    const vw = window.innerWidth;
    const vh = window.innerHeight;
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
    private historyCache: HeatmapHistoryCache,
    private destroyRef: DestroyRef,
    private host: ElementRef<HTMLElement>
  ) {
    this.load();
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
        this.loadHistories(heatmap.sectors.flatMap(sector => sector.tickers.map(ticker => ticker.ticker)));
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Market data could not be loaded. The market service may be unavailable.');
      }
    });
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

  private loadHistories(tickers: string[]): void {
    this.historySub?.unsubscribe();
    this.histories.set(new Map());
    this.historySub = this.historyCache.load(tickers).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(([ticker, bars]) =>
      this.histories.update(current => new Map(current).set(ticker, bars))
    );
  }

  private tile(placed: Placed<Stock>, period: PerformancePeriod): Tile {
    const rect = inset(placed, TILE_GAP / 2);
    const size: TileSize = rect.w >= 120 && rect.h >= 78 ? 'lg' : rect.w >= 62 && rect.h >= 40 ? 'md' : rect.w >= 34 && rect.h >= 18 ? 'sm' : 'xs';
    const font = size === 'lg' ? Math.round(Math.min(34, Math.max(15, Math.min(rect.w / 4.6, rect.h / 3.4)))) : size === 'md' ? 13 : 11;
    return { ...rect, stock: placed.data, size, font, ...tone(placed.data.change, period) };
  }
}

function inset(rect: Rect, by: number): Rect {
  return { x: rect.x + by, y: rect.y + by, w: Math.max(0, rect.w - by * 2), h: Math.max(0, rect.h - by * 2) };
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
