# ADR-0030: Native async caches, one lookup-result cache, bounded retry

- **Status:** Accepted; supersedes [ADR-0011](0011-60-second-negative-cache-for-unknown-app-ids.md); amends [ADR-0012](0012-upstream-status-mapping.md)
- **Date:** 2026-09-13 (prep)

- **Context:**
  - **Blocking:** a synchronous Caffeine load runs inside a `ConcurrentHashMap` computation, which can block other keys in the same hash bin (Caffeine FAQ). Spring's `CaffeineCache.get(key, Callable)` blocks the same way, and `CaffeineCacheManager.setAsyncCacheMode` does not apply to caches registered with `registerCustomCache`.
  - **Not-found bursts:** throwing a not-found exception from the loader stores nothing, so concurrent lookups of an unknown id all reach Apple.
  - **Latency:** retrying slow 5xx responses allowed worst cases of about 21.6 s.
- **Options:**
  1. Spring cache annotations with separate found/not-found caches.
  2. Native Caffeine `AsyncCache` with per-entry expiry.
  3. No caching of not-found results.
- **Decision:**
  - **Caches:** native Caffeine `AsyncCache`s built with `buildAsync()` and `recordStats()`, used directly by the `catalog` services (no Spring cache abstraction):
    - `app-search`: 10 min
    - `app-details`: holds a sealed `LookupResult` (`Found` for 15 min, `NotFound` for 60 s) via `Caffeine.expireAfter(Expiry)`
    - `storefront-verdict`: 24 h
  - **Loaders** run on an explicit virtual-thread executor passed to `Caffeine.executor(...)`. It is wrapped with Micrometer context propagation, so the MDC reaches the loader thread (*verify* on the day). `spring.threads.virtual.enabled=true` covers request threads only.
  - **Failures:** futures that fail are removed automatically, so upstream failures are never cached.
  - **Cache keys:** `l` is normalized to lower case with `-`.
  - **Retry:** `@Retryable` retries **only** `UpstreamConnectException` (retries and timeout from `appstore.apple.retry.*` via `maxRetriesString`/`timeoutString`; delay 200 ms, multiplier 2, jitter 100 ms; enabled with `@EnableResilientMethods`). Apple 5xx, read timeouts, 4xx, 429 and malformed payloads are not retried.
  - **Latency:** a single failing call takes at most about 7 s (2 s connect + 5 s read). The theoretical worst case is about 12 s (two connection failures, then a read timeout), because the 8 s retry `timeout` stops new attempts but doesn't abort a running one (*verify*).
- **Consequences:**
  - One lookup path, and deduplication for unknown ids too.
  - No Spring `@Cacheable` means cache metrics are bound explicitly with Micrometer's `CaffeineCacheMetrics` (*verify* `AsyncCache` support on the day).
  - Apple 5xx surfaces immediately as 502 instead of being retried.
  - `spring-boot-starter-cache` is not needed.
