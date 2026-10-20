import { Component, ElementRef, HostListener, afterNextRender, computed, inject, input, output, viewChild } from '@angular/core';
import { formatDate } from '@angular/common';
import { TranslatePipe } from '../../../../../core/pipes/translate.pipe';
import { TranslationService } from '../../../../../core/services/translation.service';
import { AnalysisErrorKind, TradeAnalysisDialogState, Verdict } from '../../../../../core/models/trade-analysis.model';

export interface AnalysedTrade {
  symbol: string;
  type: 'Buy' | 'Sell';
  qty: number;
  date: string;
}

const VERDICT_CLASS: Record<Verdict, string> = {
  GOOD: 'verdict-good',
  MIXED: 'verdict-mixed',
  POOR: 'verdict-poor',
  TOO_EARLY: 'verdict-too-early',
};

const ERROR_KEY: Record<AnalysisErrorKind, string> = {
  'session-expired': 'trades.analysis.error.session_expired',
  'not-found': 'trades.analysis.error.not_found',
  'rate-limited': 'trades.analysis.error.rate_limited',
  'unavailable': 'trades.analysis.error.unavailable',
  'unknown': 'trades.analysis.error.unknown',
};

@Component({
  selector: 'app-trade-analysis-dialog',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './trade-analysis-dialog.component.html',
  styleUrl: './trade-analysis-dialog.component.css'
})
export class TradeAnalysisDialogComponent {
  trade = input.required<AnalysedTrade>();
  state = input.required<TradeAnalysisDialogState>();
  closed = output<void>();

  private translation = inject(TranslationService);
  private closeButton = viewChild.required<ElementRef<HTMLButtonElement>>('closeButton');

  loading = computed(() => this.state().status === 'loading');

  analysis = computed(() => {
    const state = this.state();
    return state.status === 'done' ? state.analysis : null;
  });

  verdictClass = computed(() => {
    const analysis = this.analysis();
    return analysis ? VERDICT_CLASS[analysis.result.verdict] : '';
  });

  sections = computed(() => {
    const analysis = this.analysis();
    if (!analysis) {
      return [];
    }
    return [
      { titleKey: 'trades.analysis.strengths', items: analysis.result.strengths },
      { titleKey: 'trades.analysis.concerns', items: analysis.result.concerns },
      { titleKey: 'trades.analysis.suggestions', items: analysis.result.suggestions },
    ].filter(section => section.items.length > 0);
  });

  /** Null when the context request failed, so whether P&L was included is unknown. */
  pnlLine = computed(() => {
    const pnl = this.analysis()?.pnl;
    if (pnl?.kind === 'calculated') {
      return this.translation.format('trades.analysis.pnl_as_of', {
        time: formatDate(pnl.asOf, 'MMM d, y, h:mm a', 'en-US'),
      });
    }
    return pnl?.kind === 'not-calculated' ? this.translation.translate('trades.analysis.pnl_not_calculated') : null;
  });

  errorMessage = computed(() => {
    const state = this.state();
    if (state.status !== 'error') {
      return null;
    }
    const seconds = state.error.retryAfterSeconds ?? 0;
    // Intl gets the plural right in each language: "1 second", "41 seconds", "41 segundos"
    const duration = new Intl.NumberFormat(this.translation.currentLanguage(),
      { style: 'unit', unit: 'second', unitDisplay: 'long' }).format(seconds);
    return this.translation.format(ERROR_KEY[state.error.kind], { duration });
  });

  subtitle = computed(() => {
    const trade = this.trade();
    const side = this.translation.translate(trade.type === 'Buy' ? 'trades.buy' : 'trades.sell');
    return `${trade.symbol} · ${side} · ${trade.qty} · ${trade.date}`;
  });

  constructor() {
    afterNextRender(() => this.closeButton().nativeElement.focus());
  }

  @HostListener('document:keydown.escape')
  close(): void {
    this.closed.emit();
  }
}
