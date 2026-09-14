# Glossary

| Term | Meaning |
|---|---|
| **adamId** | Apple's numeric identifier of a store item. The Search API returns it as `trackId` (a number), the lookup API as `id` (a string live, a number in doc samples). Our API always uses a string `id`. |
| **allowlist (storefront)** | Our list of 175 country codes that have an App Store storefront ([ADR-0008](adr/0008-storefront-allowlist-that-reports-its-own-staleness.md)). |
| **Apps and Books (VPP)** | Apple's authenticated API for organizations to manage purchased apps and books. Its request limits do **not** apply to the public lookup API. |
| **captures** | Real Apple responses (trimmed, dated), used as test fixtures. Kept in `backend/src/test/resources/wiremock/` ([ADR-0041](adr/0041-move-captures-into-wiremock-and-remove-stubs.md)). |
| **`cc`** | Country code of the storefront (ISO 3166-1 alpha-2, plus `xk`). A query parameter on both endpoints; internally `countryCode`. |
| **correlation id** | An id that ties together one request's logs, responses and frontend console output. Header `X-Correlation-Id`. When tracing is enabled, it equals the trace id. |
| **drift** | Apple changing a response shape without notice. Detected by the nightly live test and the `missing_field` metric ([ADR-0037](adr/0037-legacy-api-drift-detection.md)). |
| **fixture** | Test data owned by the backend under `backend/src/test/resources/wiremock/`: the moved captures, plus hand-made `synthetic-*` variants. |
| **gateway (port)** | A domain interface (`AppSearchGateway`, `AppDetailsGateway`) that the Apple adapter implements ([ADR-0028](adr/0028-package-boundaries-and-ports.md)). |
| **`itvt` / sToken** | An organization's Apps and Books token, sent as a cookie to unlock B2B app metadata. Out of scope: never accepted or forwarded. |
| **`kind`** | Three vocabularies. Search API: `software`, `mac-software`, `ebook`. Lookup API: `iosSoftware`, `desktopApp`, `epubBook`. Our API: `IOS_APP`, `MAC_APP`, `OTHER` (extensible). |
| **`l`** | Requested language tag for details (e.g. `de`, `en-GB`); internally `languageTag`. Apple may serve another language, and `storefront.language` reports what it served. |
| **Legacy** | Apple's label for the MZStorePlatform lookup documentation: no schema, no versioning, may change. |
| **lookup API / MZStorePlatform** | `uclient-api.itunes.apple.com/WebObjects/MZStorePlatform.woa/wa/lookup`, the metadata service MDM servers use. Source of our details endpoint. |
| **LookupResult** | The cached outcome of a details lookup: `Found(details)` or `NotFound`, with different TTLs ([ADR-0030](adr/0030-details-cache-and-bounded-retry.md)). |
| **MDM** | Mobile device management, Relution's product domain. |
| **negative cache** | Caching a "not found" result briefly (60 s), so repeated unknown ids don't reach Apple. |
| **`platform`** | Our details parameter `ios` or `mac`. Apple's equivalents are the channels `enterprisestore` and `macappstore` ([ADR-0029](adr/0029-domain-terms-in-public-api.md)). Not to be confused with **`platforms`** in responses (device families: `IPHONE`, `IPAD`, `MAC`, …). |
| **problem type** | The `type` URN of an RFC 9457 error response, e.g. `urn:appstore:problem:app-not-found`. The stable way for clients to identify errors. |
| **Search API** | The public iTunes Search API (`itunes.apple.com/search`), rate-limited to about 20 calls per minute per source IP. |
| **short-circuit** | Answering 503 locally while Apple's `Retry-After` window is running, without calling Apple ([ADR-0031](adr/0031-rate-limit-short-circuit.md)). |
| **single-flight** | Concurrent identical requests share one upstream call (via Caffeine `AsyncCache`). |
| **storefront** | Apple's per-country store. Determines prices, availability and default language. Apple returns its code upper case (`DE`). |
| **universal app** | An app with `kind = iosSoftware` whose `deviceFamilies` include `mac`. |
