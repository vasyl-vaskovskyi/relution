import { TestBed } from '@angular/core/testing';
import { DEBUG_LOG_CONFIG } from '../debug/debug-log.service';
import { LocaleService, servedLanguageDiffers } from './locale.service';

describe('LocaleService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: DEBUG_LOG_CONFIG,
          useValue: { debugLogging: false, debugLoggingOverride: false },
        },
      ],
    });
  });

  it('pre-fills the German storefront', () => {
    expect(TestBed.inject(LocaleService).prefill()).toEqual({ cc: 'de', l: 'de' });
  });

  it('returns a copy, so callers cannot change the default', () => {
    const service = TestBed.inject(LocaleService);
    service.prefill().cc = 'us';

    expect(service.prefill()).toEqual({ cc: 'de', l: 'de' });
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
