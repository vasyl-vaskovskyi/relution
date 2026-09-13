# ADR-0007: Part 3 direction: resilience + caching

- **Status:** Accepted; scope amended by [ADR-0017](0017-add-an-angular-web-client-time-boxed.md), [ADR-0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md), [ADR-0030](0030-details-cache-and-bounded-retry.md) (retry only on connection failures), [ADR-0031](0031-rate-limit-short-circuit.md) and [ADR-0035](0035-continuous-integration.md)
- **Date:** 2026-09-13 (prep)

- **Context:** Both upstreams are outside our control. The Search API allows ~20 calls/min **per source IP**, and our server is that single IP for all of its clients.
- **Options:** resilience + caching; books as a second media type; observability; CI/DX; i18n.
- **Decision:** Resilience + caching:
  - caches, including a 60 s negative cache and the storefront verdict cache,
  - retries on connection errors and 5xx only,
  - an outbound rate limiter,
  - metrics.

  Timeouts and upstream error translation (429 → 503, etc.) count as Part 2 requirements, not Part 3.
- **Consequences:** The cache and the limiter are both in-memory and per instance. Running several instances would need a shared store.
- **Amendment (prep):**
  - ADR-0017 trimmed Part 3 to caches plus retry; the outbound rate limiter became a stretch goal.
  - ADR-0027 made the metrics core (Level 1 observability).
  - The current scope is in docs/architecture/caching-resilience.md.
