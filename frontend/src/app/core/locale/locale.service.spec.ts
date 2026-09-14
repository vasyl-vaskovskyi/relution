import { TestBed } from '@angular/core/testing';
import { DEBUG_LOG_CONFIG } from '../debug/debug-log.service';
import {
  BROWSER_LANGUAGES,
  LocaleService,
  prefillFromLanguages,
  servedLanguageDiffers,
} from './locale.service';

describe('prefillFromLanguages', () => {
  it('takes cc and l from a tag with a region', () => {
    expect(prefillFromLanguages(['de-DE'])).toEqual({ cc: 'de', l: 'de' });
  });

  it('uses cc=us and the language when no entry has a region', () => {
    expect(prefillFromLanguages(['en'])).toEqual({ cc: 'us', l: 'en' });
  });

  it('lets the first entry with a region win', () => {
    expect(prefillFromLanguages(['en', 'fr-CA', 'de-DE'])).toEqual({ cc: 'ca', l: 'fr' });
  });

  it('takes the language from the first entry when none has a region', () => {
    expect(prefillFromLanguages(['fr', 'de'])).toEqual({ cc: 'us', l: 'fr' });
  });

  it('skips script subtags and numeric regions', () => {
    expect(prefillFromLanguages(['zh-Hans-CN'])).toEqual({ cc: 'cn', l: 'zh' });
    expect(prefillFromLanguages(['es-419', 'pt-BR'])).toEqual({ cc: 'br', l: 'pt' });
  });

  it('accepts underscores and normalizes to lower case', () => {
    expect(prefillFromLanguages(['de_AT'])).toEqual({ cc: 'at', l: 'de' });
  });

  it('falls back to us/en without usable languages', () => {
    expect(prefillFromLanguages([])).toEqual({ cc: 'us', l: 'en' });
    expect(prefillFromLanguages(['', 'fil'])).toEqual({ cc: 'us', l: 'en' });
  });
});

describe('LocaleService', () => {
  it('pre-fills from the browser languages', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: BROWSER_LANGUAGES, useValue: ['en-US', 'de-DE'] },
        {
          provide: DEBUG_LOG_CONFIG,
          useValue: { debugLogging: false, debugLoggingOverride: false },
        },
      ],
    });

    expect(TestBed.inject(LocaleService).prefill()).toEqual({ cc: 'us', l: 'en' });
  });
});

describe('servedLanguageDiffers', () => {
  it('detects another language', () => {
    expect(servedLanguageDiffers('fr', 'de-de')).toBe(true);
  });

  it('accepts any region when none was requested', () => {
    expect(servedLanguageDiffers('de', 'de-de')).toBe(false);
  });

  it('compares the region when one was requested', () => {
    expect(servedLanguageDiffers('en-GB', 'en-us')).toBe(true);
    expect(servedLanguageDiffers('de_DE', 'de-de')).toBe(false);
  });

  it('does not flag an unknown served language', () => {
    expect(servedLanguageDiffers('de', null)).toBe(false);
  });
});
