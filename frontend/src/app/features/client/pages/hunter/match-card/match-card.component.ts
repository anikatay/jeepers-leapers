import { Component, computed, inject, input, output } from '@angular/core';
import { TranslatePipe } from '../../../../../core/pipes/translate.pipe';
import { TranslationService } from '../../../../../core/services/translation.service';
import { Criterion, EstimateValue, HunterCatalog, HunterMatch } from '../../../../../core/models/hunter.model';
import { formatMetricValue } from '../hunter-format';
import '../hunter-i18n';

/** One candidate match (section 8.2 of the spec). The score badge always shows its text. */
@Component({
  selector: 'app-match-card',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './match-card.component.html',
  styleUrl: './match-card.component.css'
})
export class MatchCardComponent {
  private translation = inject(TranslationService);

  match = input.required<HunterMatch>();
  catalog = input.required<HunterCatalog>();
  /** The spec's checks, to show each verdict next to its statement. */
  criteria = input<Criterion[]>([]);
  onWatchlist = input(false);
  busy = input(false);
  toggleWatchlist = output<void>();

  scoreText = computed(() => {
    const score = this.match().matchScore;
    return score === null
      ? this.translation.translate('hunter.matches_filters')
      : this.translation.format('hunter.score', { n: score });
  });

  scoreLevel = computed(() => {
    const score = this.match().matchScore;
    return score === null ? 'none' : score >= 75 ? 'high' : score >= 40 ? 'medium' : 'low';
  });

  watchlistLabel = computed(() => this.translation.format(
    this.onWatchlist() ? 'hunter.remove_watchlist_named' : 'hunter.add_watchlist_named',
    { ticker: this.match().ticker }));

  statement(criterionId: string): string {
    return this.criteria().find(criterion => criterion.id === criterionId)?.statement ?? criterionId;
  }

  estimateText(estimate: EstimateValue): string {
    const metric = this.catalog().metrics.find(m => m.id === estimate.metric);
    const label = this.translation.translate(`hunter.metric_${estimate.metric}`);
    const value = formatMetricValue(estimate.value, metric, this.translation.currentLanguage());
    return `${label} ≈ ${value}`;
  }
}
