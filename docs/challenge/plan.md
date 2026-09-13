# Discovery Day plan

The working agreement and plan for the day. The durable engineering rules are in [`../../CLAUDE.md`](../../CLAUDE.md) and [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md). This file adds the challenge-specific rules.

## Working agreement (Vasyl ↔ AI assistant)

1. **Vasyl owns every commit.** Per step:
   1. State the step in 1–3 sentences.
   2. Implement it.
   3. Run the tests.
   4. Show a diff summary.
   5. **Wait for explicit commit approval.**
2. **Questions** are always multiple-choice (AskUserQuestion), with the recommended option first. Check the arithmetic of any schedule or budget option before offering it (see AI log #5).
3. **Quality over the clock.** The budgets below are for planning only. Never rush, skip tests or drop edge cases because a block overran. Vasyl decides if anything is reprioritized.
4. **Confirm concrete actions** before running generators, creating code or installing anything (see AI log #6).
5. **Log as you go:**
   - rejected, corrected or reworked AI suggestions go in [`ai-log.md`](ai-log.md). Vasyl needs **at least one code-level example from the day**;
   - decisions go in new ADRs.

## Prep status (2026-09-13)

- **Tooling verified:** nvm 0.40.7, Node 24.21.0, Angular CLI 22.1.8. A trial `ng new` built and passed its tests, and was then removed.
- **Repo contents:** docs, `.gitignore`, `.nvmrc` and `stubs/`. No code.
- **Open question:** [ADR-0026](../adr/0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md) (browser-direct search). Do not implement it until it's decided.

## Kickoff checklist

1. Confirm the brief and scope with the team.
2. **Initializr:** start.spring.io → Gradle Kotlin, Java, Spring Boot 4.1.x, Jar, Java 25.
   - Group `com.example`, artifact `appstore`, package `com.example.appstore` (Initializr derives it from group + artifact; check it).
   - Generate into `backend/`.
   - Dependencies: Spring Web, Validation, Actuator, and the RestClient starter (*verify* its label).
   - No Security yet (it comes in the auth block), and no Cache Abstraction, because we use Caffeine directly ([ADR-0030](../adr/0030-details-cache-and-bounded-retry.md)).
3. **Build setup:**
   - Move versions into `backend/gradle/libs.versions.toml`.
   - Add Caffeine, the Prometheus registry, springdoc 3.1.x, and for tests `wiremock-spring-boot` 4.2.x and `archunit-junit6` 1.5.x ([`../development/tooling.md`](../development/tooling.md)).
   - *Verify* every version on Maven Central and the Boot 4 split test starter names.
   - Add `springBoot { buildInfo() }`.
4. **Spotless** with palantir-java-format, applied to the generated code (bootstrap commit). Pin the wrapper `distributionSha256Sum`.
5. **`application.yml` skeleton:**
   - `appstore.*` properties records with validation (names from [`../operations/configuration.md`](../operations/configuration.md));
   - management port 8081, exposure list and probe paths;
   - percentiles histogram for `appstore.apple.requests`;
   - virtual threads.
6. **ArchUnit test** for the package rules ([`../architecture/overview.md`](../architecture/overview.md#dependency-rules-enforced-by-an-archunit-test)). It passes on the empty packages.
7. **`.github/`:** CI `backend` job, Dependabot (`gradle` and `github-actions` only for now), `pull_request_template.md`.
8. `./gradlew check` is green → commit `chore: bootstrap backend`.

## Schedule (relative hours; planning only)

| Start | Block | Budget | Done when |
|---|---|---|---|
| H+0:00 | **Kickoff and merge hygiene** (checklist above) | 0:45 | CI green on the bootstrap commit |
| H+0:45 | **Search:** domain records, gateways, `StorefrontPolicy`; `ItunesSearchClient` with timeouts and error translation; `SearchGatewayAdapter` with the 429 guard and one outcome metric and log line per logical call; first commit moves `stubs/` into `backend/src/test/resources/wiremock/` and deletes it ([ADR-0041](../adr/0041-move-captures-into-wiremock-and-remove-stubs.md)); mapper (test-first with those fixtures); service; controller; validation | 1:30 | `GET /api/v1/apps?term=pages&cc=de` works via curl |
| H+2:15 | **Details:** `LookupResult`; `MzLookupClient` and `LookupGatewayAdapter` (lookup outcome metrics); mapper (artwork shapes, nulls, kinds, ids, `platform` mapping, `BigDecimal`); storefront and language checks; controller | 1:30 | `GET /api/v1/apps/{id}?cc=de&l=de&platform=mac` works |
| H+3:45 | **Errors and Level 1 observability:** `ProblemDetails` factory and advice with `errors[]`, `ProblemType`, correlation-id filter with validation and precedence, ECS logging config, log-safety test | 0:40 | Error-matrix tests green |
| H+4:25 | **Docker:** hardened backend Dockerfile (Ubuntu-based JRE), compose (localhost binds, healthcheck), `.env.example`, CI `images` job, Dependabot `docker` (`/backend`) and `docker-compose` | 0:30 | Readiness UP in the container, search works |
| H+4:55 | **Auth:** security dependencies; token endpoint; JWT claims and validators; scope; constant-time check; catch-all `denyAll`, `/error`, management-port chain; `clientId` in MDC; fail-fast `ApplicationContextRunner` test; security tests | 1:10 | 401 without a token, 403 without the scope, 200 with a token, `/actuator/env` 401 on 8080 and 404 on 8081 |
| H+6:05 | **Caching and resilience:** async caches (`app-search`, `app-details` with `LookupResult` expiry, `storefront-verdict`) with an explicit virtual-thread executor and MDC propagation; cache metrics binding; `@EnableResilientMethods` and retry on connection failures with timeout; dedup, negative-cache and not-cached-failure tests | 1:15 | Resilience tests green |
| H+7:20 | **OpenAPI** annotations and bearer scheme; `application-prod.yml` disabling the API docs; README check; `scripts/smoke.sh` (checks below) | 0:20 | Swagger UI usable with a token, smoke checks pass |
| H+7:40 | **Operability:** `liveTest` source set (required-key list), nightly `apple-drift` workflow, `missing_field` counter, `ops/alerts.yml` | 0:40 | `./gradlew liveTest` passes locally |
| H+8:20 | **Frontend:** scaffold (below), login, search, details, platform toggle, error mapping by problem type, `DebugLogService`, `demo` build configuration, nginx image with CSP and query-free access log, 3 unit tests, CI `frontend` job, Dependabot `npm` and `docker` (`/frontend`), compose service | 1:30 | Full flow works at `localhost:4200` |
| H+9:50 | **Buffer:** lunch, AI log and ADR tidy-up, honest assessment, presentation dry run | 0:45 | — |

**Total: 10:35.** That's about 2.5 h more than a normal day, because of the large-product changes decided on 2026-09-13. The blocks are in priority order.

### If Vasyl asks for cuts

Cut in this order:
1. Frontend extras (platform toggle, served-language chip).
2. Operability block (drift test, alert rules).
3. Frontend unit tests beyond the interceptor.
4. The frontend as a whole.
5. Retry.

Never cut:
- error-matrix tests;
- auth;
- OpenAPI;
- Docker;
- CI;
- the 429 short-circuit;
- the management port;
- the ArchUnit rules.

### Stretch goals (only after all blocks, with Vasyl's go-ahead)

1. Level 2 observability ([`../operations/observability.md`](../operations/observability.md#level-2-opentelemetry--grafana-lgtm-stretch-goal)).
2. Outbound rate limiter.
3. Circuit breaker.

## Frontend scaffold (start of the frontend block)

1. From the repo root: `nvm use && npx @angular/cli@22 new frontend --routing --style=scss --ssr=false --zoneless --test-runner=vitest --ai-config=none --skip-git --package-manager=npm --interactive=false`
2. `cd frontend && npx ng add @angular/material --skip-confirmation --interactive=false`
3. `ng generate environments`, then add the `demo` build configuration ([`../architecture/frontend.md`](../architecture/frontend.md#debug-logging)).
4. Add `proxy.conf.json` (`/api` and `/auth` → `http://localhost:8080`) and set `proxyConfig` in the `serve` options.
5. Keep the generated `.editorconfig` and `.prettierrc`. Commit the untouched generator output separately (`chore(frontend): scaffold …`).

## `scripts/smoke.sh` checks

- **Environment:** bash with `set -euo pipefail`, `curl` and `jq`. Loads `.env`; `BASE_URL` defaults to `http://localhost:8080`, `MANAGEMENT_URL` to `http://localhost:8081`.
- **Output:** prints PASS or FAIL per check and exits non-zero on any failure.
- **Rate limit:** calls the real Apple APIs, so run it rarely.

| # | Request | Expect |
|---|---|---|
| 1 | `GET /readyz` | 200 |
| 2 | `POST /auth/token` with Basic credentials | 200, `accessToken` |
| 3 | Search without a token | 401 |
| 4 | `?term=pages&cc=de&limit=5` | 200, `count >= 1`, string ids, string `price.amount` |
| 5 | Search without `cc` | 400, `errors[]` contains `cc` |
| 6 | Search with `cc=cu` | 400, type `unsupported-storefront` |
| 7 | Details `361309726?cc=de&l=de` | 200, `kind == "IOS_APP"`, `storefront.cc == "de"`, version present |
| 8 | Details with `platform=mac` | 200, `storefront.platform == "mac"` |
| 9 | Details with `l=fr` | 200, `storefront.language == "de-de"` |
| 10 | Details for unknown id `1` | 404, type `app-not-found` |
| 11 | Details without `cc` | 400 |
| 12 | `GET /actuator/env` on 8080 without a token | 401, ProblemDetail body with no environment data |
| 13 | `GET $MANAGEMENT_URL/actuator/env` | 404 |
