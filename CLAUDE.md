# CLAUDE.md

This is the working agreement for AI assistants in this repository. It stays short on purpose. The knowledge itself lives in `docs/`, and each fact has exactly one home (see the table below).

## Current phase: Relution Discovery Day challenge

Before doing any work, read [`docs/challenge/plan.md`](docs/challenge/plan.md). Its working agreement (commit approval, question format, schedule) applies **in addition to** this file until `docs/challenge/` is archived.

## The project

A Spring Boot 4 service (Java 25) with two endpoints: it searches apps via the Apple iTunes Search API and returns app details via the Apple MZStorePlatform lookup API (which Apple marks as Legacy). An Angular 22 client demonstrates both. The service turns Apple payloads into small, stable DTOs and translates upstream failures into RFC 9457 problem responses. It also provides caching with request deduplication, bounded retry, a 429 short-circuit, an outbound limiter for the Search budget, JWT auth, OpenAPI, structured logs and metrics.

## Where things live

| Topic | Single source of truth |
|---|---|
| Architecture, packages, dependency rules, stack | [`docs/architecture/overview.md`](docs/architecture/overview.md) |
| Error handling, storefront policy, problem types | [`docs/architecture/error-handling.md`](docs/architecture/error-handling.md) |
| Caching, timeouts, retry, 429 short-circuit | [`docs/architecture/caching-resilience.md`](docs/architecture/caching-resilience.md) |
| Security and privacy | [`docs/architecture/security.md`](docs/architecture/security.md) |
| Frontend | [`docs/architecture/frontend.md`](docs/architecture/frontend.md) |
| Public API contract | [`docs/api/README.md`](docs/api/README.md) (OpenAPI at `/v3/api-docs` once code exists) |
| How the Apple APIs behave (evidence) | [`docs/integrations/apple-api-behavior.md`](docs/integrations/apple-api-behavior.md) |
| Mapping Apple JSON to the domain | [`docs/integrations/apple-mapping.md`](docs/integrations/apple-mapping.md) |
| Configuration and environment variables | [`docs/operations/configuration.md`](docs/operations/configuration.md) |
| Docker, compose, ports, health checks | [`docs/operations/deployment.md`](docs/operations/deployment.md) |
| Logs, metrics, tracing, alerts | [`docs/operations/observability.md`](docs/operations/observability.md) |
| Operational procedures | [`docs/operations/runbook.md`](docs/operations/runbook.md) |
| Test strategy and fixtures | [`docs/development/testing.md`](docs/development/testing.md) |
| CI, formatting, dependency updates | [`docs/development/tooling.md`](docs/development/tooling.md) |
| Decisions (ADRs) and open questions | [`docs/adr/README.md`](docs/adr/README.md) |
| Terms (storefront, adamId, kind, …) | [`docs/glossary.md`](docs/glossary.md) |
| Captured Apple responses (WireMock test data) | [`backend/src/test/resources/wiremock/README.md`](backend/src/test/resources/wiremock/README.md) |
| Contribution workflow | [`CONTRIBUTING.md`](CONTRIBUTING.md) |

## Rules

1. **Verify before claiming.** Run the tests or the app before saying something works. Anything marked *verify* in the docs is unconfirmed: check it against the official source, don't guess.
2. **Confirm concrete actions before creating code or files.** Picking an option in a planning question is not an instruction to run generators, write code or install tools. Ask first.
3. **No new dependencies** beyond the stack in `docs/architecture/overview.md` and the accepted ADRs, unless the maintainer approves.
4. **Respect the architecture rules** ([ADR-0028](docs/adr/0028-package-boundaries-and-ports.md)). The ArchUnit test enforces them. Don't weaken the test to make code pass.
5. **Test-first** for mappers, Apple clients and the storefront policy. Backend test data lives in `backend/src/test/resources/wiremock/`, the single copy of the Apple captures ([ADR-0041](docs/adr/0041-move-captures-into-wiremock-and-remove-stubs.md)).
6. **Never leak or log sensitive data.** The binding list is in [`docs/architecture/security.md`](docs/architecture/security.md#logging-and-privacy). In short: no Apple bodies or stack traces in responses; never log tokens, credentials or search terms; never forward Apple's `itvt` cookie.
7. **Decisions go into ADRs.**
   - Every non-obvious choice gets a new ADR (see `docs/adr/README.md`).
   - Never rewrite an accepted ADR. Supersede or amend it instead.
   - Never implement an ADR whose status is **Open**.
8. **Docs move with the code.** Update the one doc that owns a fact in the same commit as the code. Link to it from elsewhere; don't copy it. Use American English, kebab-case file names under `docs/`, and roles rather than personal names.
9. **Commits and pull requests.**
   - Conventional Commits, one logical change per commit, **at most 10 changed files**. Exceptions (generated output, pure moves, lockfiles, formatting) are stated in the commit body.
   - Run `./gradlew spotlessApply` (backend) before committing.
   - Commit only within a commit list the maintainer approved (per block; during the challenge see `docs/challenge/plan.md`). Anything outside it needs a new approval.
   - One pull request per block, rebase-merged. Only independent tracks run in parallel worktrees ([`CONTRIBUTING.md`](CONTRIBUTING.md#branches-and-pull-requests), [ADR-0042](docs/adr/0042-commit-size-and-parallel-pull-requests.md)).
10. **Prefer current LTS and stable versions** (Java 25, Node 24). Pin exact versions and let Dependabot update them.

## Commands

```bash
(cd backend && ./gradlew check)                      # format check, unit, WireMock, web, ArchUnit tests
(cd backend && ./gradlew spotlessApply)              # format Java
(cd backend && ./gradlew liveTest)                   # real Apple calls (drift check), not part of check
nvm use && (cd frontend && npm ci && npm test -- --watch=false && npm run build)
cp .env.example .env && docker compose up --build    # API :8080, management :8081 (127.0.0.1); web :4200 arrives with the frontend PR
```
