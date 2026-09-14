import { Injectable, InjectionToken, inject } from '@angular/core';
import { DebugLogService } from '../debug/debug-log.service';

export interface LocalePrefill {
  cc: string;
  l: string;
}

export const FALLBACK_CC = 'us';
export const FALLBACK_LANGUAGE = 'en';

export const BROWSER_LANGUAGES = new InjectionToken<readonly string[]>('BROWSER_LANGUAGES', {
  providedIn: 'root',
  factory: () => {
    if (typeof navigator === 'undefined') {
      return [];
    }
    return navigator.languages?.length ? navigator.languages : [navigator.language];
  },
});

const LANGUAGE = /^[a-z]{2}$/i;
const REGION = /^[a-z]{2}$/i;

/**
 * Pre-fills `cc` and `l` (docs/architecture/frontend.md#locale-pre-fill): the first entry with a region
 * wins (`de-DE` → de/de). Without any region, `l` comes from the first language and `cc` is `us`.
 */
export function prefillFromLanguages(languages: readonly string[]): LocalePrefill {
  let firstLanguage: string | undefined;
  for (const tag of languages) {
    const [language, ...subtags] = (tag ?? '').split(/[-_]/);
    if (!LANGUAGE.test(language)) {
      continue;
    }
    firstLanguage ??= language.toLowerCase();
    // Skips script subtags (zh-Hans-CN) and numeric regions (es-419)
    const region = subtags.find((subtag) => REGION.test(subtag));
    if (region) {
      return { cc: region.toLowerCase(), l: language.toLowerCase() };
    }
  }
  return { cc: FALLBACK_CC, l: firstLanguage ?? FALLBACK_LANGUAGE };
}

/**
 * True when Apple served another language than requested: `fr` vs `de-de`, or `en-gb` vs `en-us`.
 * A request without a region matches any region of that language (`de` vs `de-de`).
 */
export function servedLanguageDiffers(requested: string, served: string | null): boolean {
  if (!served) {
    return false;
  }
  const wanted = requested.toLowerCase().replace('_', '-');
  const got = served.toLowerCase().replace('_', '-');
  return wanted.includes('-') ? wanted !== got : got.split('-')[0] !== wanted;
}

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly languages = inject(BROWSER_LANGUAGES);
  private readonly log = inject(DebugLogService);
  private cached?: LocalePrefill;

  prefill(): LocalePrefill {
    if (!this.cached) {
      this.cached = prefillFromLanguages(this.languages);
      this.log.log('locale pre-fill', { ...this.cached, browserLanguages: [...this.languages] });
    }
    return this.cached;
  }
}
