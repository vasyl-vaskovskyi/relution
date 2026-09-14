# Tooling

Versions marked *verify* were checked on 2026-09-13. Confirm them when the files are created, and pin exact versions.

## Continuous integration ([ADR-0035](../adr/0035-continuous-integration.md))

**`.github/workflows/ci.yml`** runs on pushes to `main` and on every pull request. Actions are pinned to exact release tags, and Dependabot updates them:

| Job | Steps |
|---|---|
| `backend` | `actions/setup-java` (v6, Temurin 25) → `gradle/actions/setup-gradle` (v6) → `./gradlew check` |
| `frontend` | `actions/setup-node` (v7, `node-version-file: .nvmrc`, npm cache keyed on `frontend/package-lock.json`) → `npm ci` → `npm test -- --watch=false` → `npm run build` |
| `images` | `docker build backend` → `docker build frontend` (no push) |

**`.github/workflows/apple-drift.yml`** runs nightly and on demand: `./gradlew liveTest` ([ADR-0037](../adr/0037-legacy-api-drift-detection.md)). It never blocks pull requests.

## Formatting ([ADR-0036](../adr/0036-formatting-version-catalog-and-updates.md))

| Area | Tool |
|---|---|
| Java | Spotless Gradle plugin `com.diffplug.spotless` (8.10.x, *verify*) with `palantirJavaFormat()` (2.98.x, *verify*; Java 25 support needs ≥ 2.71.0). Applied once in the bootstrap commit; `spotlessCheck` runs as part of `check` |
| Gradle Kotlin DSL | Spotless `kotlinGradle` with ktlint, only if it needs no extra approval |
| Frontend | The generated `.editorconfig` and `.prettierrc`. A Prettier check in CI needs approval if it adds a dev dependency |

## Dependencies

- **Version catalog:** `backend/gradle/libs.versions.toml` holds every backend library and plugin version.
- **Gradle wrapper:** pinned `distributionSha256Sum` in `backend/gradle/wrapper/gradle-wrapper.properties`.
- **Dependabot** (`.github/dependabot.yml`), weekly, with minor and patch updates grouped. Add each ecosystem once its directory exists (kickoff: `gradle`, `github-actions`; Docker block: `docker` for `/backend`, `docker-compose`; frontend block: `npm`, `docker` for `/frontend`). During the Discovery Day every entry sets `open-pull-requests-limit: 0`, so update PRs don't compete with the day's reviews; raise the limit (e.g. to 5) afterwards:

| `package-ecosystem` | `directory` |
|---|---|
| `gradle` | `/backend` |
| `npm` | `/frontend` |
| `docker` | `/backend`, `/frontend` |
| `docker-compose` | `/` |
| `github-actions` | `/` |

## Libraries added by the ADRs (ask before adding anything else)

| Library | Purpose | ADR |
|---|---|---|
| Spring Boot starters: web MVC, validation, actuator, RestClient (kickoff); security, OAuth2 resource server (auth block) | Web, validation, management, HTTP client, JWT | [0003](../adr/0003-spring-boot-4-1-with-restclient.md), [0004](../adr/0004-self-issued-hs256-jwt-no-fallback.md) |
| `com.github.ben-manes.caffeine:caffeine` (Boot-managed) | Async caches | [0030](../adr/0030-details-cache-and-bounded-retry.md) |
| `io.micrometer:context-propagation` (Boot-managed) | MDC propagation to cache loader threads (not a transitive dependency, verified in the kickoff spike) | [0030](../adr/0030-details-cache-and-bounded-retry.md) |
| `io.github.resilience4j:resilience4j-circuitbreaker` and `resilience4j-micrometer` 2.4.x | Circuit breaker per Apple API and its metrics (used programmatically, no Boot starter) | [0047](../adr/0047-circuit-breaker-per-apple-api.md) |
| `io.micrometer:micrometer-registry-prometheus` (Boot-managed) | Prometheus endpoint | [0032](../adr/0032-management-port-and-probes.md) |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.1.x | OpenAPI and Swagger UI | [0001](../adr/0001-server-only-scope-with-spring-boot.md) |
| `org.wiremock.integrations:wiremock-spring-boot` 4.2.x (test) | Apple HTTP tests | [0003](../adr/0003-spring-boot-4-1-with-restclient.md) |
| `com.tngtech.archunit:archunit-junit6` 1.5.x (test) | Architecture rules | [0028](../adr/0028-package-boundaries-and-ports.md) |
| `org.springframework.boot:spring-boot-starter-opentelemetry` (stretch goal) | OTLP export | [0027](../adr/0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md) |

## GitHub repository

See [ADR-0042](../adr/0042-commit-size-and-parallel-pull-requests.md).

- **Merge settings:** allow **Rebase and merge** only; disable squash merging and merge commits. Delete branches after merge.
- **Branch protection on `main`:** require a pull request, one approval and the CI checks `backend`, `frontend` and `images` (once they exist). Don't require branches to be up to date: with parallel tracks, every merge would force a rebase and CI rerun on every open PR. The rebase before merging ([`CONTRIBUTING.md`](../../CONTRIBUTING.md#branches-and-pull-requests)) is enough. No force pushes.
- **CLI:** `gh` authenticated (`gh auth status`) for creating and reviewing PRs from the terminal.

## Pull request template

`.github/pull_request_template.md` has a checklist: tests added or updated, docs updated in the owning file, ADR added for non-obvious decisions, no secrets, `./gradlew check` and the frontend tests green.

## Local toolchain

- **Java:** a Gradle toolchain downloads Java 25; any JDK can run Gradle. The download needs the `org.gradle.toolchains.foojay-resolver-convention` plugin in `settings.gradle.kts`. Initializr doesn't add it, and without it a machine with no local JDK 25 fails (found in the kickoff spike).
- **Node:** 24 LTS via nvm (`nvm install && nvm use` reads `.nvmrc`).
- **Docker:** Docker with Compose v2.
