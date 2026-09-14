# Discovery Day plan

The working agreement and plan for the day. The durable engineering rules are in [`../../CLAUDE.md`](../../CLAUDE.md) and [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md). This file adds the challenge-specific rules.

## Working agreement (Vasyl ↔ AI assistant)

1. **Vasyl owns every commit; commit lists are pre-approved per block** (changed at H+1:10 on 2026-09-14 to keep the pace; the bootstrap block still had an explicit list):
   1. Implement each block's scope from the schedule below commit by commit, with at most 10 files per commit or a named exception. Run the tests before each commit, and commit only when they are green.
   2. **Ask Vasyl only about** new dependencies, ADR-level decisions and every merge into `main`.
   3. Keep status reports minimal.
   4. Open the block's PR with a summary. The PR review is the real review.
2. **Questions** are always multiple-choice (AskUserQuestion), with the recommended option first. Check the arithmetic of any schedule or budget option before offering it (see AI log #5).
3. **Quality over the clock.** The budgets below are for planning only. Never rush, skip tests or drop edge cases because a block overran. Vasyl decides if anything is reprioritized.
4. **Confirm concrete actions** before running generators, creating code or installing anything (see AI log #6).
5. **Log as you go:**
   - rejected, corrected or reworked AI suggestions go in [`ai-log.md`](ai-log.md). Vasyl needs **at least one code-level example from the day**;
   - decisions go in new ADRs.

## Branches, pull requests and parallel tracks

See [ADR-0042](../adr/0042-commit-size-and-parallel-pull-requests.md) and [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md#branches-and-pull-requests).

- **Commits:** at most 10 changed files each. Exceptions (generated, pure move, lockfile, formatting) are named in the commit body. The AI checks `git diff --cached --name-only | wc -l` before each commit.
- **Pull requests:** one per block on GitHub, rebase-merged after green CI and Vasyl's approval. At most two PRs wait for review at once; for the Discovery Day, Vasyl raised this to four so more tracks can run in parallel (the durable rule in ADR-0042 is unchanged).

| Track | Pull requests (in order) | Starts when | Notes |
|---|---|---|---|
| **Backend core** (sequential, built in this session) | `build/bootstrap` → `feat/search` → `feat/details` → `feat/caching-resilience` → `feat/errors-observability` → `feat/auth` → `docs/openapi-smoke` | Kickoff | Each PR is merged before the next branches off; they share domain records, gateways and the error advice |
| **Delivery** | `build/docker` (Dockerfile, compose, `.env.example`, CI `images` job, Dependabot `docker` and `docker-compose`) | `feat/search` merged | Needs a real endpoint to check in the container |
| **Frontend** (subagent in its own worktree) | `feat/frontend-scaffold` (generated exception commit, proxy, `demo` config, CI `frontend` job) → `feat/frontend-ui` | Scaffold: `build/bootstrap` merged. UI: `feat/auth` merged | The UI builds against `docs/api/README.md`; a full end-to-end check needs auth and details |
| **Operability** (subagent in its own worktree) | `ops/alert-rules` → `test/apple-drift` (`liveTest`, nightly workflow, `missing_field` counter) | Alert rules: `build/bootstrap` merged. Drift: `feat/details` merged | The drift test needs the mappers and clients |

- **Subagent tracks** follow the same working agreement: each presents its commit list for Vasyl's approval before writing code, and its PR goes through the same review.
- **Rebase conflicts:** the tracks touch the same few shared files (`ci.yml`, `dependabot.yml`, `docker-compose.yml`, `libs.versions.toml`, `README.md`, `configuration.md`). Keep those edits in small, separate commits, and rebase right before merging.
- **Priority:** the schedule below stays the priority order. Parallel tracks can start earlier than their row, but they never delay review of the backend core.

## Prep status (updated 2026-09-14)

- **Tooling verified:** nvm 0.40.7, Node 24.21.0, Angular CLI 22.1.8. A trial `ng new` built and passed its tests, and was then removed. Docker 29.7.2; `gh` authenticated with the `repo` scope.
- **Repository:** `vasyl-vaskovskyi/relution` on GitHub. `main` is pushed and is the default branch. Merge settings: **Rebase and merge** only, branches deleted after merge.
- **Prep commits:** eight documentation commits on `main`. Three of them exceed 10 files; they predate the rule and are kept as a documented exception (ADR-0042).
- **Still open:** branch protection on `main` with the required CI check, as soon as the `backend` job exists (kickoff step 9).
- **Browser-direct search** ([ADR-0026](../adr/0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md)): not for the Discovery Day; a scaling option for the presentation.
- **Search budget** ([ADR-0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md)): the team confirmed we build for today's limit and that more requests can be bought.

## Kickoff checklist

1. Confirm the brief and scope with the team. Check the GitHub setup (`gh auth status`, `main` pushed, rebase merge only).
2. **De-risking spike** (20–30 min, in a throwaway project outside the repository, nothing committed): run the checks in [De-risking spike](#de-risking-spike).
3. **Initializr:** start.spring.io → Gradle Kotlin, Java, Spring Boot **`4.1.1`**, Jar, Java 25.
   - Request the Boot version as `4.1.1`: the metadata id `4.1.1.RELEASE` makes the generator answer HTTP 500 (spike).
   - Group `com.example`, artifact `appstore`, package `com.example.appstore`.
   - Generate into `backend/`.
   - Dependencies: Spring Web, Validation, Actuator, **HTTP Client** (`spring-restclient`), Prometheus.
   - No Security yet (it comes in the auth block), and no Cache Abstraction, because we use Caffeine directly ([ADR-0030](../adr/0030-details-cache-and-bounded-retry.md)).
4. **Build setup:**
   - Add the `org.gradle.toolchains.foojay-resolver-convention` plugin (1.0.0) to `settings.gradle.kts`, so Gradle can download Java 25 ([`../development/tooling.md`](../development/tooling.md#local-toolchain)).
   - Move versions into `backend/gradle/libs.versions.toml`.
   - Add Caffeine, `io.micrometer:context-propagation`, springdoc 3.1.x, and for tests `wiremock-spring-boot` 4.2.x and `archunit-junit6` 1.5.x ([`../development/tooling.md`](../development/tooling.md)).
   - *Verify* every version on Maven Central. The Boot 4 test starters are `spring-boot-starter-{webmvc,restclient,validation,actuator}-test` (spike).
   - Add `springBoot { buildInfo() }`.
5. **Spotless** with palantir-java-format, applied to the generated code (Initializr emits tabs). Pin the wrapper `distributionSha256Sum` (Gradle 9.7.1).
6. **`application.yml` skeleton:**
   - `appstore.*` properties records with validation (names from [`../operations/configuration.md`](../operations/configuration.md));
   - management port 8081, exposure list and probe paths;
   - percentiles histogram for `appstore.apple.requests`;
   - virtual threads.
7. **ArchUnit test** for the package rules ([`../architecture/overview.md`](../architecture/overview.md#dependency-rules-enforced-by-an-archunit-test)). It passes on the empty packages.
8. **`.github/`:** CI `backend` job, Dependabot (`gradle` and `github-actions` only for now, `open-pull-requests-limit: 0` until after the day), `pull_request_template.md`.
9. `./gradlew check` is green → commit the untouched Initializr output (`Exception: generated`), then the setup commits (≤ 10 files each) → PR `build/bootstrap` → rebase-merge → branch protection on `main` with the required `backend` check. The parallel tracks can start after this merge.

## De-risking spike

Done at kickoff on 2026-09-14, in a throwaway Spring Boot 4.1.1 project outside the repository. Nothing from it was committed. The results are recorded in the owning docs and in [ADR-0044](../adr/0044-secure-the-management-port-as-a-whole.md).

| # | Check | Result | Consequence |
|---|---|---|---|
| 1 | Initializr RestClient starter label; Boot 4 split test starter names; the timeout exception causes the JDK `RestClient` throws | Passed. "HTTP Client" (`spring-restclient`); `…-test` starters per module; connect → `HttpConnectTimeoutException`, read → `HttpTimeoutException`, both in `ResourceAccessException` | Check the connect type first (it extends the read type). Initializr needs `4.1.1` and the foojay resolver |
| 2 | `@Retryable(maxRetriesString = "${…}", timeoutString = "${…}")` with a `PT8S` value, enabled by `@EnableResilientMethods` | Passed. 3 attempts for a connection failure, 1 for other exceptions; the timeout stops new attempts but not a running one | As documented |
| 3 | Caffeine `AsyncCache` with `Caffeine.executor(…)` wrapped by Micrometer context propagation | Passed, with `io.micrometer:context-propagation` added (not transitive) and a selective `Slf4jThreadLocalAccessor` | New Boot-managed dependency, approved |
| 4 | `CaffeineCacheMetrics` binding for an `AsyncCache` | Passed. `cache.gets` hit and miss counted (needs `recordStats()`) | As documented |
| 5 | Security with `management.server.port=8081` and a chain for `EndpointRequest.toAnyEndpoint()` | Partly. Exposed endpoints worked, but other paths on 8081 returned 401 instead of 404. Matching the whole management server gave 404 | [ADR-0044](../adr/0044-secure-the-management-port-as-a-whole.md) |
| 6 | Bash `/dev/tcp` healthcheck in `eclipse-temurin:25-jre` | Passed. Exit 0 when readiness is UP, 1 on a closed port. The image is Ubuntu 26.04, runs as root by default and has no curl | Keep the non-root user in the Dockerfile |

The frontend CSP check (does the production build emit inline scripts?) happens in the frontend block.

## Schedule (relative hours; planning only)

| Start | Block | Budget | Done when |
|---|---|---|---|
| H+0:00 | **Kickoff and merge hygiene** (checklist above, including the spike) | 1:10 | CI green on the bootstrap commit |
| H+1:10 | **Search:** domain records, gateways, `StorefrontPolicy`; `ItunesSearchClient` with timeouts and error translation; `SearchGatewayAdapter` with the 429 guard and one outcome metric and log line per logical call; first commit moves `stubs/` into `backend/src/test/resources/wiremock/` and deletes it (`Exception: pure move`) ([ADR-0041](../adr/0041-move-captures-into-wiremock-and-remove-stubs.md)); mapper (test-first with those fixtures); service; controller; validation | 1:30 | `GET /api/v1/apps?term=pages&cc=de` works via curl |
| H+2:40 | **Details:** `LookupResult`; `MzLookupClient` and `LookupGatewayAdapter` (lookup outcome metrics); mapper (artwork shapes, nulls, kinds, ids, `platform` mapping, `BigDecimal`); storefront and language checks; controller | 1:30 | `GET /api/v1/apps/{id}?cc=de&l=de&platform=mac` works |
| H+4:10 | **Caching, resilience and limiter** ([ADR-0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md)): async caches (`app-search`, `app-details` with `LookupResult` expiry) with single-flight deduplication, an explicit virtual-thread executor and MDC propagation; cache metrics binding; `@EnableResilientMethods` and retry on connection failures with timeout; outbound Search limiter with the configurable budget (implementation approved at the start of the block); dedup, negative-cache, not-cached-failure and budget tests (the 503 mapping itself is tested in the errors block) | 1:35 | Resilience tests green; a burst above the budget never reaches WireMock |
| H+5:45 | **Errors and Level 1 observability:** `ProblemDetails` factory and advice with `errors[]`, `ProblemType`, correlation-id filter with validation and precedence, ECS logging config, log-safety test | 0:40 | Error-matrix tests green |
| H+6:25 | **Docker:** hardened backend Dockerfile (Ubuntu-based JRE), compose (localhost binds, healthcheck), `.env.example`, CI `images` job, Dependabot `docker` (`/backend`) and `docker-compose` | 0:30 | Readiness UP in the container, search works |
| H+6:55 | **Auth:** security dependencies; token endpoint; JWT claims and validators; scope; constant-time check; catch-all `denyAll`, `/error`, management-port chain matching the whole management server ([ADR-0044](../adr/0044-secure-the-management-port-as-a-whole.md)); `clientId` in MDC; fail-fast `ApplicationContextRunner` test; security tests | 1:10 | 401 without a token, 403 without the scope, 200 with a token, `/actuator/env` 401 on 8080 and 404 on 8081 |
| H+8:05 | **OpenAPI** annotations and bearer scheme; `application-prod.yml` disabling the API docs; README check; `scripts/smoke.sh` (checks below) | 0:20 | Swagger UI usable with a token, smoke checks pass |
| H+8:25 | **Operability:** `liveTest` source set (required-key list), nightly `apple-drift` workflow, `missing_field` counter, `ops/alerts.yml` | 0:40 | `./gradlew liveTest` passes locally |
| H+9:05 | **Frontend:** scaffold (below), login, search, details, platform toggle, error mapping by problem type, `DebugLogService`, `demo` build configuration, nginx image with CSP and query-free access log, 3 unit tests, CI `frontend` job, Dependabot `npm` and `docker` (`/frontend`), compose service | 1:30 | Full flow works at `localhost:4200` |
| H+10:35 | **Buffer:** lunch, AI log and ADR tidy-up, honest assessment, presentation dry run | 0:45 | — |

**Total: 11:20.** That's about 3 h 20 min more than a normal day: the large-product changes (2026-09-13), the de-risking spike and the outbound limiter (2026-09-14). The blocks are in priority order, and the independent tracks can overlap (see above).

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
- the 429 short-circuit and the outbound limiter;
- request deduplication and the caches;
- the management port;
- the ArchUnit rules.

### Stretch goals (only after all blocks, with Vasyl's go-ahead)

1. Level 2 observability ([`../operations/observability.md`](../operations/observability.md#level-2-opentelemetry--grafana-lgtm-stretch-goal)).
2. Circuit breaker.

## Frontend scaffold (start of the frontend block)

1. From the repo root: `nvm use && npx @angular/cli@22 new frontend --routing --style=scss --ssr=false --zoneless --test-runner=vitest --ai-config=none --skip-git --package-manager=npm --interactive=false`
2. `cd frontend && npx ng add @angular/material --skip-confirmation --interactive=false`
3. `ng generate environments`, then add the `demo` build configuration ([`../architecture/frontend.md`](../architecture/frontend.md#debug-logging)).
4. Add `proxy.conf.json` (`/api` and `/auth` → `http://localhost:8080`) and set `proxyConfig` in the `serve` options.
5. Keep the generated `.editorconfig` and `.prettierrc`. Commit the untouched generator output separately (`chore(frontend): scaffold …`, `Exception: generated`).

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
