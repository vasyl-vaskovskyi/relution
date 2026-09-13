# ADR-0041: Move the captures into WireMock test resources and remove `stubs/`

- **Status:** Accepted; supersedes [ADR-0040](0040-capture-archive-and-test-fixtures.md)
- **Date:** 2026-09-14 (prep)

- **Context:**
  - [ADR-0040](0040-capture-archive-and-test-fixtures.md) kept `stubs/` as a permanent read-only archive, next to separate WireMock copies owned by the tests.
  - Once the tests exist, every payload would exist twice, and the runbook would require refreshing both copies in the same change. That's maintenance work that exists only because of the duplication.
  - The captures are still needed as input for the day. Re-capturing then costs time, depends on Apple's availability and rate limit, and hand-written fixtures reflect our assumptions rather than Apple's behavior.
- **Options:**
  1. Keep `stubs/` permanently as an archive (ADR-0040).
  2. Keep it until the day, then move the files into the WireMock test resources and delete `stubs/`.
  3. Delete it now and re-capture on the day.
- **Decision:** Option 2.
  - **When:** the Search block starts with one commit, `test: move Apple captures into WireMock test resources`.
  - **Where:** every file moves to `backend/src/test/resources/wiremock/`:
    - response bodies to `__files/apple/{search,lookup}/`, keeping their names (including `doc-sample-*`);
    - the 429 capture becomes a mapping in `mappings/apple/search/` with its status and headers.
  - **Metadata:** the capture metadata (request URLs, capture date, trimming rules, naming convention) moves from `stubs/README.md` to `backend/src/test/resources/wiremock/README.md`.
  - **Removal:** `stubs/` is deleted in the same commit.
  - **References:** `docs/integrations/apple-api-behavior.md` links its "observed" claims to the WireMock files.
- **Consequences:**
  - A single copy of the test data, owned by the tests, with no sync rule.
  - The evidence stays in the repository, next to the tests that depend on it.
  - Until the day, `stubs/` remains the source.
