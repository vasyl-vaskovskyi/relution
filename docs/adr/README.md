# Architecture decision records

One file per decision, MADR-lite format ([ADR-0039](0039-documentation-structure.md)). Decisions are never rewritten. A later ADR supersedes or amends an earlier one, and the status line links to it.

**Statuses:** `Accepted`, `Accepted; amended by …`, `Superseded by …`, `Open` (do not implement), `Proposed`.

## Open questions

| ADR | Title | Status |
|---|---|---|
| [0026](0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md) | Hybrid routing: Angular client calls Apple Search directly, details via our server | Open — do not implement until decided |

## All decisions

| ADR | Title | Status |
|---|---|---|
| [0001](0001-server-only-scope-with-spring-boot.md) | Server-only scope with Spring Boot | Accepted; amended by ADR-0017 (web client added) |
| [0002](0002-java-25-lts.md) | Java 25 LTS | Accepted |
| [0003](0003-spring-boot-4-1-with-restclient.md) | Spring Boot 4.1 with RestClient | Accepted |
| [0004](0004-self-issued-hs256-jwt-no-fallback.md) | Self-issued HS256 JWT, no fallback | Accepted; amended by ADR-0017, ADR-0018 and ADR-0034 (token claims hardening) |
| [0005](0005-treat-the-mzstoreplatform-api-as-untrusted.md) | Treat the MZStorePlatform API as untrusted | Accepted; adapter package renamed by ADR-0028 (`integration.apple`); fixture handling amended by ADR-0040 and ADR-0041 |
| [0006](0006-docker-with-no-default-secrets.md) | Docker with no default secrets | Accepted; amended by ADR-0033 (variable names) and ADR-0034 (container hardening) |
| [0007](0007-part-3-direction-resilience-caching.md) | Part 3 direction: resilience + caching | Accepted; scope amended by ADR-0017, ADR-0027, ADR-0030 (retry only on connection failures), ADR-0031 and ADR-0035 |
| [0008](0008-storefront-allowlist-that-reports-its-own-staleness.md) | Storefront allowlist that reports its own staleness | Accepted |
| [0009](0009-report-the-language-apple-served-instead-of-rejecting.md) | Report the language Apple served instead of rejecting | Accepted |
| [0010](0010-cc-required-on-both-endpoints.md) | `cc` required on both endpoints | Accepted |
| [0011](0011-60-second-negative-cache-for-unknown-app-ids.md) | 60-second negative cache for unknown app ids | Superseded by ADR-0030 (the 60 s negative-cache intent is kept, the mechanism changed) |
| [0012](0012-upstream-status-mapping.md) | Upstream status mapping | Accepted; amended by ADR-0030 (bounded retry) and ADR-0031 (429 short-circuit) |
| [0013](0013-search-limit-1-50-no-pagination.md) | Search `limit` 1–50, no pagination | Accepted |
| [0014](0014-optional-platform-on-details.md) | Optional `platform` on details | Superseded by ADR-0029 (`platform=ios\|mac`) |
| [0015](0015-package-com-example-appstore.md) | Package `com.example.appstore` | Accepted; sub-packages renamed by ADR-0028 |
| [0016](0016-defer-editorconfig-to-the-day.md) | Defer `.editorconfig` to the day | Superseded for the backend by ADR-0036 (Spotless); the frontend keeps the generated `.editorconfig` |
| [0017](0017-add-an-angular-web-client-time-boxed.md) | Add an Angular web client, time-boxed | Accepted; amended by ADR-0027 and ADR-0031 (429 short-circuit) |
| [0018](0018-frontend-auth-login-form-token-in-memory.md) | Frontend auth: login form, token in memory | Accepted; amended by ADR-0033 (variable names) and ADR-0034 (token claims, constant-time check) |
| [0019](0019-serve-the-frontend-from-an-nginx-container-same-origin.md) | Serve the frontend from an nginx container, same origin | Accepted; amended by ADR-0034 (unprivileged nginx, security headers) |
| [0020](0020-central-debug-logging-in-the-frontend.md) | Central debug logging in the frontend | Accepted; amended by ADR-0034 (debug override only in a demo build) |
| [0021](0021-node-24-lts-via-nvm.md) | Node 24 LTS via nvm | Accepted |
| [0022](0022-scaffold-the-angular-project-on-the-day-prep-holds-docs-only.md) | Scaffold the Angular project on the day; prep holds docs only | Accepted; amended by ADR-0040 and ADR-0041 (`stubs/` moves into WireMock test resources on the day, then is deleted) |
| [0023](0023-frontend-scope-and-tests.md) | Frontend scope and tests | Accepted; amended by ADR-0029 (errors mapped by problem type) |
| [0024](0024-keep-real-apple-captures-as-trimmed-consistently-named-fixtu.md) | Keep real Apple captures as trimmed, consistently named fixtures | Accepted; amended by ADR-0040 and ADR-0041 (captures move into WireMock test resources; `stubs/` removed on the day) |
| [0025](0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md) | Deal with the per-IP Search rate limit within Apple's rules | Accepted |
| [0026](0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md) | Hybrid routing: Angular client calls Apple Search directly, details via our server | Open — do not implement until decided |
| [0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md) | Observability: logs and metrics in the app, OpenTelemetry + Grafana LGTM as a stretch goal | Accepted; amended by ADR-0033 (Boot-native OTLP properties, variable names), ADR-0034 (trace id as correlation id) and ADR-0038 (alert rules, `appstore.` metric prefix) |
| [0028](0028-package-boundaries-and-ports.md) | Package boundaries, ports and ArchUnit enforcement | Accepted; renames sub-packages of ADR-0015 |
| [0029](0029-domain-terms-in-public-api.md) | Domain terms in the public API, decimal money, extensible contract | Accepted; supersedes ADR-0014; amends ADR-0023 |
| [0030](0030-details-cache-and-bounded-retry.md) | Native async caches, one lookup-result cache, bounded retry | Accepted; supersedes ADR-0011; amends ADR-0012 |
| [0031](0031-rate-limit-short-circuit.md) | Short-circuit Search calls after Apple returns 429 | Accepted; amends ADR-0012 and ADR-0007 |
| [0032](0032-management-port-and-probes.md) | Separate management port, probes, restricted exposure | Accepted |
| [0033](0033-configuration-namespace.md) | One configuration namespace `appstore.*` | Accepted; amends ADR-0006, ADR-0018 and ADR-0027 |
| [0034](0034-security-and-privacy-hardening.md) | Security and privacy hardening | Accepted; amends ADR-0004, ADR-0006, ADR-0019, ADR-0020 and ADR-0027 |
| [0035](0035-continuous-integration.md) | Continuous integration with GitHub Actions | Accepted; amends ADR-0007 (CI is merge hygiene, not a Part 3 topic) |
| [0036](0036-formatting-version-catalog-and-updates.md) | Formatter, version catalog, wrapper checksum, dependency updates | Accepted; supersedes ADR-0016 for the backend |
| [0037](0037-legacy-api-drift-detection.md) | Detect drift of the Legacy Apple APIs | Accepted |
| [0038](0038-alert-rules.md) | Committed alert rules | Accepted; amends ADR-0027 |
| [0039](0039-documentation-structure.md) | Documentation structure, ADR format and conventions | Accepted |
| [0040](0040-capture-archive-and-test-fixtures.md) | Capture archive vs. test fixtures | Superseded by ADR-0041 (`stubs/` is moved into WireMock test resources on the day and then deleted) |
| [0041](0041-move-captures-into-wiremock-and-remove-stubs.md) | Move the captures into WireMock test resources and remove `stubs/` | Accepted; supersedes ADR-0040 |

## Writing a new ADR

1. Copy the structure of an existing ADR. Use the next free number and a kebab-case title.
2. Fill in Status, Date, Context, Options, Decision and Consequences.
3. If it replaces or changes an earlier decision, update only that ADR's **Status** line ("Superseded by ADR-NNNN" or "Accepted; amended by ADR-NNNN"). Leave the rest of the old ADR unchanged.
4. Add a row to this index in the same commit.
