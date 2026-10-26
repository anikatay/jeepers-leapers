import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, finalize, switchMap, tap } from 'rxjs';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { HunterService } from '../../../../core/services/hunter.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { WatchlistService } from '../../../../core/services/watchlist.service';
import {
  HunterCatalog,
  HunterError,
  HunterFieldError,
  HunterMatch,
  HunterRun,
  HunterSpec,
  HunterSpecSummary,
} from '../../../../core/models/hunter.model';
import { HunterCardComponent } from './hunter-card/hunter-card.component';
import { MatchCardComponent } from './match-card/match-card.component';
import { ErrorContext, errorMessage, formatDate, formatDateTime, formatMonth } from './hunter-format';
import './hunter-i18n';

/** Watchlist Hunter (docs/specs/watchlist-hunter.md, section 8). */
@Component({
  selector: 'app-hunter-page',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HunterCardComponent, MatchCardComponent],
  templateUrl: './hunter-page.component.html',
  styleUrl: './hunter-page.component.css'
})
export class HunterPageComponent implements OnInit {
  private hunters = inject(HunterService);
  private translation = inject(TranslationService);
  private route = inject(ActivatedRoute);
  readonly watchlist = inject(WatchlistService);

  /** Sent to the model as written, so they stay in English like its replies. */
  readonly examples = [
    'High-margin AI infrastructure companies with improving cash flow',
    'Profitable healthcare companies with a P/E below 20',
    'Mega-cap tech companies with a subscription business model',
  ];

  catalog = signal<HunterCatalog | null>(null);
  prompt = signal('');
  spec = signal<HunterSpec | null>(null);
  saved = signal<HunterSpecSummary[]>([]);
  run = signal<HunterRun | null>(null);
  drafting = signal(false);
  running = signal(false);
  pendingTicker = signal<string | null>(null);
  error = signal<string | null>(null);
  fieldErrors = signal<HunterFieldError[]>([]);

  busy = computed(() => this.drafting() || this.running());
  promptLimit = computed(() => this.catalog()?.limits.maxPromptLength ?? 500);
  charactersLeft = computed(() => this.t('hunter.characters_left', { n: this.promptLimit() - this.prompt().length }));
  cardMonth = computed(() => {
    const catalog = this.catalog();
    return catalog ? formatMonth(catalog.knowledgeCutoff, this.translation.currentLanguage()) : '';
  });
  runMonth = computed(() => {
    const run = this.run();
    return run ? formatMonth(run.source.knowledgeCutoff, this.translation.currentLanguage()) : '';
  });
  scoredMatches = computed(() => (this.run()?.matches ?? []).filter(match => match.matchScore !== 0));
  zeroMatches = computed(() => (this.run()?.matches ?? []).filter(match => match.matchScore === 0));

  ngOnInit() {
    this.hunters.catalog().subscribe({
      next: catalog => this.catalog.set(catalog),
      error: (error: HunterError) => this.showError(error, 'other')
    });
    this.refreshSaved();
    this.watchlist.load().subscribe({ error: () => undefined });

    const specId = this.route.snapshot.queryParamMap.get('spec');
    if (specId) {
      this.open(specId);
    }
  }

  t(key: string, values: Record<string, string | number>): string {
    return this.translation.format(key, values);
  }

  build() {
    const prompt = this.prompt().trim();
    if (!prompt || this.busy()) {
      return;
    }
    this.clearMessages();
    this.drafting.set(true);
    this.run.set(null);
    this.hunters.draft(prompt).pipe(finalize(() => this.drafting.set(false))).subscribe({
      next: spec => this.spec.set(spec),
      error: (error: HunterError) => this.showError(error, 'spec')
    });
  }

  open(specId: string) {
    this.clearMessages();
    this.hunters.getSpec(specId).pipe(
      tap(spec => {
        this.spec.set(spec);
        this.prompt.set(spec.prompt);
        this.run.set(null);
      }),
      switchMap(() => this.hunters.latestRun(specId))
    ).subscribe({
      next: run => this.run.set(run),
      error: (error: HunterError) => this.showError(error, 'spec')
    });
  }

  remove(summary: HunterSpecSummary) {
    this.clearMessages();
    this.hunters.deleteSpec(summary.specId).subscribe({
      next: () => {
        this.saved.update(saved => saved.filter(item => item.specId !== summary.specId));
        if (this.spec()?.id === summary.specId) {
          this.spec.set(null);
          this.run.set(null);
        }
      },
      error: (error: HunterError) => this.showError(error, 'spec')
    });
  }

  /** Saves the card first, so a run always matches what is saved. */
  runHunter() {
    const spec = this.spec();
    if (!spec || this.busy()) {
      return;
    }
    this.clearMessages();
    this.running.set(true);
    this.hunters.saveSpec(spec).pipe(
      tap(saved => this.spec.set(saved)),
      switchMap(saved => this.hunters.run(saved.id!)),
      finalize(() => this.running.set(false))
    ).subscribe({
      next: run => {
        this.run.set(run);
        this.refreshSaved();
      },
      error: (error: HunterError) => {
        this.showError(error, 'spec');
        this.refreshSaved();
      }
    });
  }

  toggleWatchlist(match: HunterMatch) {
    if (this.pendingTicker()) {
      return;
    }
    this.error.set(null);
    this.pendingTicker.set(match.ticker);
    const request: Observable<unknown> = this.watchlist.has(match.ticker)
      ? this.watchlist.remove(match.ticker)
      : this.watchlist.add(match.ticker, this.spec()?.id ?? this.run()?.specId ?? null);
    request.pipe(finalize(() => this.pendingTicker.set(null))).subscribe({
      error: (error: HunterError) => this.showError(error, 'watchlist')
    });
  }

  savedLine(summary: HunterSpecSummary): string {
    return summary.lastRunAt
      ? this.t('hunter.last_run', {
        date: formatDate(summary.lastRunAt, this.translation.currentLanguage()),
        n: summary.lastMatchCount ?? 0
      })
      : this.translation.translate('hunter.never_run');
  }

  runLine(run: HunterRun): string {
    const date = formatDateTime(run.ranAt, this.translation.currentLanguage());
    return run.reused ? this.t('hunter.reused', { date }) : this.t('hunter.ran_at', { date });
  }

  setPrompt(value: string) {
    this.prompt.set(value);
  }

  private refreshSaved() {
    this.hunters.listSpecs().subscribe({
      next: saved => this.saved.set(saved),
      error: () => undefined
    });
  }

  private clearMessages() {
    this.error.set(null);
    this.fieldErrors.set([]);
  }

  private showError(error: HunterError, context: ErrorContext) {
    this.fieldErrors.set(error.fieldErrors ?? []);
    this.error.set(errorMessage(error, context, this.translation));
  }
}
