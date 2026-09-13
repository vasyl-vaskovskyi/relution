# ADR-0039: Documentation structure, ADR format and conventions

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:**
  - The AI working brief (`CLAUDE.md`, about 760 lines) mixed durable engineering knowledge with challenge-day material (schedule, presentation, personal approval rules). Every future AI session would load all of it.
  - One growing `DECISIONS.md` had no statuses, so superseded decisions still read as current.
  - Facts were duplicated across files.
  - File names mixed three styles, and the prose mixed British and American spelling.
- **Decision:**
  - **Durable docs:**
    - `README.md` and `CONTRIBUTING.md`;
    - `docs/architecture/`, `docs/api/`, `docs/integrations/`, `docs/operations/`, `docs/development/`;
    - `docs/glossary.md`.
  - **Decisions:** one ADR per file, `docs/adr/NNNN-kebab-title.md`, MADR-lite: Status (Proposed, Accepted, Superseded by, Open), Date, Context, Options, Decision, Consequences. Decisions are never rewritten; a new ADR supersedes or amends. `docs/adr/README.md` is the index.
  - **Challenge material:** `docs/challenge/` (brief, plan, presentation, AI log), frozen after the Discovery Day and removable on merge.
  - **`CLAUDE.md`:** a short AI working agreement with links only.
  - **Conventions:**
    - Lowercase kebab-case file names under `docs/`; uppercase only for root files that tools expect.
    - American English.
    - Roles ("the maintainer") instead of personal names in durable docs.
    - Each fact has one home, and other documents link to it.
- **Consequences:** New developers find current decisions and knowledge quickly. Personal and day-specific content can be deleted without touching durable docs. This superseded the single `DECISIONS.md`, `AI_LOG.md` and `APPLE-API-BEHAVIOUR.md` files.
