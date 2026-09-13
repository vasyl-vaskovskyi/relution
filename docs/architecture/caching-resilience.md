# Caching and resilience

**Why this matters:** the service depends on two external APIs. One is rate-limited **per source IP**, and our server is that single IP for all of its users. The other is Legacy and undocumented. Caching and resilience protect availability and Apple's budget, and Apple itself recommends caching. This was the challenge's "Part 3" direction ([ADR-0007](../adr/0007-part-3-direction-resilience-caching.md)).

Property names and default values: [`../operations/configuration.md`](../operations/configuration.md).

## Timeouts

- **Settings:** connect timeout `appstore.apple.timeout.connect` (2 s), read timeout `appstore.apple.timeout.read` (5 s), applied to both Apple `RestClient`s.
- **Classification:** `RestClient` wraps both timeout kinds in `ResourceAccessException`, and the cause depends on the request factory (JDK client: `HttpConnectTimeoutException` vs `HttpTimeoutException`). The clients translate them into `UpstreamConnectException` and `UpstreamReadTimeoutException`. WireMock tests prove the classification.

## Retry

See [ADR-0030](../adr/0030-details-cache-and-bounded-retry.md).

```java
@Retryable(includes = UpstreamConnectException.class,
           maxRetriesString = "${appstore.apple.retry.max}",
           timeoutString = "${appstore.apple.retry.timeout}",
           delay = 200, multiplier = 2, jitter = 100, timeUnit = TimeUnit.MILLISECONDS)
```

- **Verify on the day:** the exact attribute names, and whether `timeoutString` accepts `PT8S`.
- **Enabling:** `@EnableResilientMethods` on a configuration class.
- **Proxy limit:** `@Retryable` works through a Spring proxy, so it goes on the public methods of `ItunesSearchClient` and `MzLookupClient`, and those methods are called from the gateway adapters, never from inside the client class itself.
- **Only connection failures are retried.** Read timeouts, Apple 5xx, 4xx, 429 and malformed payloads are not.
- **Latency:**
  - A single failing call takes at most about 7 s (2 s connect + 5 s read timeout).
  - The theoretical worst case is about 12 s: two connection failures with back-off, then a read timeout. The retry `timeout` (8 s) stops new attempts but doesn't abort one already running (*verify*).
  - nginx's `proxy_read_timeout` (15 s) sits above that.
- **Metrics and logs per logical call:** the gateway adapters wrap the retryable client and record one `appstore.apple.requests` sample and one INFO line per logical call, after retries ([`../operations/observability.md`](../operations/observability.md)).

## 429 short-circuit

See [ADR-0031](../adr/0031-rate-limit-short-circuit.md).

- **Setting the block:** when Apple answers a Search call with 429, `SearchRateLimitGuard` stores `blockedUntil = now + Retry-After` (default 30 s, capped at `appstore.apple.retryafter.max`).
- **While blocked:** `SearchGatewayAdapter` throws `UpstreamRateLimitedException` without calling Apple. The client gets 503 with the remaining seconds, and the metric outcome is `short_circuited`.
- **Scope:** the guard is per instance and applies to Search only.

## Caches

The caches are native Caffeine `AsyncCache`s (`buildAsync()`, `recordStats()`), used directly by the `catalog` services.

| Cache | Key | Value and TTL | Size |
|---|---|---|---|
| `app-search` | `(term.strip().toLowerCase(Locale.ROOT), cc, limit)` | Search result, 10 min | 1 000 |
| `app-details` | `(id, cc, normalized l, platform)` | `LookupResult`: `Found` 15 min (= Apple `max-age=900`), `NotFound` 60 s, via `Caffeine.expireAfter(Expiry)` | 5 000 |
| `storefront-verdict` | `cc` (valid ISO codes that aren't on the allowlist) | Served or rejected, 24 h | 250 |

- **Single-flight:** concurrent identical requests share one in-flight future, so one upstream call. That includes lookups of unknown ids.
- **Failures are never cached:** Caffeine removes a future that completes exceptionally.
- **Search term:** Apple receives the **trimmed original** term. Only the cache key is lower-cased, on the *inferred* assumption that Apple's search is case-insensitive (*verify* on the day; if it isn't, drop the lower-casing).
- **Language normalization:** before `l` becomes part of the key, it is lower-cased and `_` becomes `-` (`de_DE`, `de-DE` → `de-de`).
- **Loader executor:**
  - Set explicitly with `Caffeine.executor(...)`: a virtual-thread-per-task executor wrapped with Micrometer context propagation (`ContextExecutorService`), so the MDC (`correlationId`, `clientId`) reaches the loader thread (*verify* the SLF4J MDC accessor on the day).
  - `spring.threads.virtual.enabled` covers request threads only; it doesn't configure Caffeine.
- **Single-flight and logs:** with single-flight, the upstream log line carries the ids of the request that triggered the load.
- **Metrics:** bound explicitly with Micrometer `CaffeineCacheMetrics` (*verify* `AsyncCache` support on the day), producing `cache.gets{cache, result}`.

## Known limits

| Limit | Consequence | Next step at scale |
|---|---|---|
| Caches, the 429 guard and the stretch-goal limiter are per instance | N instances behind one egress IP share Apple's budget but don't coordinate | Shared cache (e.g. Redis) and a central budget |
| The storefront allowlist is static | Staleness is detected and logged, but not fixed automatically | Runbook refresh every 6 months |
| Browser-direct search to spread load across user IPs | Not implemented | Open question [ADR-0026](../adr/0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md) |

## Stretch goals (only after all planned work, in this order)

1. **Level 2 observability:** see [`../operations/observability.md`](../operations/observability.md).
2. **Outbound rate limiter on Search:**
   - Budget `appstore.apple.search.budget` (20 per minute).
   - It wraps the actual HTTP call inside the retry, so every attempt takes a token. When exhausted, the client gets 503 + `Retry-After`.
   - The implementation choice (Resilience4j `RateLimiter` vs a small token bucket) needs the maintainer's approval.
3. **Circuit breaker** (Resilience4j). Never put it on the same method as `@Retryable`.
