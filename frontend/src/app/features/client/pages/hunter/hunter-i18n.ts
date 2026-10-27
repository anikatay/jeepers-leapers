import { HUNTER_TRANSLATIONS } from '../../../../core/i18n/hunter.translations';
import { registerTranslations } from '../../../../core/services/translation.service';

// Imported for its effect by every Watchlist Hunter component: the strings load with the lazy
// Hunter pages instead of the initial bundle.
registerTranslations(HUNTER_TRANSLATIONS);
