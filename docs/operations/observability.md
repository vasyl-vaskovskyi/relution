# Observability

There are two levels ([ADR-0027](../adr/0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md)). **Level 1 is core**, built together with the features. **Level 2** (OpenTelemetry export plus Grafana LGTM) is a stretch goal.

## Level 1: inside the service

### Structured logs

- **Format:** ECS JSON in containers (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), plain text for local runs.
- **ECS service fields:** `service.name=appstore`; `service.environment` comes from the compose file.
- **MDC:**
  - `correlationId` on every line;
  - `clientId` (JWT `sub`) on authenticated requests;
  - `traceId` and `spanId` when tracing is enabled.
  - The MDC is propagated to cache loader threads ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#caches)).
- **Per logical upstream call:** one INFO line from the gateway adapter, after retries, with `api`, `outcome`, `status`, `durationMs`, and `termLength` (search) or `id` (lookup).
- **WARN:** a storefront missing from the allowlist, Apple 429, timeouts, Apple 5xx.
- **ERROR:** an outdated allowlist, contract errors, unexpected exceptions.
- **What is never logged** is defined in [`../architecture/security.md`](../architecture/security.md#logging-and-privacy).

### Health

| Endpoint | Port | Contents |
|---|---|---|
| `/livez`, `/readyz` | 8080 | Liveness and readiness; no Apple dependency |
| `/actuator/health`, `/actuator/health/{liveness,readiness}` | 8081 | Details never shown |
| `/actuator/info` | 8081 | Build info (`springBoot { buildInfo() }`) |

### Metrics

Exposed on `8081/actuator/metrics` and `8081/actuator/prometheus`.

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `http.server.requests` | Timer | Spring defaults (URI template, no query) | Incoming requests |
| `appstore.apple.requests` | Timer with percentiles histogram | `api=search\|lookup`, `outcome=success\|empty\|not_found\|storefront_rejected\|rate_limited\|short_circuited\|connect_error\|read_timeout\|server_error\|contract_error` | One sample per logical upstream call or short-circuit, recorded after retries |
| `appstore.apple.mapping.missing_field` | Counter | `api`, `field` | Drift signal ([ADR-0037](../adr/0037-legacy-api-drift-detection.md)) |
| `appstore.storefront.allowlist.mismatch` | Counter | `direction=missing\|outdated` | Allowlist maintenance signal |
| `cache.gets` (and the other Caffeine cache metrics) | Counter | `cache=app-search\|app-details`, `result=hit\|miss` | Cache effectiveness |

- **Prometheus names:** `appstore_apple_requests_seconds_count`, `appstore_apple_requests_seconds_bucket` and so on.
- **Cardinality rules:** never use `term`, `cc`, `id`, client ids or free text as tag values. Every tag listed above has a fixed, small set of values.
- **Tests:**
  - WireMock client tests assert the `outcome` tag (one sample per logical call), using `SimpleMeterRegistry`.
  - The storefront policy test asserts the mismatch counter.
  - A web-layer test asserts that the correlation id is echoed.
  - A log-capture test asserts that no secret is logged.

### Alert rules

See [ADR-0038](../adr/0038-alert-rules.md). The rules live in `ops/alerts.yml`, and each links to a section of [`runbook.md`](runbook.md).

| Alert | Condition (sketch) | Severity |
|---|---|---|
| `AppleSearchRateLimited` | `sum(rate(appstore_apple_requests_seconds_count{api="search",outcome=~"rate_limited\|short_circuited"}[5m])) > 0.1` for 10 m | Warning |
| `AppleContractErrors` | `increase(appstore_apple_requests_seconds_count{outcome="contract_error"}[15m]) > 0` | Critical |
| `AppleMissingFields` | `increase(appstore_apple_mapping_missing_field_total[1h]) > 0` | Warning |
| `StorefrontAllowlistOutdated` | `increase(appstore_storefront_allowlist_mismatch_total{direction="outdated"}[1h]) > 0` | Warning |
| `AppleLatencyHigh` | `histogram_quantile(0.95, sum by (le, api) (rate(appstore_apple_requests_seconds_bucket[5m]))) > 3` for 10 m | Warning |
| `AppstoreDown` | `up{job="appstore-management"} == 0` for 5 m (the management port can't be scraped) | Critical |

## Level 2: OpenTelemetry + Grafana LGTM (stretch goal)

- **Dependency:** `org.springframework.boot:spring-boot-starter-opentelemetry` (Micrometer Tracing bridge plus OTLP exporters). Exporting logs may also need the OpenTelemetry Logback appender; ask before adding it.
- **Default:** all exports are disabled in `application.yml`. `compose.observability.yml` enables them, with endpoints and 100 % sampling ([`configuration.md`](configuration.md)).
- **Trace context:** W3C `traceparent`, Spring Boot's default. nginx forwards it, and the trace id becomes the correlation id ([`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id)).
- **Privacy:** query strings are removed from span attributes ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)).
- **Stack:** `grafana/otel-lgtm`, a single container with an OpenTelemetry Collector, Prometheus, Loki, Tempo, Pyroscope and Grafana.
  - Only Grafana is published, on `127.0.0.1:3000`.
  - The admin password comes from `.env`.
  - Grafana describes the image as intended for **development, demo and testing**. In production, point the OTLP properties at a real backend; no code change is needed.
- **Scope:** no committed dashboards (use Grafana Explore) and no automated tests.
- **Demo:**
  1. Search in the UI.
  2. In Tempo, show the server span and the outgoing `RestClient` span.
  3. In Loki, filter by `traceId`.
  4. In Prometheus, show `appstore_apple_requests_seconds_count` by `outcome` and the cache hit ratio.
