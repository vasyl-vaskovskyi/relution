# Testing

## Strategy

| Layer | Tool | What | Why |
|---|---|---|---|
| Mappers | Plain JUnit + WireMock `__files` JSON | iOS app, Mac-only app, universal app on both platforms, ebook (`OTHER`), explicit nulls, both artwork shapes plus a hand-made concrete-URL object, missing offers, numeric and string ids, every raw key name, `BigDecimal` prices | Highest risk: inconsistent Legacy payloads. Pure, fast, cheap |
| Storefront policy | Plain JUnit (fake Apple outcome) | All four cases of the policy table, including the logs and counters emitted | Our own logic with two maintenance signals |
| Apple clients | WireMock (`wiremock-spring-boot`) | Happy path; empty results; 429 with `Retry-After` and the short-circuit that follows; 5xx (not retried); connect failure (retried) vs read timeout (not retried) with short test timeouts; malformed JSON; `text/javascript` content type; Search 400 `[country]`; lookup storefront fallback; served language; `outcome` metric tags | Proves error translation and retry classification against real HTTP behavior |
| Web layer | `@WebMvcTest` + `MockMvcTester` | Validation → 400 with `errors[]`; exception → problem type; correlation id echo and validation; 401 without a token; 403 without the scope; 200 with a token | The public contract |
| Caching and limiter | `@SpringBootTest` + WireMock | N concurrent identical searches → 1 upstream call; concurrent unknown-id lookups → 1 upstream call; `NotFound` expires after its TTL; failures aren't cached; with a small test budget, the call after the last permit gets 503 with `Retry-After` and never reaches WireMock | The resilience claims must be proven, not asserted |
| Security | Slice/integration | Token for valid Basic credentials; 401 for invalid ones; expired token, wrong `iss` or `aud` rejected; `/actuator/env` returns 401 on port 8080 and 404 on the management port; no secret or `Bearer` value in captured logs | Security must not break silently |
| Configuration | `ApplicationContextRunner` | A missing or short JWT secret stops the context | Fail-fast must actually fail |
| Architecture | ArchUnit (`archunit-junit6`) | Package dependency rules ([`../architecture/overview.md`](../architecture/overview.md#dependency-rules-enforced-by-an-archunit-test)) | Boundaries don't erode |
| Apple drift | `liveTest` source set, tag `live` | At most 3 real calls; the required-key list (the same as the `missing_field` counter) is present, plus known values for the pinned apps | Frozen fixtures can't detect Legacy API changes ([ADR-0037](../adr/0037-legacy-api-drift-detection.md)) |
| Frontend | Vitest | Auth interceptor, locale pre-fill, problem-type → message ([`../architecture/frontend.md`](../architecture/frontend.md#tests)) | The client logic most likely to break silently |

**Deliberately not tested:**
- Spring wiring beyond one context-load test.
- Records and accessors.
- OpenAPI output.
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

- **Origin:** the Apple captures prepared before the Discovery Day move here from `stubs/` (same names) at the start of the Search block, together with their README (request URLs, capture date, trimming rules, naming). `stubs/` is then deleted.
- **Refreshing:** replace the file here directly; there is no second copy. The procedure is in [`../operations/runbook.md`](../operations/runbook.md#refresh-captures-and-fixtures).
- **Hand-made variants** (e.g. an artwork object with a concrete URL) are named `synthetic-<scenario>.json`, so they're never mistaken for captures.
- **Timeouts:** tests set short `appstore.apple.timeout.*` values (e.g. `PT0.2S`) and use WireMock `fixedDelayMilliseconds`.
- **Prometheus endpoint:** in the kickoff spike, `/actuator/prometheus` answered 404 inside `@SpringBootTest` but 200 in the running jar. Integration tests don't assert it (unless a test explicitly enables metrics export, *verify* how in Boot 4); the smoke script and the container check cover it.

## Commands

```bash
(cd backend && ./gradlew check)       # Spotless check + all tests except live
(cd backend && ./gradlew liveTest)    # real Apple calls; not part of check
(cd frontend && npm test -- --watch=false)
```
