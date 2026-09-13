# ADR-0021: Node 24 LTS via nvm

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** Angular 22 (checked with 22.1.6 packages and CLI 22.1.8) requires Node `^22.22.3 || ^24.15.0 || >=26`, and the local Node v25.8.1 is not supported.
- **Options:** Node 24 LTS; Node 26 (Current, not yet LTS); Docker-only builds.
- **Decision:** Node 24 LTS, pinned in `.nvmrc`. The Docker build uses the `node:24-alpine` image.
- **Consequences:** The same reasoning as Java 25 (ADR-0002): current LTS, no day-one tech debt. Colleagues run `nvm use`.
