import { Injectable, inject } from '@angular/core';
import { DebugLogService } from '../debug/debug-log.service';

export interface LocalePrefill {
  cc: string;
  l: string;
}

/**
 * Pre-fill for `cc` and `l` when the URL has none: the German storefront, whatever the browser language is
 * (docs/architecture/frontend.md#locale-pre-fill, ADR-0053). Both fields stay editable.
 */
export const DEFAULT_PREFILL: Readonly<LocalePrefill> = { cc: 'de', l: 'de' };

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
  private readonly log = inject(DebugLogService);
  private logged = false;

  prefill(): LocalePrefill {
    if (!this.logged) {
      this.logged = true;
      this.log.log('locale pre-fill', { ...DEFAULT_PREFILL });
    }
    return { ...DEFAULT_PREFILL };
  }
}
