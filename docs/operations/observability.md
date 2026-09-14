# Observability

There are two levels ([ADR-0027](../adr/0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md)). **Level 1 is core**, built together with the features. **Level 2** (OpenTelemetry export plus Grafana LGTM) is a stretch goal.

## Level 1: inside the service

### Structured logs

- **Format:** ECS JSON in containers (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), plain text for local runs.
- **ECS service fields:** `service.name=appstore`; `service.environment` comes from the compose file.
- **MDC:**
  - `correlationId` on every line;
  - `clientId` (JWT `sub`) on authenticated requests;
  - `traceId` and `spanId` on request lines. Spring Boot's OpenTelemetry bridge creates spans even while tracing export is off (observed 2026-09-14), but only Level 2 exports them, so at Level 1 `correlationId` is the id to search for;
  - with tracing (Level 2): `clientCorrelationId` when the client sent a valid `X-Correlation-Id` ([`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id)).
  - `correlationId`, `clientCorrelationId` and `clientId` are propagated to cache loader threads, together with the current observation, so the Apple client span stays in the request's trace ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#caches)).
- **Per logical upstream call:** one INFO line from the gateway adapter, after retries, with `api`, `outcome`, `status`, `durationMs`, and `termLength` (search) or `id` (lookup).
- **WARN:** a storefront missing from the allowlist, Apple 429, an exhausted outbound budget, timeouts, Apple 5xx.
- **INFO and DEBUG in the upstream line:** a storefront Apple rejected is INFO (the policy logs the ERROR); the 429 short-circuit is DEBUG, because the WARN was already logged when Apple answered 429.
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
| `appstore.apple.requests` | Timer with percentiles histogram | `api=search\|lookup`, `outcome=success\|empty\|not_found\|storefront_rejected\|rate_limited\|short_circuited\|budget_exhausted\|circuit_open\|connect_error\|read_timeout\|server_error\|contract_error` | One sample per logical upstream call, short-circuit or exhausted budget, recorded after retries |
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
| `AppleSearchRateLimited` | `sum(rate(appstore_apple_requests_seconds_count{api="search",outcome=~"rate_limited\|short_circuited\|budget_exhausted"}[5m])) > 0.1` for 10 m | Warning |
| `AppleContractErrors` | `increase(appstore_apple_requests_seconds_count{outcome="contract_error"}[15m]) > 0` | Critical |
| `AppleMissingFields` | `increase(appstore_apple_mapping_missing_field_total[1h]) > 0` | Warning |
| `StorefrontAllowlistOutdated` | `increase(appstore_storefront_allowlist_mismatch_total{direction="outdated"}[1h]) > 0` | Warning |
| `AppleLatencyHigh` | `histogram_quantile(0.95, sum by (le, api) (rate(appstore_apple_requests_seconds_bucket[5m]))) > 3` for 10 m | Warning |
| `AppstoreDown` | `up{job="appstore-management"} == 0` for 5 m (the management port can't be scraped) | Critical |
| `AppleCircuitOpen` | `max by (name) (resilience4j_circuitbreaker_state{state="open"}) == 1` for 5 m | Warning |

**Rule tests** ([ADR-0050](../adr/0050-alert-rule-unit-tests-with-promtool.md)): `ops/alerts.test.yml` holds promtool unit tests with a firing and a non-firing case for every rule above, fed with the metric names the rules use. The CI job `alerts` runs `promtool check rules` and `promtool test rules` ([`../development/tooling.md`](../development/tooling.md#continuous-integration-adr-0035)). A new or changed rule needs matching test cases in the same commit. Expected annotations are compared as rendered, so a change to a rule's text changes its test too.

Circuit breaker metrics come from Resilience4j's Micrometer binding: `resilience4j_circuitbreaker_state{name, state}`, `resilience4j_circuitbreaker_calls_seconds_count{name, kind}` and `resilience4j_circuitbreaker_failure_rate{name}` ([ADR-0047](../adr/0047-circuit-breaker-per-apple-api.md)). `name` is `search` or `lookup`.

## Level 2: OpenTelemetry + Grafana LGTM (stretch goal)

- **Dependency:** `org.springframework.boot:spring-boot-starter-opentelemetry` (Micrometer Tracing bridge, OpenTelemetry SDK, OTLP span exporter and the Micrometer OTLP meter registry).
- **Signals exported:** traces and metrics. **Logs are not exported:** Boot's OTLP log exporter only receives Logback events through the OpenTelemetry Logback appender, which is not an approved dependency. Container logs stay the log source (`docker compose logs app`), and their `traceId` field links them to Tempo.
- **Default:** all exports are disabled in `application.yml`, and sampling is 10 %. `compose.observability.yml` enables trace and metric export, with endpoints and 100 % sampling ([`configuration.md`](configuration.md#set-by-the-compose-files-not-secrets-not-in-env)).
- **Trace context:** W3C `traceparent`, Spring Boot's default, consumed only while tracing export is enabled. nginx forwards it, and the trace id becomes the correlation id ([`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id)).
- **Cache loaders:** the current observation is propagated to loader threads, so the outgoing `RestClient` span stays in the request's trace ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#caches)).
- **Privacy:** query strings are removed from span attributes ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)).
- **Stack:** `grafana/otel-lgtm`, a single container with an OpenTelemetry Collector, Prometheus, Loki, Tempo, Pyroscope and Grafana.
  - Only Grafana is published, on `127.0.0.1:3000`.
  - The admin password comes from `.env.observability`, which the app container never receives ([`deployment.md`](deployment.md#optional-observability-stack)).
  - Grafana describes the image as intended for **development, demo and testing**. In production, point the OTLP properties at a real backend; no code change is needed.
- **Scope:** no committed dashboards (use Grafana Explore). Tests cover the privacy filter and the trace id as correlation id with an in-memory span exporter, not the compose stack ([`../development/testing.md`](../development/testing.md)).
- **Demo:**
  1. Search in the UI.
  2. In Tempo, show the server span and the outgoing `RestClient` span.
  3. Copy the trace id from the `X-Correlation-Id` response header and find the request's log lines with `docker compose logs app | grep <trace id>`.
  4. In Prometheus, show `appstore_apple_requests_milliseconds_count` by `outcome` and the cache hit ratio (`cache_gets_total`). Metrics pushed over OTLP use milliseconds, the OTLP registry's base time unit (observed 2026-09-14). The alert rules keep the scraped `_seconds` names from `/actuator/prometheus`.
- **Verified end to end (2026-09-14):** one search produced a single trace in Tempo with the server span (`http.url=/api/v1/apps`), Spring Security's internal spans and the Apple client span (`http.url=https://itunes.apple.com/search`); the term was in no span. The response's `X-Correlation-Id` was the trace id.
