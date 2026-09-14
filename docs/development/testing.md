# Testing

## Strategy

| Layer | Tool | What | Why |
|---|---|---|---|
| Mappers | Plain JUnit + WireMock `__files` JSON | iOS app, Mac-only app, universal app on both platforms, ebook (`OTHER`), explicit nulls, both artwork shapes plus a hand-made concrete-URL object, missing offers, numeric and string ids, every raw key name, `BigDecimal` prices | Highest risk: inconsistent Legacy payloads. Pure, fast, cheap |
| Storefront policy | Plain JUnit (fake Apple outcome) | All four cases of the policy table, including the logs and counters emitted | Our own logic with two maintenance signals |
| Apple clients | WireMock (`wiremock-spring-boot`) | Happy path; empty results; 429 with `Retry-After` and the short-circuit that follows; 5xx (not retried); connect failure (retried) vs read timeout (not retried) with short test timeouts; malformed JSON; `text/javascript` content type; Search 400 `[country]`; lookup storefront fallback; served language; `outcome` metric tags | Proves error translation and retry classification against real HTTP behavior |
| Web layer | `@WebMvcTest` + `MockMvcTester`, run as an authenticated user (`AppSearchControllerTest`, `AppDetailsControllerTest`, `ApiExceptionHandlerTest`) | Response shapes; validation → 400 with `errors[]` naming the public parameter; the whole error matrix → problem type, status, `Retry-After`; no exception messages in bodies; correlation id echo | The public contract |
| Correlation id | Plain JUnit (`CorrelationIdFilterTest`) | Valid ids echoed, unsafe or missing ids replaced, MDC cleared | Log injection and traceability |
| Caching, retry and limiter | Plain JUnit with real caches and a fake ticker (`AppSearchServiceTest`, `AppDetailsServiceTest`); Spring proxy (`AppleRetryTest`); `SearchBudgetTest` | N concurrent identical searches → 1 upstream call; concurrent unknown-id lookups → 1 upstream call; both TTLs; a failed load is never served to the next caller (on the real virtual-thread executor); MDC reaches loader threads; 3 attempts for a refused connection, 1 for a read timeout; every attempt takes a budget permit; burst, refill and wait of the budget | The resilience claims must be proven, not asserted |
| Security | `@SpringBootTest` on random ports (`SecurityIntegrationTest`) | Token for valid Basic credentials; 401 for invalid ones; expired, wrong `iss`, wrong `aud` and foreign-key tokens rejected; 403 without the scope; catch-all deny (`/actuator/env` 401 on port 8080, 403 with a token); management port exposes only the listed endpoints (404 otherwise) | Security must not break silently |
| Log safety | `@SpringBootTest` with ECS JSON logs and WireMock (`LogSafetyIntegrationTest`) | Application log lines carry the correlation id, client id and term length, never the term, the token, the secrets or an Apple body | The binding privacy list ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)) |
| Configuration | `ApplicationContextRunner` (`ApplicationPropertiesTest`, `AuthConfigurationTest`) | Defaults bind; invalid Apple/cache values, a missing JWT secret or client credentials, a secret that isn't Base64 or shorter than 32 bytes, and a TTL above 1 h stop the context without revealing the secret | Fail-fast must actually fail |
| OpenAPI | `@SpringBootTest` (`OpenApiDocumentationTest`) | Both endpoints and the bearer scheme are documented; the `prod` profile serves no API docs | Docs stay reachable locally and closed in production |
| Architecture | ArchUnit (`archunit-junit6`) | Package dependency rules ([`../architecture/overview.md`](../architecture/overview.md#dependency-rules-enforced-by-an-archunit-test)) | Boundaries don't erode |
| Apple drift | `liveTest` source set (`AppleDriftLiveTest`), tag `live`, plain JUnit without a Spring context | Exactly 3 real calls on `cc=de`: one Search (`pages`), a lookup of Pages (`361309726`, iOS) and one of Final Cut Pro (`424389933`, Mac). They use the production clients, mappers, `AppleMissingFieldDetector` and the URLs and timeouts from `application.yml`. No `missing_field` counter increments (the same required-key list), plus known values (`kind`, `bundleId`, `deviceFamilies`, offer version). The unit test `AppleMissingFieldDetectorTest` proves that the captures produce no signal | Frozen fixtures can't detect Legacy API changes ([ADR-0037](../adr/0037-legacy-api-drift-detection.md)) |
| Frontend | Vitest | Auth interceptor, locale pre-fill, problem-type → message ([`../architecture/frontend.md`](../architecture/frontend.md#tests)) | The client logic most likely to break silently |

**Deliberately not tested:**
- Spring wiring beyond one context-load test and the integration tests above.
- Records and accessors.
- The OpenAPI document in detail (only its presence and the security scheme).
- Angular templates and components (covered by the demo).
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
(cd frontend && npm test -- --watch=false)    # Vitest via ng test
```
