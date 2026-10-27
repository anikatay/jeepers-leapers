import { HunterError, MetricInfo } from '../../../../core/models/hunter.model';
import { TranslationService } from '../../../../core/services/translation.service';

/** "75%", "$4,300 bn", "28×": a metric value with its catalog unit. */
export function formatMetricValue(value: number, metric: MetricInfo | undefined, lang: string): string {
  const number = new Intl.NumberFormat(lang, { maximumFractionDigits: 1 }).format(value);
  const unit = metric?.unit ?? '';
  if (unit === '$ bn') {
    return `$${number} bn`;
  }
  return `${number}${unit}`;
}

/** "May 2026" for the knowledge cutoff, in the selected language. */
export function formatMonth(isoDate: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, { month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${isoDate.slice(0, 10)}T00:00:00Z`));
}

export function formatDateTime(isoDateTime: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(isoDateTime));
}

export function formatDate(isoDateTime: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium' }).format(new Date(isoDateTime));
}

/** "41 seconds", "41 segundos": worded by Intl so the plural is right in every language. */
export function formatSeconds(seconds: number, lang: string): string {
  return new Intl.NumberFormat(lang, { style: 'unit', unit: 'second', unitDisplay: 'long' }).format(seconds);
}

export type ErrorContext = 'spec' | 'watchlist' | 'other';

/** The message for an error, worded for what the user was doing (section 8.3 of the spec). */
export function errorMessage(error: HunterError, context: ErrorContext, translation: TranslationService): string {
  const lang = translation.currentLanguage();
  switch (error.kind) {
    case 'invalid':
      return translation.translate('hunter.error_invalid');
    case 'session-expired':
      return translation.translate('hunter.error_session');
    case 'not-found':
      return translation.translate(context === 'watchlist' ? 'hunter.error_not_in_universe' : 'hunter.error_not_found');
    case 'limit-reached':
      return translation.translate(context === 'watchlist' ? 'hunter.error_limit_watchlist' : 'hunter.error_limit_specs');
    case 'rate-limited':
      return translation.format('hunter.error_rate', { duration: formatSeconds(error.retryAfterSeconds ?? 60, lang) });
    case 'unavailable':
      return translation.translate('hunter.error_unavailable');
    default:
      return translation.translate('hunter.error_unknown');
  }
}
