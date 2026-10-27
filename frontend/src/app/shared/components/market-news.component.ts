import { Component, Input, OnChanges, OnDestroy, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { MarketDataService } from '../../core/services/market-data.service';
import { TranslationService } from '../../core/services/translation.service';
import { TranslatePipe } from '../../core/pipes/translate.pipe';
import { NewsItem } from '../../core/models/market.model';

/** Tickers used when the viewer holds nothing, so the panel still has something to say. */
const FALLBACK_TICKERS = ['AAPL', 'MSFT', 'NVDA'];

type NewsStatus = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'app-market-news',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    <section class="news-card" aria-labelledby="market-news-title">
      <div class="news-head">
        <h2 id="market-news-title" class="news-title font-display">{{ 'news.title' | translate }}</h2>
        <span class="news-source">{{ 'news.source' | translate }}</span>
      </div>

      <p *ngIf="status() === 'loading'" class="news-state" aria-busy="true">
        {{ 'news.loading' | translate }}
      </p>

      <p *ngIf="status() === 'empty'" class="news-state">{{ 'news.empty' | translate }}</p>

      <p *ngIf="status() === 'error'" class="news-state" role="alert">
        {{ 'news.error' | translate }}
      </p>

      <ul *ngIf="status() === 'ready'" class="news-list">
        <li *ngFor="let item of items()" class="news-item">
          <!-- Third-party destination: noopener keeps the opener handle away from it. -->
          <a class="news-link" [href]="item.link" target="_blank" rel="noopener noreferrer">
            <span class="news-headline">{{ item.title }}</span>
            <span class="news-meta">
              <span *ngIf="item.publisher" class="news-publisher">{{ item.publisher }}</span>
              <span *ngIf="item.publisher && relativeTime(item)" aria-hidden="true">·</span>
              <span *ngIf="relativeTime(item)" class="news-time">{{ relativeTime(item) }}</span>
            </span>
          </a>
        </li>
      </ul>
    </section>
  `,
  styles: [`
    .news-card {
      display: flex;
      flex-direction: column;
      padding: 1.25rem;
      border: 1px solid var(--border);
      border-radius: 1rem;
      background: var(--card);
    }

    .news-head {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }

    .news-title {
      margin: 0;
      font-size: 0.9375rem;
      font-weight: 700;
      color: var(--foreground);
    }

    .news-source {
      font-size: 0.6875rem;
      color: var(--muted-foreground);
    }

    .news-state {
      margin: 0;
      padding: 0.75rem 0;
      font-size: 0.8125rem;
      color: var(--muted-foreground);
    }

    .news-list {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .news-item + .news-item {
      border-top: 1px solid var(--border);
    }

    .news-link {
      display: flex;
      flex-direction: column;
      gap: 0.1875rem;
      padding: 0.5rem 0.25rem;
      border-radius: 0.5rem;
      color: var(--foreground);
      text-decoration: none;
    }

    .news-link:hover { background: var(--secondary); }

    .news-link:focus-visible {
      outline: 2px solid var(--ring, var(--primary));
      outline-offset: -2px;
    }

    .news-headline {
      font-size: 0.8125rem;
      font-weight: 600;
      line-height: 1.35;
      /* Headlines vary wildly in length; two lines keeps the card a predictable height. */
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .news-meta {
      display: flex;
      align-items: center;
      gap: 0.3125rem;
      font-size: 0.6875rem;
      color: var(--muted-foreground);
    }

    .news-publisher { font-weight: 500; }
  `]
})
export class MarketNewsComponent implements OnChanges, OnDestroy {
  /** Tickers to pull headlines for, normally the viewer's holdings. */
  @Input() tickers: string[] = [];

  /** Headlines shown; the panel is deliberately a glance, not a feed. */
  @Input() limit = 5;

  private readonly market = inject(MarketDataService);
  private readonly translation = inject(TranslationService);

  readonly items = signal<NewsItem[]>([]);
  readonly status = signal<NewsStatus>('loading');

  private sub?: Subscription;
  private lastKey = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['tickers'] && !changes['limit']) return;

    const tickers = this.resolveTickers();
    const key = `${this.limit}:${tickers.join(',')}`;
    // Holdings reload on every trade and poll; only refetch when the set actually changed.
    if (key === this.lastKey) return;

    this.lastKey = key;
    this.load(tickers);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  /** "3h ago" style, in the active locale; null when the provider gave no timestamp. */
  relativeTime(item: NewsItem): string | null {
    if (!item.publishedAt) return null;

    const published = new Date(item.publishedAt).getTime();
    if (!Number.isFinite(published)) return null;

    const minutes = Math.round((published - Date.now()) / 60_000);
    const formatter = new Intl.RelativeTimeFormat(this.translation.currentLanguage(), {
      numeric: 'auto',
      style: 'narrow'
    });

    const absolute = Math.abs(minutes);
    if (absolute < 60) return formatter.format(minutes, 'minute');
    if (absolute < 60 * 24) return formatter.format(Math.round(minutes / 60), 'hour');
    return formatter.format(Math.round(minutes / (60 * 24)), 'day');
  }

  private resolveTickers(): string[] {
    const held = [...new Set((this.tickers ?? []).filter(Boolean))];
    return held.length > 0 ? held.slice(0, 8) : FALLBACK_TICKERS;
  }

  private load(tickers: string[]): void {
    this.sub?.unsubscribe();
    this.status.set(this.items().length > 0 ? 'ready' : 'loading');

    this.sub = this.market.getNews(tickers, this.limit).subscribe({
      next: response => {
        const items = response.items ?? [];
        this.items.set(items);
        this.status.set(items.length === 0 ? 'empty' : 'ready');
      },
      error: err => {
        console.warn('Market news unavailable:', err);
        // Headlines are a nicety; a stale list beats replacing it with an error.
        this.status.set(this.items().length > 0 ? 'ready' : 'error');
      }
    });
  }
}
