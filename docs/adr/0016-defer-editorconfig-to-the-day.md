# ADR-0016: Defer `.editorconfig` to the day

- **Status:** Superseded for the backend by [ADR-0036](0036-formatting-version-catalog-and-updates.md) (Spotless); the frontend keeps the generated `.editorconfig`
- **Date:** 2026-09-13 (prep)

- **Context:** A prep draft used spaces, while Initializr may generate tab-indented files (not verified). A mismatch would create whitespace noise in the bootstrap commit.
- **Decision:** No root `.editorconfig` before the day. At kickoff, inspect the indentation Initializr generates and add a minimal matching `backend/.editorconfig` (whitespace rules only).
- **Consequences:**
  - A 2-minute check at kickoff.
  - **Update (prep, trial Angular scaffold, later removed — see ADR-0022):** `ng new` generates `frontend/.editorconfig` (2 spaces, single quotes in TS) and `.prettierrc`. When the frontend is scaffolded on the day, keep both as generated. Only the backend needs a decision.
