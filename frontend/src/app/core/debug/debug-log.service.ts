import { Injectable, InjectionToken, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface DebugLogConfig {
  debugLogging: boolean;
  debugLoggingOverride: boolean;
}

export const DEBUG_LOG_CONFIG = new InjectionToken<DebugLogConfig>('DEBUG_LOG_CONFIG', {
  providedIn: 'root',
  factory: () => environment,
});

/** Runtime switch, honored only when the build sets `debugLoggingOverride` (the `demo` configuration). */
export const DEBUG_LOG_OVERRIDE_KEY = 'appstore.debug';

const PREFIX = '[appstore]';

/**
 * The only place that writes to the browser console (docs/architecture/frontend.md#debug-logging).
 * Callers must never pass tokens, the Authorization header, credentials or search terms.
 */
@Injectable({ providedIn: 'root' })
export class DebugLogService {
  private readonly config = inject(DEBUG_LOG_CONFIG);

  /** Read on every call, so the demo override works without a reload. */
  enabled(): boolean {
    if (this.config.debugLoggingOverride) {
      const override = readOverride();
      if (override !== null) {
        return override;
      }
    }
    return this.config.debugLogging;
  }

  log(message: string, data?: Record<string, unknown>): void {
    if (!this.enabled()) {
      return;
    }
    if (data === undefined) {
      console.log(`${PREFIX} ${message}`);
    } else {
      console.log(`${PREFIX} ${message}`, data);
    }
  }

  /** One collapsed console group; entries with an undefined value are skipped. */
  group(title: string, entries: Record<string, unknown>): void {
    if (!this.enabled()) {
      return;
    }
    console.groupCollapsed(`${PREFIX} ${title}`);
    for (const [key, value] of Object.entries(entries)) {
      if (value !== undefined) {
        console.log(`${key}:`, value);
      }
    }
    console.groupEnd();
  }
}

function readOverride(): boolean | null {
  try {
    const value = globalThis.localStorage?.getItem(DEBUG_LOG_OVERRIDE_KEY);
    return value === 'true' ? true : value === 'false' ? false : null;
  } catch {
    // Storage can be blocked (privacy settings); fall back to the build flag
    return null;
  }
}
