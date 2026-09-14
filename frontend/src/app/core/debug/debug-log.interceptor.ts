import {
  HttpErrorResponse,
  HttpHeaders,
  HttpInterceptorFn,
  HttpParams,
  HttpResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { DebugLogService } from './debug-log.service';

/**
 * Logs one collapsed group per request: `GET /api/v1/apps 200 143ms` with the parameters (the term only
 * as its length), X-Correlation-Id, the item count or the ProblemDetail, and Retry-After. Never logs
 * headers other than those two, request bodies or tokens.
 */
export const debugLogInterceptor: HttpInterceptorFn = (req, next) => {
  const log = inject(DebugLogService);
  if (!log.enabled()) {
    return next(req);
  }
  const started = performance.now();
  const path = req.url.split('?')[0];
  const title = (status: number) =>
    `${req.method} ${path} ${status} ${Math.round(performance.now() - started)}ms`;

  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          log.group(title(event.status), {
            params: safeParams(req.params),
            correlationId: header(event.headers, 'X-Correlation-Id'),
            result: summarizeBody(event.body),
          });
        }
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse) {
          log.group(title(error.status), {
            params: safeParams(req.params),
            correlationId: header(error.headers, 'X-Correlation-Id'),
            problem: summarizeProblem(error.error),
            retryAfter: header(error.headers, 'Retry-After'),
          });
        }
      },
    }),
  );
};

/** Request parameters for the log: `term` is replaced by `termLength` (GDPR, security.md). */
export function safeParams(params: HttpParams): Record<string, string | number> | undefined {
  const keys = params.keys();
  if (keys.length === 0) {
    return undefined;
  }
  const result: Record<string, string | number> = {};
  for (const key of keys) {
    const value = (params.getAll(key) ?? []).join(',');
    if (key === 'term') {
      result['termLength'] = value.length;
    } else {
      result[key] = value;
    }
  }
  return result;
}

function header(headers: HttpHeaders, name: string): string | undefined {
  return headers.get(name) ?? undefined;
}

function summarizeBody(body: unknown): Record<string, unknown> | undefined {
  if (body === null || typeof body !== 'object') {
    return undefined;
  }
  const record = body as Record<string, unknown>;
  if ('accessToken' in record) {
    // Token response: only the metadata, never the token
    return { tokenType: record['tokenType'], expiresIn: record['expiresIn'] };
  }
  if (Array.isArray(record['items'])) {
    return { count: record['items'].length, storefront: record['storefront'] };
  }
  if ('id' in record && 'storefront' in record) {
    return { id: record['id'], storefront: record['storefront'] };
  }
  return undefined;
}

function summarizeProblem(body: unknown): unknown {
  if (body === null || typeof body !== 'object') {
    return body === null || body === undefined ? undefined : '(no problem detail body)';
  }
  const { type, title, status, detail, instance, correlationId, errors } = body as Record<
    string,
    unknown
  >;
  return { type, title, status, detail, instance, correlationId, errors };
}
