# ADR-0012: Upstream status mapping

- **Status:** Accepted; amended by [ADR-0030](0030-details-cache-and-bounded-retry.md) (bounded retry) and [ADR-0031](0031-rate-limit-short-circuit.md) (429 short-circuit)
- **Date:** 2026-09-13 (prep)

- **Context:** Clients need statuses that tell them what to do, and nothing about Apple's internals.
- **Decision:**

  | Upstream failure | Our status |
  |---|---|
  | Apple 429 / our own limiter | 503 + `Retry-After` |
  | Read timeout | 504 (not retried) |
  | Connect failure, or Apple 5xx after retries | 502 |
  | Other Apple 4xx, malformed payload | 502 + ERROR log (our integration bug) |
  | Empty MZ result | 404 "not found or not available in storefront" |
- **Consequences:** Read timeouts are not retried, so a slow Apple costs ~7 s per call (2 s connect + 5 s read) instead of ~21 s with retries. A slow 5xx is still retried and can take up to ≈ 21.6 s (docs/architecture/caching-resilience.md). The full mapping lives in docs/architecture/error-handling.md.
