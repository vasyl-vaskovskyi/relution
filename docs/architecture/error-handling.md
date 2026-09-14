# Error handling

The client-facing problem types are listed in [`../api/README.md`](../api/README.md#errors). This document describes how the service decides which one to return.

## Principles

- **Validate before calling Apple.** Invalid input never costs upstream budget.
- **Responses never contain Apple bodies, stack traces or internal maintenance hints.**
- **Every error response carries a `correlationId`,** and every error log line carries the same id.
- **Retry only what can succeed on retry:** connection failures ([`caching-resilience.md`](caching-resilience.md)).

## Storefront policy ([ADR-0008](../adr/0008-storefront-allowlist-that-reports-its-own-staleness.md))

Every `cc` passes through `StorefrontPolicy` before any Apple call. The allowlist is `SupportedStorefronts`: 175 codes, with its source and verification date in the constant's comment. The full list is in [`../integrations/apple-api-behavior.md`](../integrations/apple-api-behavior.md#4-app-store-storefront-allowlist).

| Case | Client gets | Internally |
|---|---|---|
| `cc` is not 2 letters, or is neither on the allowlist nor an ISO 3166-1 country (`xk` is on the allowlist but not ISO) | **400** `invalid-request` | — |
| On the allowlist, Apple serves it | normal response | — |
| On the allowlist, but Apple rejects it (Search 400 `[country]`, or `meta.storefront.cc` ≠ `cc`, compared case-insensitively) | **400** `unsupported-storefront` | **ERROR** `storefront allowlist outdated`, counter `appstore.storefront.allowlist.mismatch{direction=outdated}` |
| A valid ISO code **not** on the allowlist | **400** `unsupported-storefront`, without calling Apple | **WARN** `storefront missing from allowlist, verify the list`, counter `{direction=missing}` ([ADR-0043](../adr/0043-reject-unlisted-storefront-codes-locally.md)) |

The client never learns anything about the allowlist or how it is maintained. The refresh procedure is in [`../operations/runbook.md`](../operations/runbook.md).

## Mapping outcomes to responses

| Situation | HTTP | Problem type | Retried | Log |
|---|---|---|---|---|
| Invalid or missing parameters | 400 | `invalid-request` + `errors[]` | — | DEBUG |
| Storefront cases | see above | | | |
| Search returns no results | 200 `{items: [], count: 0}` | — | — | — |
| Lookup result `NotFound` (empty `results` or requested id missing) | 404 | `app-not-found` | — | DEBUG |
| Apple served a different language than requested | 200, `storefront.language` shows what was served | — | — | — |
| Apple 429 | 503 + `Retry-After` | `upstream-unavailable` | no | WARN |
| Local short-circuit while Apple's `Retry-After` runs | 503 + remaining `Retry-After` | `upstream-unavailable` | no | DEBUG |
| Outbound Search budget exhausted ([ADR-0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md)) | 503 + `Retry-After` | `upstream-unavailable` | no | WARN |
| Read timeout | 504 | `upstream-timeout` | no | WARN |
| Connection failure (after retries or retry `timeout`) | 502 | `upstream-error` | yes | WARN |
| Apple 5xx | 502 | `upstream-error` | no | WARN |
| Other Apple 4xx (e.g. 400 `status:7011`, 403) | 502 | `upstream-error` | no | ERROR (our integration bug) |
| Malformed or unexpected payload | 502 | `upstream-error` | no | ERROR |
| Missing, invalid or expired token; bad client credentials on `/auth/token` | 401 | `unauthorized` | — | INFO (no credentials) |
| Valid token without the required scope | 403 | `forbidden` | — | INFO |
| Any other path on port 8080 (e.g. `/actuator/env`): catch-all `denyAll` | 401 without a token, 403 with one | `unauthorized` / `forbidden` | — | DEBUG |
| Unexpected exception | 500 | `internal` | — | ERROR with stack trace (server side only) |

- **Implementation:** a `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`, with an exhaustive `switch` over the sealed `UpstreamException`. The `ProblemType` enum is the only place problem-type URNs and titles are defined.
- **Security errors:** the Spring Security entry point and access-denied handler write the same format via the `api` package's `ProblemDetails` factory. This is the only allowed `auth → api` dependency ([`overview.md`](overview.md#dependency-rules-enforced-by-an-archunit-test)).

## Correlation id

This section is the single home for the correlation-id rules.

- **Validation:** an incoming `X-Correlation-Id` is used only if it matches `^[A-Za-z0-9._-]{1,64}$`. Otherwise it is ignored.
- **Tracing disabled (Level 1):** the correlation id is the valid incoming id, or a new UUID.
- **Tracing enabled (Level 2):** the correlation id is the trace id (W3C `traceparent`). A valid incoming `X-Correlation-Id` is kept in the MDC as `clientCorrelationId`, so it can still be searched.
- **Where it appears:**
  - the MDC as `correlationId`, propagated to cache loader threads ([`caching-resilience.md`](caching-resilience.md#caches));
  - the `X-Correlation-Id` response header;
  - every ProblemDetail.
