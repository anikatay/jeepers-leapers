import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { HunterService } from '../../../../core/services/hunter.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { WatchlistService } from '../../../../core/services/watchlist.service';
import { HunterError, WatchlistItem } from '../../../../core/models/hunter.model';
import { errorMessage, formatDate, formatMonth } from '../hunter/hunter-format';
import '../hunter/hunter-i18n';

/** The caller's watchlist (docs/specs/watchlist-hunter.md, sections 7 and 8.2). */
@Component({
  selector: 'app-watchlist-page',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './watchlist-page.component.html',
  styleUrl: './watchlist-page.component.css'
})
export class WatchlistPageComponent implements OnInit {
  private hunters = inject(HunterService);
  private translation = inject(TranslationService);
  readonly watchlist = inject(WatchlistService);

  loading = signal(true);
  pendingTicker = signal<string | null>(null);
  error = signal<string | null>(null);
  private knowledgeCutoff = signal<string | null>(null);

  disclaimer = computed(() => {
    const cutoff = this.knowledgeCutoff();
    return cutoff
      ? this.translation.format('hunter.disclaimer', { month: formatMonth(cutoff, this.translation.currentLanguage()) })
      : null;
  });

  ngOnInit() {
    this.watchlist.load().pipe(finalize(() => this.loading.set(false))).subscribe({
      error: (error: HunterError) => this.error.set(errorMessage(error, 'other', this.translation))
    });
    this.hunters.catalog().subscribe({
      next: catalog => this.knowledgeCutoff.set(catalog.knowledgeCutoff),
      error: () => undefined
    });
  }

  remove(item: WatchlistItem) {
    this.error.set(null);
    this.pendingTicker.set(item.ticker);
    this.watchlist.remove(item.ticker).pipe(finalize(() => this.pendingTicker.set(null))).subscribe({
      error: (error: HunterError) => this.error.set(errorMessage(error, 'watchlist', this.translation))
    });
  }

  added(item: WatchlistItem): string {
    return formatDate(item.addedAt, this.translation.currentLanguage());
  }

  removeLabel(item: WatchlistItem): string {
    return this.translation.format('hunter.remove_watchlist_named', { ticker: item.ticker });
  }
}
