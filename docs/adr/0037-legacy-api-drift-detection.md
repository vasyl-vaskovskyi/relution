# ADR-0037: Detect drift of the Legacy Apple APIs

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** Tests use frozen captures. If Apple renames a field in the unversioned Legacy lookup API, mappers silently return `null` and the service still answers 200, so no error metric fires.
- **Decision:**
  - **Nightly live test:** a separate Gradle source set `src/liveTest/java` (JUnit tag `live`, task `liveTest`, excluded from `check`) makes at most 3 real Apple calls (one search, one iOS lookup, one Mac lookup). It asserts the same required-key list as the runtime counter, plus known values for the pinned apps (e.g. Pages has `offers[0].version.display`). The scheduled workflow `.github/workflows/apple-drift.yml` runs it nightly and on demand.
  - **Runtime counter:** `appstore.apple.mapping.missing_field{api, field}` counts fields that are present in every capture but missing in a live response (`trackId`, `trackName`; `name`, `kind`, `artwork` for app kinds; not `offers`, which can legitimately be missing). Field names are a fixed, low-cardinality set.
- **Consequences:**
  - Drift is detected within a day, or immediately in production metrics, instead of by users.
  - The nightly job depends on Apple's availability and rate limit, and never blocks pull requests.
