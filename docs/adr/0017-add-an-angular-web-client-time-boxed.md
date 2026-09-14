# ADR-0017: Add an Angular web client, time-boxed

- **Status:** Accepted; amended by [ADR-0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md) [ADR-0031](0031-rate-limit-short-circuit.md) (429 short-circuit) and [ADR-0045](0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md) (outbound limiter is core again)
- **Date:** 2026-09-13 (prep)

- **Context:** The maintainer wants a frontend that demonstrates the required functionality and prints debug information to the browser console. For a backend position the client is optional, and the task says it "must not come at the expense of the server part". The task prescribes Angular with Angular Material for web clients.
- **Options:**
  - a time-boxed client block, with Part 3 trimmed;
  - a client only as a stretch goal;
  - the client replaces Part 3.
- **Decision:** Angular 22 + Angular Material in `frontend/`, in a 1:15 block **after the backend is complete** (after Part 3 and OpenAPI), so the client cannot eat into server time.
  - Part 3 is trimmed to the caches (including the negative cache and the storefront verdict cache) plus retry.
  - The outbound rate limiter and metrics become stretch goals. **Amendment:** metrics became core again with ADR-0027 (Level 1 observability).
- **Consequences:**
  - The server part stays complete. Part 3 goes less deep, which must be explained in the presentation.
  - The schedule is in docs/challenge/plan.md.
  - Without the rate limiter, the per-IP Search budget is protected only by the cache. This goes into the honest assessment.
