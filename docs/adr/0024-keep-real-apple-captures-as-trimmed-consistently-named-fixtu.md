# ADR-0024: Keep real Apple captures as trimmed, consistently named fixtures

- **Status:** Accepted; amended by [ADR-0040](0040-capture-archive-and-test-fixtures.md) and [ADR-0041](0041-move-captures-into-wiremock-and-remove-stubs.md) (captures move into WireMock test resources; `stubs/` removed on the day)
- **Date:** 2026-09-13 (prep)

- **Context:** Mapper and client tests need Apple payloads. Hand-written JSON tends to reflect our assumptions rather than what Apple actually sends (see challenge AI log #2 and #4). The raw captures were 228 KB, mostly Apple marketing text and screenshots.
- **Options:**
  - keep the raw captures;
  - keep them, trimmed;
  - hand-write fixtures on the day;
  - re-capture them on the day.
- **Decision:** Keep them, trimmed to the fields the service maps plus fields that show documented quirks. Absent keys and explicit `null`s are preserved, long texts are shortened, and the total is 68 KB.
  - Naming: `<api>/<http-status>-<scenario>[-<variant>][-<cc>].<ext>`.
  - Added one capture: the silent language fallback (`l=fr` → `de-de`).
- **Consequences:**
  - Fixtures are committed as labeled prep data. On the day, tests copy the files they need into `backend/src/test/resources`.
  - Refreshing them follows `stubs/README.md`.
  - **Amendment (prep):** moved from `docs/samples/` to the top-level `stubs/` folder at the maintainer's request. They are test data, not documentation.
