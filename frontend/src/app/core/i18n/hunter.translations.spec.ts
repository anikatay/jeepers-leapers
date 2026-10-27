import { TestBed } from '@angular/core/testing';
import { HUNTER_TRANSLATIONS } from './hunter.translations';
import { LANGUAGES, TranslationService, registerTranslations } from '../services/translation.service';

const placeholders = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort();

describe('Watchlist Hunter translations', () => {
  const english = HUNTER_TRANSLATIONS.en;

  it.each(LANGUAGES.map(language => language.code))('has every key, with the same placeholders, in %s', code => {
    const strings = HUNTER_TRANSLATIONS[code];

    expect(Object.keys(strings).sort()).toEqual(Object.keys(english).sort());
    for (const [key, text] of Object.entries(english)) {
      expect(strings[key].trim().length, `${code} ${key}`).toBeGreaterThan(0);
      expect(placeholders(strings[key]), `${code} ${key}`).toEqual(placeholders(text));
    }
  });

  it('is served by TranslationService in the selected language once registered', () => {
    const translation = TestBed.inject(TranslationService);
    registerTranslations(HUNTER_TRANSLATIONS);

    translation.setLanguage('es');
    expect(translation.translate('hunter.build')).toBe('Crear hunter');
    expect(translation.format('hunter.score', { n: 83 })).toBe('83% de coincidencia con la tesis');

    translation.setLanguage('en');
    expect(translation.format('hunter.score', { n: 83 })).toBe('83% thesis match');
  });
});
