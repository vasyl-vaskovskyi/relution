# Configuration

All application settings live under `appstore.*` and bind to validated `@ConfigurationProperties` records ([ADR-0033](../adr/0033-configuration-namespace.md)). This file is the single home for property names, environment variables and defaults. Once the code exists, `.env.example` mirrors the variables.

**Naming rule:** every path segment is a single lowercase word without dashes (`appstore.apple.timeout.read`). The environment variable is then Spring's canonical form: dots become underscores and everything is upper case (`APPSTORE_APPLE_TIMEOUT_READ`), so no legacy name mapping is involved.

## Required

| Property | Environment variable | Description |
|---|---|---|
| `appstore.auth.jwt.secret` | `APPSTORE_AUTH_JWT_SECRET` | HS256 signing key, Base64, at least 32 bytes after decoding (`openssl rand -base64 32`) |
| `appstore.auth.client.id` | `APPSTORE_AUTH_CLIENT_ID` | Client id for `POST /auth/token` and the web client login |
| `appstore.auth.client.secret` | `APPSTORE_AUTH_CLIENT_SECRET` | Client secret for `POST /auth/token` and the web client login |

If one of these is missing or invalid, the application refuses to start. An `ApplicationContextRunner` test covers this.

## Optional

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `appstore.auth.jwt.ttl` | `APPSTORE_AUTH_JWT_TTL` | `PT15M` | Access-token lifetime (validated maximum `PT1H`) |
| `appstore.auth.jwt.issuer` | `APPSTORE_AUTH_JWT_ISSUER` | `appstore` | JWT `iss` |
| `appstore.auth.jwt.audience` | `APPSTORE_AUTH_JWT_AUDIENCE` | `appstore-api` | JWT `aud` |
| `appstore.apple.search.url` | `APPSTORE_APPLE_SEARCH_URL` | `https://itunes.apple.com` | Search API base URL (tests point it at WireMock) |
| `appstore.apple.lookup.url` | `APPSTORE_APPLE_LOOKUP_URL` | `https://uclient-api.itunes.apple.com` | Lookup API base URL |
| `appstore.apple.timeout.connect` | `APPSTORE_APPLE_TIMEOUT_CONNECT` | `PT2S` | Connect timeout |
| `appstore.apple.timeout.read` | `APPSTORE_APPLE_TIMEOUT_READ` | `PT5S` | Read timeout |
| `appstore.apple.retry.max` | `APPSTORE_APPLE_RETRY_MAX` | `2` | Retries for connection failures |
| `appstore.apple.retry.timeout` | `APPSTORE_APPLE_RETRY_TIMEOUT` | `PT8S` | No new attempt starts after this time (a running attempt isn't aborted) |
| `appstore.apple.retryafter.max` | `APPSTORE_APPLE_RETRYAFTER_MAX` | `PT5M` | Upper bound for Apple's `Retry-After` in the 429 guard |
| `appstore.apple.circuit.failurerate` | `APPSTORE_APPLE_CIRCUIT_FAILURERATE` | `50` | Percentage of failed calls in the window that opens a circuit breaker (1–100) |
| `appstore.apple.circuit.window` | `APPSTORE_APPLE_CIRCUIT_WINDOW` | `20` | Number of recent calls the failure rate is computed over |
| `appstore.apple.circuit.minimumcalls` | `APPSTORE_APPLE_CIRCUIT_MINIMUMCALLS` | `10` | Calls needed before the failure rate is evaluated |
| `appstore.apple.circuit.open` | `APPSTORE_APPLE_CIRCUIT_OPEN` | `PT30S` | How long a breaker stays open (also the `Retry-After` while open) |
| `appstore.apple.circuit.halfopencalls` | `APPSTORE_APPLE_CIRCUIT_HALFOPENCALLS` | `3` | Test calls allowed while half-open |
| `appstore.apple.search.budget` | `APPSTORE_APPLE_SEARCH_BUDGET` | `20` | Outbound Search calls per minute allowed by the outbound limiter. Raise it when more requests are bought ([ADR-0045](../adr/0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md)) |
| `appstore.cache.search.ttl` | `APPSTORE_CACHE_SEARCH_TTL` | `PT10M` | `app-search` time to live |
| `appstore.cache.search.size` | `APPSTORE_CACHE_SEARCH_SIZE` | `1000` | `app-search` maximum entries |
| `appstore.cache.details.ttl` | `APPSTORE_CACHE_DETAILS_TTL` | `PT15M` | `app-details` time to live for `Found` |
| `appstore.cache.notfound.ttl` | `APPSTORE_CACHE_NOTFOUND_TTL` | `PT60S` | `app-details` time to live for `NotFound` |
| `appstore.cache.details.size` | `APPSTORE_CACHE_DETAILS_SIZE` | `5000` | `app-details` maximum entries |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | (none) | `prod` activates `application-prod.yml` |

## Profiles

| Profile | File | Differences |
|---|---|---|
| default (local, tests) | `application.yml` | API docs and Swagger UI enabled |
| `prod` | `application-prod.yml` | `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` |

## Set by the compose files (not secrets, not in `.env`)

| Variable | Value | Where |
|---|---|---|
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` | `docker-compose.yml`: JSON logs in containers, plain text for local runs |
| `LOGGING_STRUCTURED_ECS_SERVICE_ENVIRONMENT` | `local` | `docker-compose.yml` |
| `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED`, `MANAGEMENT_OTLP_METRICS_EXPORT_URL` | `true`, `http://otel-lgtm:4318/v1/metrics` | `compose.observability.yml` |
| `MANAGEMENT_TRACING_EXPORT_ENABLED`, `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`, `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `true`, `http://otel-lgtm:4318/v1/traces`, `1.0` | `compose.observability.yml` |
| `MANAGEMENT_LOGGING_EXPORT_ENABLED`, `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT` | `true`, `http://otel-lgtm:4318/v1/logs` | `compose.observability.yml` |

The OTLP property names come from the Spring Boot 4.1 docs (checked 2026-09-13). `application.yml` disables every OTLP export by default.

## Only used by the observability stack

| Variable | Description |
|---|---|
| `APPSTORE_GRAFANA_ADMIN_PASSWORD` | Grafana admin password. `compose.observability.yml` passes it on as `GF_SECURITY_ADMIN_PASSWORD` |

## Fixed in `application.yml` and the build

| Setting | Value | Why |
|---|---|---|
| `server.port` | `8080` | Public API |
| `management.server.port` | `8081` | Management port ([ADR-0032](../adr/0032-management-port-and-probes.md)) |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | The only exposed endpoints |
| `management.endpoint.health.probes.add-additional-paths` | `true` | `/livez` and `/readyz` on 8080 |
| `management.metrics.distribution.percentiles-histogram.appstore.apple.requests` | `true` | Needed for the p95 latency alert |
| `spring.threads.virtual.enabled` | `true` | Virtual threads for request handling |
| `logging.structured.ecs.service.name` | `appstore` | ECS `service.name` |
| `springBoot { buildInfo() }` in `build.gradle.kts` | — | Build info for `/actuator/info` |
