# ADR-0045: The Search budget is configuration, and the outbound limiter is core

- **Status:** Accepted; amends [ADR-0007](0007-part-3-direction-resilience-caching.md), [ADR-0017](0017-add-an-angular-web-client-time-boxed.md) and [ADR-0025](0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md)
- **Date:** 2026-09-14 (Discovery Day, kickoff)

- **Context:**
  - **The team's answer:** we build for Apple's current Search limit (about 20 calls per minute per source IP), and we consider that more requests can be bought.
  - **What we don't know:** [ADR-0025](0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md) found no public paid tier, so how extra capacity would be bought, and whether it changes the endpoint or adds authentication, is unknown.
  - **The gap:** the caches and single-flight deduplication remove repeated calls, and the 429 short-circuit reacts after Apple has rejected a call. Nothing keeps unique searches within a budget. With a bought budget, exceeding it can cost money or break an agreement.
  - **The earlier trim:** [ADR-0017](0017-add-an-angular-web-client-time-boxed.md) made the outbound limiter a stretch goal to make room for the web client.
- **Options:**
  1. Keep the limiter as a stretch goal; the budget is protected only by the caches and the 429 short-circuit.
  2. Treat the budget as configuration, make the outbound limiter core, and build caching and resilience right after the details endpoint.
  3. Also prepare for a paid tier now (authentication, endpoints).
- **Decision:** Option 2.
  - **Budget:** `appstore.apple.search.budget` (default 20 calls per minute) is plain configuration. No code or test assumes the value 20. Raising a bought budget is a configuration change.
  - **Outbound limiter on Search:** core, built in the caching and resilience block. Every HTTP attempt, retries included, takes a permit. When none is left, the client gets 503 `upstream-unavailable` with `Retry-After`, without calling Apple. The implementation (Resilience4j `RateLimiter` or a small token bucket) is approved at the start of that block.
  - **Order:** the caching and resilience block (caches, single-flight deduplication, retry, limiter) directly follows the details block, before errors, Docker and auth ([`../challenge/plan.md`](../challenge/plan.md)).
  - **Not built:** anything specific to a paid tier, until its access mechanism is known.
  - **Unchanged:** no IP rotation ([ADR-0025](0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md)).
- **Consequences:**
  - Part 3 regains the limiter that ADR-0017 had trimmed.
  - Clients can get 503 during bursts that Apple itself would still have accepted (bursts of 36–43 calls passed in prep). This is deliberate: we stay within the published or bought budget.
  - The limiter is per instance. Several instances sharing one egress IP or one bought budget must split it, or use a central budget (known limit in `docs/architecture/caching-resilience.md`).
