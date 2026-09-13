# ADR-0022: Scaffold the Angular project on the day; prep holds docs only

- **Status:** Accepted; amended by [ADR-0040](0040-capture-archive-and-test-fixtures.md) and [ADR-0041](0041-move-captures-into-wiremock-and-remove-stubs.md) (`stubs/` moves into WireMock test resources on the day, then is deleted)
- **Date:** 2026-09-13 (prep)

- **Context:** A scaffold (`ng new` + `ng add @angular/material` + `proxy.conf.json`) was generated during prep. The maintainer had not asked for anything to be implemented yet.
- **Options:**
  - keep the scaffold as a setup commit;
  - remove it and scaffold on the day.
- **Decision:** Remove it. Before the day the repo holds only documentation plus two config files, `.gitignore` (protects `.env`) and `.nvmrc` (pins Node 24), and `stubs/` (test data, ADR-0024). The frontend is scaffolded at the start of the frontend block. `scripts/smoke.sh` is written in the OpenAPI block.
- **Consequences:**
  - Tooling was still verified in prep: nvm 0.40.7, Node 24.21.0, Angular CLI 22.1.8. The trial scaffold built, and its Vitest tests passed.
  - The trial also showed that `ng new` generates `.editorconfig` and `.prettierrc`.
  - The frontend block now includes about 10 minutes of scaffolding. The budgets are for planning only (docs/challenge/plan.md).
  - See challenge AI log #6.
