# ADR-0031: Short-circuit Search calls after Apple returns 429

- **Status:** Accepted; amends [ADR-0012](0012-upstream-status-mapping.md) and [ADR-0007](0007-part-3-direction-resilience-caching.md)
- **Date:** 2026-09-13 (prep)

- **Context:** A 429 was mapped to 503, but every uncached request still called Apple during the `Retry-After` window. Apple's limit is per source IP, so this can extend the block for every user of the server.
- **Options:** keep calling Apple; remember the block and answer locally; full outbound rate limiter (stretch goal).
- **Decision:**
  - **Guard:** `integration.apple` keeps a process-wide `SearchRateLimitGuard` (`AtomicReference<Instant> blockedUntil`), set from `Retry-After`: default 30 s, capped at 5 min.
  - **While blocked:** Search requests fail fast with `UpstreamRateLimitedException` and no Apple call. That maps to 503 `upstream-unavailable` with the remaining seconds in `Retry-After`, and is counted with `outcome=short_circuited`.
  - **Scope:** only Search. The lookup API showed no limit.
  - **Priority:** this is core, not a stretch goal.
- **Consequences:**
  - Respects Apple's signal at almost no cost (about 20 lines plus one WireMock test).
  - The guard is per instance. Several instances behind one egress IP each learn the block on their first 429. This is accepted and documented.
