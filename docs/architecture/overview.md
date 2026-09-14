# Architecture overview

## Context

```
                 ┌────────────────────── appstore service (Spring Boot) ──────────────────────┐
browser ─nginx─▶ │ api ──▶ catalog (services, caches, storefront policy) ◀── integration.apple │ ──▶ Apple Search API
curl/Bruno ────▶ │  ▲                         ▲ gateways (ports)                               │ ──▶ Apple lookup API
                 │ auth (JWT)          observability (correlation id, metrics)                 │
                 └──────────────────── management port 8081: health, metrics ─────────────────┘
```

- **Upstreams:** the iTunes Search API (rate-limited per IP) and the MZStorePlatform lookup (Legacy, undocumented schema). Their observed behavior is in [`../integrations/apple-api-behavior.md`](../integrations/apple-api-behavior.md).
- **Clients:** the Angular web client (same origin through nginx) and API clients such as curl or Bruno.

## Repository layout

```
backend/     Spring Boot service (Gradle Kotlin DSL, version catalog)
frontend/    Angular 22 + Angular Material
docs/        architecture, api, integrations, operations, development, adr, glossary
ops/         alert rules (alerts.yml)
scripts/     smoke.sh
.github/     CI workflows, Dependabot, pull request template
docker-compose.yml, compose.observability.yml, .env.example, .env.observability.example, .nvmrc
README.md, CONTRIBUTING.md, CLAUDE.md
```

## Backend packages ([ADR-0028](../adr/0028-package-boundaries-and-ports.md))

```
com.example.appstore
├── api/                   controllers, request/response DTOs, validation, ProblemDetail advice, ProblemType,
│                          ProblemDetails factory (also used by auth), OpenAPI config
├── catalog/               domain records (AppSummary, AppDetails, Price, Rating, Storefront), Platform, AppKind,
│   │                      LookupResult, sealed UpstreamException hierarchy, AppNotFoundException,
│   │                      gateways (AppSearchGateway, AppDetailsGateway), services, cache configuration
│   └── storefront/        StorefrontPolicy, SupportedStorefronts
├── integration/apple/     SearchGatewayAdapter, LookupGatewayAdapter (implement the gateways; 429 guard;
│                          one outcome metric and log line per logical call), ItunesSearchClient, MzLookupClient
│                          (HTTP, @Retryable), raw Apple records, pure mappers, RestClient beans, AppleProperties,
│                          SearchRateLimitGuard, SearchBudget (outbound token bucket), AppleCircuitBreakers
├── auth/                  TokenController, TokenService, SecurityConfig, AuthProperties
└── observability/         CorrelationIdFilter, MDC context propagation for loader threads, MetricNames
```

### Dependency rules (enforced by an ArchUnit test)

| Package | May depend on | Must not depend on |
|---|---|---|
| `catalog` | JDK, Spring core, Caffeine, Micrometer, `observability` | `api`, `integration`, `auth` |
| `integration.apple` | `catalog`, `observability` | `api`, `auth` |
| `api` | `catalog`, `observability` | `integration`, `auth` |
| `auth` | `api` (only the `ProblemDetails` factory), `observability` | `catalog`, `integration` |

- **Controllers never see Apple JSON.** Services never see HTTP status codes.
- **Mappers are pure functions** (raw Apple record → domain record), with no Spring and no I/O. They are the most thoroughly tested code.
- **Configuration lives next to its feature**, in validated `@ConfigurationProperties` records under the `appstore.*` namespace ([ADR-0033](../adr/0033-configuration-namespace.md)). There is no global `config` package.
- **Java conventions:** records for DTOs and domain objects; `Optional` only as a return type; no Lombok.

### Upstream exception hierarchy (in `catalog`)

| Exception | Raised when |
|---|---|
| `UpstreamConnectException` | Connect timeout, connection refused, DNS failure. **The only retried type.** |
| `UpstreamReadTimeoutException` | Read timeout |
| `UpstreamServerErrorException` | Apple 5xx |
| `UpstreamRateLimitedException(Duration retryAfter)` | Apple 429 or the local short-circuit |
| `UpstreamContractException` | Unexpected 4xx or malformed payload |
| `StorefrontNotServedException` | Apple rejects or silently replaces the storefront |

All six extend the sealed `UpstreamException`. `AppNotFoundException` is separate: it means an empty lookup result, not an upstream failure. `catalog.storefront` adds two input errors thrown before any Apple call: `InvalidCountryCodeException` (not a country code) and `UnsupportedStorefrontException` (a country without an App Store storefront). How each one maps to an HTTP response is in [`error-handling.md`](error-handling.md).

## Request flow

**Search:** `GET /api/v1/apps?term&cc&limit`
1. The controller validates the parameters.
2. `StorefrontPolicy` checks `cc`.
3. `AppSearchService` looks up the `app-search` async cache.
4. On a miss, the loader calls `AppSearchGateway`, implemented by `SearchGatewayAdapter`.
5. The adapter checks the 429 guard, calls `ItunesSearchClient` (timeouts, retry on connection failures), maps the response, and records one outcome metric and log line.
6. The controller maps the domain records to DTOs.

**Details:** `GET /api/v1/apps/{id}?cc&l&platform`
1. The controller validates the parameters.
2. `StorefrontPolicy` checks `cc`.
3. `AppDetailsService` looks up the `app-details` async cache, which holds a `LookupResult`.
4. On a miss, the loader calls `AppDetailsGateway`, implemented by `LookupGatewayAdapter`.
5. The adapter calls `MzLookupClient`, checks the storefront Apple served, maps the response, and records one outcome metric and log line.
6. A `NotFound` result becomes `AppNotFoundException` and then a 404.

## Technology stack

| Area | Choice | ADR |
|---|---|---|
| Language / runtime | Java 25 LTS (Gradle toolchain), virtual threads | [0002](../adr/0002-java-25-lts.md), [0030](../adr/0030-details-cache-and-bounded-retry.md) |
| Framework | Spring Boot 4.1.x (Spring Framework 7.0, Spring Security 7.1, Jackson 3) | [0003](../adr/0003-spring-boot-4-1-with-restclient.md) |
| HTTP client | `RestClient` from the Boot-configured builder; connect/read timeouts configurable | [0003](../adr/0003-spring-boot-4-1-with-restclient.md) |
| Caching | Caffeine `AsyncCache` used directly (no Spring cache abstraction); loader threads get the MDC via Micrometer context propagation | [0030](../adr/0030-details-cache-and-bounded-retry.md) |
| Resilience | Spring Framework 7 `@Retryable` (with `timeout`), 429 short-circuit, outbound limiter on Search with a configurable budget | [0030](../adr/0030-details-cache-and-bounded-retry.md), [0031](../adr/0031-rate-limit-short-circuit.md), [0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md) |
| Auth | Spring Security OAuth2 resource server, self-issued HS256 JWT | [0004](../adr/0004-self-issued-hs256-jwt-no-fallback.md), [0034](../adr/0034-security-and-privacy-hardening.md) |
| API docs | springdoc-openapi 3.1.x | [0001](../adr/0001-server-only-scope-with-spring-boot.md) |
| Observability | Actuator on port 8081, Micrometer + Prometheus registry, ECS structured logs; OpenTelemetry as a stretch goal | [0027](../adr/0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md), [0032](../adr/0032-management-port-and-probes.md) |
| Tests | JUnit Jupiter 6 (Boot-managed), AssertJ, `MockMvcTester`, WireMock (`wiremock-spring-boot`), ArchUnit (`archunit-junit6`) | [0028](../adr/0028-package-boundaries-and-ports.md) |
| Build quality | Spotless + palantir-java-format, version catalog, Dependabot, GitHub Actions | [0035](../adr/0035-continuous-integration.md), [0036](../adr/0036-formatting-version-catalog-and-updates.md) |
| Frontend | Angular 22 + Angular Material, zoneless, signals, Vitest; Node 24 LTS | [0017](../adr/0017-add-an-angular-web-client-time-boxed.md), [0021](../adr/0021-node-24-lts-via-nvm.md) |
| Containers | Temurin 25 JRE (non-root), `nginxinc/nginx-unprivileged`, Docker Compose | [0006](../adr/0006-docker-with-no-default-secrets.md), [0034](../adr/0034-security-and-privacy-hardening.md) |

Exact versions live in `backend/gradle/libs.versions.toml`, `frontend/package.json`, the Dockerfiles and the workflow files, all kept up to date by Dependabot.

## Extending the service

- **A second media type (e.g. books):** a sibling feature (`/api/v1/books`) with its own domain records and mappers. It reuses `MzLookupClient`, which returns the raw lookup result, and chooses the mapper by `kind`.
- **A second upstream:** a new adapter package under `integration/` that implements the existing gateways.
- **Don't build generic provider abstractions** until a second implementation actually exists.
