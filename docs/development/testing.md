# Testing

## Strategy

| Layer | Tool | What | Why |
|---|---|---|---|
| Mappers | Plain JUnit + WireMock `__files` JSON | iOS app, Mac-only app, universal app on both platforms, ebook (`OTHER`), explicit nulls, both artwork shapes plus a hand-made concrete-URL object, missing offers, numeric and string ids, every raw key name, `BigDecimal` prices | Highest risk: inconsistent Legacy payloads. Pure, fast, cheap |
| Storefront policy | Plain JUnit (fake Apple outcome) | All four cases of the policy table, including the logs and counters emitted | Our own logic with two maintenance signals |
| Apple clients | WireMock (`wiremock-spring-boot`) | Happy path; empty results; 429 with `Retry-After` and the short-circuit that follows; 5xx (not retried); connect failure (retried) vs read timeout (not retried) with short test timeouts; malformed JSON; `text/javascript` content type; Search 400 `[country]`; lookup storefront fallback; served language; `outcome` metric tags | Proves error translation and retry classification against real HTTP behavior |
| Web layer | `@WebMvcTest` + `MockMvcTester`, run as an authenticated user (`AppSearchControllerTest`, `AppDetailsControllerTest`, `ApiExceptionHandlerTest`) | Response shapes; validation → 400 with `errors[]` naming the public parameter; the whole error matrix → problem type, status, `Retry-After`; no exception messages in bodies; correlation id echo | The public contract |
| Correlation id | Plain JUnit (`CorrelationIdFilterTest`) | Valid ids echoed, unsafe or missing ids replaced, MDC cleared; with a trace, the trace id is echoed and a valid incoming id lands in `clientCorrelationId` | Log injection and traceability |
| Tracing and span privacy | Plain JUnit with Micrometer's `TestObservationRegistry` and `MockRestServiceServer` (`QueryStringObservationFilterTest`); `@SpringBootTest` with tracing on, WireMock and an in-memory `SpanExporter` bean (`TracingIntegrationTest`) | URL key values lose query and fragment; the response's correlation id is the trace id; the Apple client span shares the trace of the server span (cache loader thread); no span name or attribute contains the term | Search terms must not reach the tracing backend ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)) |
| Caching, retry and limiter | Plain JUnit with real caches and a fake ticker (`AppSearchServiceTest`, `AppDetailsServiceTest`); Spring proxy (`AppleRetryTest`); `SearchBudgetTest` | N concurrent identical searches → 1 upstream call; concurrent unknown-id lookups → 1 upstream call; both TTLs; a failed load is never served to the next caller (on the real virtual-thread executor); MDC reaches loader threads; 3 attempts for a refused connection, 1 for a read timeout; every attempt takes a budget permit; burst, refill and wait of the budget | The resilience claims must be proven, not asserted |
| Security | `@SpringBootTest` on random ports (`SecurityIntegrationTest`) | Token for valid Basic credentials; 401 for invalid ones; expired, wrong `iss`, wrong `aud` and foreign-key tokens rejected; 403 without the scope; catch-all deny (`/actuator/env` 401 on port 8080, 403 with a token); management port exposes only the listed endpoints (404 otherwise). `TokenRateLimitIntegrationTest` (own context, limit 3): successes don't use up the failure budget; over it, 429 `too-many-requests` with `Retry-After` for valid credentials too, no credentials in the body, and the `outcome` counter. `TokenRateLimitForwardedHeaderIntegrationTest` (own context, `spring.main.cloud-platform=kubernetes`, limit 1): a different `X-Forwarded-For` still gets 429. `TokenRequestLimiterTest` (plain JUnit, fake clock): burst, refill, released permits, wait of at least 1 s, bounded keys and idle expiry, IPv4 and IPv6 /64 keys | Security must not break silently |
| Log safety | `@SpringBootTest` with ECS JSON logs, log export on, an in-memory `LogRecordExporter` bean and WireMock (`LogSafetyIntegrationTest`); `ApplicationContextRunner` (`LogExportAppenderTest`) | Application log lines carry the correlation id, client id and term length, never the term, the token, the secrets or an Apple body; the exported records obey the same rules, carry the trace context and no MDC key; the appender is attached only with `management.logging.export.enabled=true` and detached when the context closes | The binding privacy list ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)) |
| Configuration | `ApplicationContextRunner` (`ApplicationPropertiesTest`, `AuthConfigurationTest`) | Defaults bind; invalid Apple/cache values, a missing JWT secret or client credentials, a secret that isn't Base64 or shorter than 32 bytes, and a TTL above 1 h stop the context without revealing the secret | Fail-fast must actually fail |
| OpenAPI | `@SpringBootTest` (`OpenApiDocumentationTest`) | Both endpoints and the bearer scheme are documented; `/auth/token` documents 401 and 429 with `Retry-After`; the `prod` profile serves no API docs | Docs stay reachable locally and closed in production |
| API contract snapshot | `@SpringBootTest` (`OpenApiContractSnapshotTest`) | `/v3/api-docs`, normalized (keys sorted, generated `servers` removed, pretty-printed), equals the committed [`docs/api/openapi.json`](../api/openapi.json); a mismatch fails with the first differing lines and the update command | Every public API change is visible in the pull request diff |
| Architecture | ArchUnit (`archunit-junit6`) | Package dependency rules ([`../architecture/overview.md`](../architecture/overview.md#dependency-rules-enforced-by-an-archunit-test)) | Boundaries don't erode |
| Apple drift | `liveTest` source set (`AppleDriftLiveTest`), tag `live`, plain JUnit without a Spring context | Exactly 3 real calls on `cc=de`: one Search (`pages`), a lookup of Pages (`361309726`, iOS) and one of Final Cut Pro (`424389933`, Mac). They use the production clients, mappers, `AppleMissingFieldDetector` and the URLs and timeouts from `application.yml`. No `missing_field` counter increments (the same required-key list), plus known values (`kind`, `bundleId`, `deviceFamilies`, offer version). The unit test `AppleMissingFieldDetectorTest` proves that the captures produce no signal | Frozen fixtures can't detect Legacy API changes ([ADR-0037](../adr/0037-legacy-api-drift-detection.md)) |
| Frontend logic | Vitest | Auth interceptor, debug log, locale pre-fill, problem-type → message (including the platform-specific not-found message), search result kind → details platform ([`../architecture/frontend.md`](../architecture/frontend.md#tests)) | The client logic most likely to break silently |
| Frontend components | Vitest with `TestBed`, `RouterTestingHarness` and `HttpTestingController`; fake timers for the debounce and the slow hint | Login (validation, success and return URL, wrong credentials, server and network errors), search (debounce, URL sync, loading and slow hint, no results, result list with platform links, field validation, problem types → messages, retry), details (render, placeholders, safe links, platform switch, served-language note, platform-specific not-found, storefront and invalid-request errors, retry), the shared loading and problem panels ([`../architecture/frontend.md`](../architecture/frontend.md#tests)) | The screens and states the demo shows, checked through the DOM and the HTTP calls instead of by hand |

**Deliberately not tested:**
- Spring wiring beyond one context-load test and the integration tests above.
- Records and accessors.
- Individual OpenAPI details beyond the presence checks: the snapshot test covers the whole document, and reviewers judge its diff.
- Angular Material's own behavior, styles and layout, and the app shell beyond one render test.
- Live Apple calls in `check`: these run only in `liveTest`.

## Test data ([ADR-0041](../adr/0041-move-captures-into-wiremock-and-remove-stubs.md))

```
backend/src/test/resources/wiremock/
├── README.md                          capture metadata: request URLs, date, trimming rules, naming
├── mappings/apple/search/…json       request matchers, status, headers (e.g. 429 with Retry-After)
├── mappings/apple/lookup/…json
├── __files/apple/search/<status>-<scenario>[-<variant>][-<cc>].json
└── __files/apple/lookup/…json        incl. doc-sample-*.json
```

- **Origin:** real Apple captures from 2026-09-13 plus two documentation samples. Request URLs, capture date, trimming rules and naming are in [`wiremock/README.md`](../../backend/src/test/resources/wiremock/README.md). They moved here from the former `stubs/` folder at the start of the Search block.
- **Refreshing:** replace the file here directly; there is no second copy. The procedure is in [`../operations/runbook.md`](../operations/runbook.md#refresh-captures-and-fixtures).
- **Hand-made variants** (e.g. an artwork object with a concrete URL) are named `synthetic-<scenario>.json`, so they're never mistaken for captures.
- **Timeouts:** tests set short `appstore.apple.timeout.*` values (e.g. `PT0.2S`) and use WireMock `fixedDelayMilliseconds`.
- **Prometheus endpoint:** in the kickoff spike, `/actuator/prometheus` answered 404 inside `@SpringBootTest` but 200 in the running jar. Integration tests don't assert it (unless a test explicitly enables metrics export, *verify* how in Boot 4); the smoke script and the container check cover it.

## Commands

```bash
(cd backend && ./gradlew check)       # Spotless check + all tests except live; compiles the live tests without running them
(cd backend && ./gradlew liveTest)    # real Apple calls; not part of check (nightly: .github/workflows/apple-drift.yml)
(cd frontend && npm test -- --watch=false)    # Vitest via ng test (logic and component specs)
(cd frontend && npm test -- --watch=false --include src/app/features/search/search.component.spec.ts)    # one spec
```

### Update the OpenAPI contract snapshot

When a backend change alters the public API on purpose, regenerate `docs/api/openapi.json` and commit it in the same pull request:

```bash
(cd backend && ./gradlew test --tests '*OpenApiContractSnapshotTest' -PupdateOpenApiSnapshot)
git diff docs/api/openapi.json    # review the contract change
```

- `-PupdateOpenApiSnapshot` makes the test write the normalized document instead of comparing it. Without the property, or with the value `false`, the test only compares, as in `check` and CI. When the `CI` environment variable is set, the build refuses to start with the property enabled, so CI can't overwrite the snapshot and pass.
- The `test` task prints full assertion messages, so a mismatch shows its diff excerpt and the update command in the console and in CI logs.
- The Gradle `test` task passes the snapshot's absolute path as the system property `openapi.snapshot.path`. A test started from an IDE without it resolves `../docs/api/openapi.json` from `backend/`.
- Breaking changes still follow the [compatibility rules](../api/README.md#compatibility-rules).
- Then regenerate the frontend API types in the same pull request (next section).

### Regenerate the frontend API types

`frontend/src/app/core/api/generated/openapi.ts` is generated from `docs/api/openapi.json` by `openapi-typescript` and committed, so the frontend Docker build doesn't need `docs/` ([ADR-0052](../adr/0052-generate-frontend-api-types-from-the-openapi-contract.md)):

```bash
nvm use && (cd frontend && npm ci && npm run generate:api)
git diff frontend/src/app/core/api/generated    # review, then fix api.types.ts if tsc complains
(cd frontend && npm test -- --watch=false && npm run build)
```

- Never edit the generated file by hand. Prettier skips it (`frontend/.prettierignore`).
- The CI `frontend` job runs the generator and fails when the committed file differs, including after an `openapi-typescript` update that changes the output.
