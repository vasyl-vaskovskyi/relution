import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';
import { TokenResponse } from '../api/api.types';
import { DebugLogService } from '../debug/debug-log.service';

export const TOKEN_PATH = '/auth/token';

export function isTokenRequest(url: string): boolean {
  return url.split('?')[0].endsWith(TOKEN_PATH);
}

/**
 * Holds the access token in memory only, as a signal: never in localStorage or sessionStorage
 * (ADR-0018). Reloading the page means logging in again.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly log = inject(DebugLogService);
  private readonly accessToken = signal<string | null>(null);

  readonly authenticated = computed(() => this.accessToken() !== null);

  token(): string | null {
    return this.accessToken();
  }

  /** POST /auth/token with HTTP Basic. The credentials are not kept after the request. */
  login(clientId: string, clientSecret: string): Observable<void> {
    const headers = { Authorization: basicAuthorization(clientId, clientSecret) };
    return this.http.post<TokenResponse>(TOKEN_PATH, null, { headers }).pipe(
      map((response) => {
        this.accessToken.set(response.accessToken);
        this.log.log('auth: logged in', { expiresIn: response.expiresIn });
      }),
    );
  }

  logout(reason: string): void {
    if (this.accessToken() !== null) {
      this.accessToken.set(null);
      this.log.log('auth: logged out', { reason });
    }
  }
}

/** RFC 7617 with UTF-8, so non-ASCII credentials don't make btoa throw. */
function basicAuthorization(clientId: string, clientSecret: string): string {
  const bytes = new TextEncoder().encode(`${clientId}:${clientSecret}`);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return `Basic ${btoa(binary)}`;
}
