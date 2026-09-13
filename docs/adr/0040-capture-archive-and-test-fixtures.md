# ADR-0040: Capture archive vs. test fixtures

- **Status:** Superseded by [ADR-0041](0041-move-captures-into-wiremock-and-remove-stubs.md) (`stubs/` is moved into WireMock test resources on the day and then deleted)
- **Date:** 2026-09-13 (prep)

- **Context:** "Stubs" means request mappings in WireMock, but `stubs/` holds response bodies. Copying files from `stubs/` into test resources would create two drifting copies. Two files were Apple documentation samples named with an invented HTTP 200.
- **Options:**
  1. Keep `stubs/` as a read-only archive and let tests own WireMock files.
  2. Move everything into test resources.
  3. Let tests read `stubs/` directly.
- **Decision:** Option 1.
  - **`stubs/`** is a dated, read-only evidence archive, never read by tests. Documentation samples are named `doc-sample-*.json`.
  - **Test data:** the backend's single source of truth is `backend/src/test/resources/wiremock/{mappings,__files}/apple/{search,lookup}/`, derived from the archive when tests are written. The 429 capture becomes a WireMock mapping with its headers.
- **Consequences:**
  - Clear ownership: evidence vs. test data.
  - The archive can be refreshed or removed without breaking tests.
  - When an archive file is refreshed, the corresponding WireMock file must be updated in the same change (documented in `docs/development/testing.md`).
