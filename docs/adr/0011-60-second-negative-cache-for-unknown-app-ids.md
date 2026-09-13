# ADR-0011: 60-second negative cache for unknown app ids

- **Status:** Superseded by [ADR-0030](0030-details-cache-and-bounded-retry.md) (the 60 s negative-cache intent is kept, the mechanism changed)
- **Date:** 2026-09-13 (prep)

- **Context:** MZ answers unknown ids with an empty result, which becomes our 404. Exceptions are never cached, so repeated lookups of unknown ids would always hit Apple.
- **Options:** a short negative cache; accept and document.
- **Decision:** A separate `appNotFound` cache with a 60 s TTL. `AppDetailsService` uses the Cache API directly, because annotations can't give found and not-found results different TTLs.
- **Consequences:**
  - A newly published app can take up to 60 s to become visible.
  - Adds a small amount of explicit cache code, covered by a test.
