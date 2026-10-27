import { Injectable, inject, signal } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { MarketDataService } from './market-data.service';
import { MarketHeatmapSector, PriceBar } from '../models/market.model';
import {
  HeatmapHistoryCache,
  PerformancePeriod
} from '../../shared/utils/market-performance';
import { SectorMover, selectSectorMovers } from '../../shared/utils/sector-movers';

/** How often the sector snapshot is refetched while a consumer is mounted. */
const REFRESH_MS = 60_000;

const PERIOD: PerformancePeriod = '1D';

export type SectorMoversStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Best and worst performers per sector, for ambient display alongside the rest of the chrome.
 *
 * Deliberately snapshot-driven: the percent change is computed from the heatmap REST snapshot's
 * quote plus cached daily history, with no websocket subscription. The heatmap page subscribes
 * to every ticker it draws, which is reasonable for a page the user opened on purpose, but the
 * navbar is mounted on every route — holding ~100 live subscriptions there would compete with
 * the page's own quotes for no real benefit at a one-minute refresh.
 *
 * Reference counted, so the polling stops when nothing is showing the data.
 */
@Injectable({ providedIn: 'root' })
export class SectorMoversService {
  private readonly market = inject(MarketDataService);
  private readonly historyCache = inject(HeatmapHistoryCache);

  readonly movers = signal<SectorMover[]>([]);
  readonly status = signal<SectorMoversStatus>('idle');

  private sectors: MarketHeatmapSector[] = [];
  private readonly histories = signal(new Map<string, PriceBar[] | null>());
  private consumers = 0;
  private pollSub?: Subscription;
  private loadSub?: Subscription;
  private historySub?: Subscription;

  /** Called by a consumer on mount; the first one starts polling. */
  retain(): void {
    this.consumers += 1;
    if (this.consumers > 1) return;

    this.load();
    this.pollSub = timer(REFRESH_MS, REFRESH_MS).subscribe(() => this.load());
  }

  /** Called by a consumer on destroy; polling stops once the last one leaves. */
  release(): void {
    this.consumers = Math.max(0, this.consumers - 1);
    if (this.consumers > 0) return;

    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    this.loadSub?.unsubscribe();
    this.loadSub = undefined;
    this.historySub?.unsubscribe();
    this.historySub = undefined;
  }

  private load(): void {
    if (this.status() === 'idle') this.status.set('loading');

    this.loadSub?.unsubscribe();
    this.loadSub = this.market.getHeatmap().subscribe({
      next: response => {
        this.sectors = response.sectors ?? [];
        this.recompute();
        this.loadHistories();
      },
      error: err => {
        console.warn('Sector movers snapshot unavailable:', err);
        // Keep whatever is already on screen; an empty strip is worse than a stale one.
        this.status.set(this.movers().length > 0 ? 'ready' : 'error');
      }
    });
  }

  private loadHistories(): void {
    this.historySub?.unsubscribe();
    this.historySub = undefined;
    const tickers = this.sectors.flatMap(sector => sector.tickers.map(t => t.ticker));
    if (tickers.length === 0) return;

    // Shared with the heatmap, so opening that page costs no extra requests and vice versa.
    this.historySub = this.historyCache.load(tickers).subscribe({
      next: ([ticker, bars]) => {
        this.histories.update(current => new Map(current).set(ticker, bars));
        this.recompute();
      },
      error: err => console.warn('Sector movers history unavailable:', err)
    });
  }

  private recompute(): void {
    const movers = selectSectorMovers(this.sectors, this.histories(), PERIOD);
    this.movers.set(movers);
    if (movers.length > 0) this.status.set('ready');
  }
}
