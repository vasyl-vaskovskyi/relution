# ADR-0047: A circuit breaker per Apple API

- **Status:** Accepted; amends [ADR-0030](0030-details-cache-and-bounded-retry.md) (resilience scope)
- **Date:** 2026-09-14 (Discovery Day, stretch goal)

- **Context:**
  - Timeouts bound one call (about 7 s, 12 s in the worst case with retries). When Apple is down or very slow, every uncached request still waits that long, holds a request thread and adds load to a struggling upstream.
  - The circuit breaker was the last stretch goal in `docs/challenge/plan.md`, after Level 2 observability; the maintainer approved both on the day.
- **Options:**
  1. No breaker; rely on timeouts.
  2. Resilience4j `CircuitBreaker` (new dependencies `resilience4j-circuitbreaker` and `resilience4j-micrometer`).
  3. A hand-written breaker.
- **Decision:** Option 2, used programmatically (no Spring Boot starter, which isn't released for Boot 4).
  - **One breaker per API** (`search`, `lookup`), held by `AppleCircuitBreakers` and applied in the gateway adapters **around the retried client call**, never on the `@Retryable` method.
  - **Counted as failures:** `UpstreamConnectException`, `UpstreamReadTimeoutException`, `UpstreamServerErrorException`. Not counted: rate limits (Apple 429, short-circuit, budget), contract errors and rejected storefronts. Those don't mean Apple is unhealthy.
  - **Defaults** (`appstore.apple.circuit.*`): count-based window of 20 calls, opens at 50 % failures after at least 10 calls, stays open 30 s, then allows 3 test calls.
  - **While open:** new exception `UpstreamCircuitOpenException` (seventh `UpstreamException` kind) → 503 `upstream-unavailable` with `Retry-After` set to the open duration; metric outcome `circuit_open` at DEBUG; the state transition is logged once at WARN.
  - **Observability:** Resilience4j's Micrometer binding (`resilience4j_circuitbreaker_state` and related metrics) and the alert `AppleCircuitOpen`.
- **Consequences:**
  - During an Apple outage clients get a fast 503 instead of waiting for timeouts; cached answers keep working.
  - Per instance, like the caches and the budget.
  - A short open duration can still let a few slow test calls through; `Retry-After` is the configured open duration, not the exact remaining time.
