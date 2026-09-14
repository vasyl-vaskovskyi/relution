# Caching and resilience

**Why this matters:** the service depends on two external APIs. One is rate-limited **per source IP**, and our server is that single IP for all of its users. The other is Legacy and undocumented. Caching and resilience protect availability and Apple's budget, and Apple itself recommends caching. This was the challenge's "Part 3" direction ([ADR-0007](../adr/0007-part-3-direction-resilience-caching.md)).

Property names and default values: [`../operations/configuration.md`](../operations/configuration.md).

## Timeouts

- **Settings:** connect timeout `appstore.apple.timeout.connect` (2 s), read timeout `appstore.apple.timeout.read` (5 s), applied to both Apple `RestClient`s.
- **Classification** (verified in the kickoff spike with the JDK request factory):
  - `RestClient` wraps both timeout kinds in `ResourceAccessException`.
  - A connect timeout has the cause `java.net.http.HttpConnectTimeoutException`; a read timeout has `java.net.http.HttpTimeoutException` (message "Request cancelled").
  - `HttpConnectTimeoutException` **extends** `HttpTimeoutException`, so the clients check for the connect type first. They translate the two into `UpstreamConnectException` and `UpstreamReadTimeoutException`. WireMock tests prove the classification.
  - Connection refused, no route to host and unknown host are also `UpstreamConnectException`. Any other I/O failure after the connection was established (e.g. a reset) becomes `UpstreamServerErrorException` with status `0` and is not retried.

## Retry

See [ADR-0030](../adr/0030-details-cache-and-bounded-retry.md).

```java
@Retryable(includes = UpstreamConnectException.class,
           maxRetriesString = "${appstore.apple.retry.max}",
           timeoutString = "${appstore.apple.retry.timeout}",
           delay = 200, multiplier = 2, jitter = 100, timeUnit = TimeUnit.MILLISECONDS)
```

- **Verified in the kickoff spike** (Spring Framework 7.0.9): the attribute names, `${…}` placeholders, and ISO durations such as `PT8S` in `timeoutString`.
- **Enabling:** `@EnableResilientMethods` on `AppleResilienceConfiguration`.
- **Tests:** `AppleRetryTest` goes through the real Spring proxy and counts attempts with a request interceptor: 3 for a refused connection (both clients), 1 for a read timeout.
- **Proxy limit:** `@Retryable` works through a Spring (CGLIB) proxy, so it goes on the public methods of `ItunesSearchClient` and `MzLookupClient`, and those methods are called from the gateway adapters, never from inside the client class itself. Tests read state through methods, not fields, because a proxy's fields are not the target's.
- **Only connection failures are retried.** Read timeouts, Apple 5xx, 4xx, 429 and malformed payloads are not.
- **Hidden retry in the JDK client:** `java.net.http.HttpClient` retries an idempotent request (all our Apple calls are GETs) once on a new connection when a connection closes before any response byte arrives. `@Retryable` never sees that attempt, and it isn't a separate metric sample. `ItunesSearchClientTest` pins the behavior (the server sees exactly 2 connections); each attempt is still bounded by the timeouts.
- **Latency:**
  - A single failing call takes at most about 7 s (2 s connect + 5 s read timeout).
  - The theoretical worst case is about 12 s: two connection failures with back-off, then a read timeout. The retry `timeout` (8 s) stops new attempts but doesn't abort one already running (confirmed in the spike).
  - nginx's `proxy_read_timeout` (15 s) sits above that.
- **Metrics and logs per logical call:** the gateway adapters wrap the retryable client and record one `appstore.apple.requests` sample and one INFO line per logical call, after retries ([`../operations/observability.md`](../operations/observability.md)).

## 429 short-circuit

See [ADR-0031](../adr/0031-rate-limit-short-circuit.md).

- **Setting the block:** when Apple answers a Search call with 429, `SearchRateLimitGuard` stores `blockedUntil = now + Retry-After` (default 30 s, capped at `appstore.apple.retryafter.max`).
- **While blocked:** `SearchGatewayAdapter` throws `UpstreamRateLimitedException` without calling Apple. The client gets 503 with the remaining seconds, and the metric outcome is `short_circuited`.
- **Scope:** the guard is per instance and applies to Search only.

## Outbound rate limiter on Search

See [ADR-0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md). The 429 short-circuit reacts after Apple has rejected a call; the limiter keeps us within the budget in the first place.

- **Budget:** `appstore.apple.search.budget` calls per minute (default 20, Apple's documented limit today). More requests can be bought, so the value is configuration only; no code or test assumes 20.
- **Where:** around the actual HTTP call, inside the retry, so every attempt takes a permit. Cache hits and single-flight followers never reach it.
- **When exhausted:** `UpstreamRateLimitedException` with the time until the next permit. The client gets 503 `upstream-unavailable` with `Retry-After`, and Apple is not called.
- **Scope:** per instance and Search only (details lookups have no known limit).
- **Implementation:** `SearchBudget` in `integration.apple`, a small token bucket chosen by the maintainer over Resilience4j (no new dependency).
  - The capacity is one minute's budget, refilled continuously (with 20: one permit every 3 s, bursts of up to 20).
  - `Retry-After` is the time until the next permit, rounded up to whole seconds (at least 1).
  - The exception carries `Reason.BUDGET`, so an exhausted budget never starts the 429 short-circuit.
- **Metric and alert:** outcome `budget_exhausted` (WARN), counted by the `AppleSearchRateLimited` alert ([`../operations/observability.md`](../operations/observability.md)).
- **Tests:** `SearchBudgetTest` (burst, refill, wait) and `AppleRetryTest` (a retry attempt takes its own permit, and an empty budget ends the retries).

## Caches

The caches are native Caffeine `AsyncCache`s (`buildAsync()`, `recordStats()`), used directly by the `catalog` services.

| Cache | Key | Value and TTL | Size |
|---|---|---|---|
| `app-search` | `(term.strip().toLowerCase(Locale.ROOT), cc, limit)` | Search result, 10 min | 1 000 |
| `app-details` | `(id, cc, normalized l, platform)` | `LookupResult`: `Found` 15 min (= Apple `max-age=900`), `NotFound` 60 s, via `Caffeine.expireAfter(Expiry)` | 5 000 |

- **Single-flight:** concurrent identical requests share one in-flight future, so one upstream call. That includes lookups of unknown ids.
- **Failures are never cached:** Caffeine removes a future that completes exceptionally, but it does so asynchronously, after waiting callers have already been woken. `CacheSupport.getOrLoad` therefore removes exactly that failed future before rethrowing, so the next caller always reaches the upstream (a test repeats failure-then-success 25 times on the real loader executor).
- **Tests with a fake ticker:** Caffeine records an entry's write time when the load completes, on the loader thread. TTL tests load on the calling thread, so the fake ticker only moves after that bookkeeping.
- **Search term:** Apple receives the **trimmed original** term. Only the cache key is lower-cased, because Apple's search is case-insensitive. Observed on 2026-09-14: `WhatsApp`, `whatsapp`, `WHATSAPP` and `wHaTsApP` returned identical results ([`../integrations/apple-api-behavior.md`](../integrations/apple-api-behavior.md#13-edge-cases-observed)).
- **Language normalization:** before `l` becomes part of the key, it is lower-cased and `_` becomes `-` (`de_DE`, `de-DE` → `de-de`).
- **Loader executor** (verified in the kickoff spike):
  - Set explicitly with `Caffeine.executor(...)`: a virtual-thread-per-task executor wrapped with `ContextExecutorService.wrap(executor, snapshotFactory)` from `io.micrometer:context-propagation` (Boot-managed; it is not pulled in transitively).
  - The snapshot factory uses its own `ContextRegistry` with a selective `Slf4jThreadLocalAccessor("correlationId", "clientId")`, so only those MDC keys reach the loader thread.
  - `spring.threads.virtual.enabled` covers request threads only; it doesn't configure Caffeine.
- **Single-flight and logs:** with single-flight, the upstream log line carries the ids of the request that triggered the load.
- **Metrics:** bound explicitly with Micrometer `CaffeineCacheMetrics.monitor(registry, asyncCache, name)`, which has an `AsyncCache` overload. It needs `recordStats()` and produces `cache.gets{cache, result}` (verified in the spike).

## Known limits

| Limit | Consequence | Next step at scale |
|---|---|---|
| Caches, the 429 guard and the outbound limiter are per instance | N instances behind one egress IP, or sharing one bought budget, don't coordinate | Shared cache (e.g. Redis) and a central budget |
| The storefront allowlist is static | Staleness is detected and logged, but not fixed automatically | Runbook refresh every 6 months |
| Browser-direct search to spread load across user IPs | Not implemented | Not for the Discovery Day; revisit after the team discussion ([ADR-0026](../adr/0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md)) |

## Circuit breaker

See [ADR-0047](../adr/0047-circuit-breaker-per-apple-api.md). Built on the day as a stretch goal, like Level 2 observability ([`../operations/observability.md`](../operations/observability.md)).

- **Where:** `AppleCircuitBreakers` holds one Resilience4j breaker per API (`search`, `lookup`). The gateway adapters call it around the retried client call, so one logical call (after retries) counts once. It is never on the `@Retryable` method.
- **Counted failures:** connection failures, read timeouts and 5xx. Rate limits (Apple 429, short-circuit, budget), contract errors and rejected storefronts don't count.
- **Settings** (`appstore.apple.circuit.*`): count-based window of 20 calls, opens at 50 % failures after at least 10 calls, open for 30 s, then 3 test calls ([`../operations/configuration.md`](../operations/configuration.md)).
- **While open:** `UpstreamCircuitOpenException` → 503 `upstream-unavailable` with `Retry-After` = the open duration; Apple isn't called; metric outcome `circuit_open` (DEBUG); each state transition is logged once at WARN.
- **Order in the Search adapter:** 429 short-circuit → circuit breaker → client (retry → budget permit → HTTP).
- **Tests:** `AppleCircuitBreakersTest` (opens, counted and ignored failures, separate breakers, recovery, metrics) and `SearchGatewayAdapterTest` (outcome, no short-circuit).
