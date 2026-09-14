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
| `appstore.auth.token.requests` | Counter | `outcome=issued\|rejected\|rate_limited` | `POST /auth/token` requests that reached the controller; `rate_limited` is the failed-attempt limit ([ADR-0049](../adr/0049-rate-limit-failed-token-requests-per-client-address.md)) |
| `cache.gets` (and the other Caffeine cache metrics) | Counter | `cache=app-search\|app-details`, `result=hit\|miss` | Cache effectiveness |

- **Prometheus names:** `appstore_apple_requests_seconds_count`, `appstore_apple_requests_seconds_bucket`, `appstore_auth_token_requests_total` and so on.
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

**Rule tests** ([ADR-0050](../adr/0050-alert-rule-unit-tests-with-promtool.md)): `ops/alerts.test.yml` holds promtool unit tests with a firing and a non-firing case for every rule above, fed with the metric names the rules use. The cases sit at each rule's boundaries (values just above and below the threshold, one evaluation before and at the end of a `for`, the last and first evaluation with the change inside an `increase` window), so changing a threshold, window, `for` or matched label makes a test fail. The CI job `alerts` runs `promtool check rules` and `promtool test rules` ([`../development/tooling.md`](../development/tooling.md#continuous-integration-adr-0035)). A new or changed rule needs matching test cases in the same commit. Expected annotations are compared as rendered, so a change to a rule's text changes its test too.

Circuit breaker metrics come from Resilience4j's Micrometer binding: `resilience4j_circuitbreaker_state{name, state}`, `resilience4j_circuitbreaker_calls_seconds_count{name, kind}` and `resilience4j_circuitbreaker_failure_rate{name}` ([ADR-0047](../adr/0047-circuit-breaker-per-apple-api.md)). `name` is `search` or `lookup`.

## Level 2: OpenTelemetry + Grafana LGTM (stretch goal)

- **Dependencies:** `org.springframework.boot:spring-boot-starter-opentelemetry` (Micrometer Tracing bridge, OpenTelemetry SDK, OTLP span, metric and log exporters) and the OpenTelemetry Logback appender ([ADR-0051](../adr/0051-export-application-logs-over-otlp.md)).
- **Signals exported:** traces, metrics and logs.
- **Logs:** with `management.logging.export.enabled=true`, `LogExportAppender` attaches the OpenTelemetry Logback appender to the root logger, and Boot batches the records to `management.opentelemetry.logging.export.otlp.endpoint`.
  - The records are the same events as the console lines: message, level, logger (`scope_name` in Loki), exception, and `trace_id`/`span_id` for lines logged inside a request.
  - No MDC attribute is exported, so `correlationId` and `clientId` stay console-only. With tracing on, the correlation id is the trace id.
  - Console logs are unchanged. Lines logged before the context has created the appender (early startup) are console-only.
- **Default:** all exports are disabled in `application.yml`, and sampling is 10 %. `compose.observability.yml` enables trace, metric and log export, with endpoints and 100 % sampling ([`configuration.md`](configuration.md#set-by-the-compose-files-not-secrets-not-in-env)).
- **Trace context:** W3C `traceparent`, Spring Boot's default, consumed only while tracing export is enabled. nginx forwards it, and the trace id becomes the correlation id ([`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id)).
- **Cache loaders:** the current observation is propagated to loader threads, so the outgoing `RestClient` span stays in the request's trace ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#caches)).
- **Privacy:** query strings are removed from span attributes; exported log records follow the same rules as console lines ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)).
- **Stack:** `grafana/otel-lgtm`, a single container with an OpenTelemetry Collector, Prometheus, Loki, Tempo, Pyroscope and Grafana.
  - Only Grafana is published, on `127.0.0.1:3000`.
  - The admin password comes from `.env.observability`, which the app container never receives ([`deployment.md`](deployment.md#optional-observability-stack)).
  - Grafana describes the image as intended for **development, demo and testing**. In production, point the OTLP properties at a real backend; no code change is needed.
- **Scope:** one committed dashboard ([below](#dashboard)); everything else through Grafana Explore. Tests cover the privacy filter and the trace id as correlation id with an in-memory span exporter, and the exported log records with an in-memory log record exporter, not the compose stack ([`../development/testing.md`](../development/testing.md)).
- **Demo:**
  1. Search in the UI.
  2. In Tempo, show the server span and the outgoing `RestClient` span.
  3. Copy the trace id from the `X-Correlation-Id` response header. In Explore → Loki, find the request's log lines with `{service_name="appstore"} | trace_id="<trace id>"` (the console alternative: `docker compose logs app | grep <trace id>`).
  4. In Prometheus, show `appstore_apple_requests_milliseconds_count` by `outcome` and the cache hit ratio (`cache_gets_total`). Metrics pushed over OTLP use milliseconds, the OTLP registry's base time unit (observed 2026-09-14). The alert rules keep the scraped `_seconds` names from `/actuator/prometheus`.
- **Verified end to end (2026-09-14):** one search produced a single trace in Tempo with the server span (`http.url=/api/v1/apps`), Spring Security's internal spans and the Apple client span (`http.url=https://itunes.apple.com/search`); the term was in no span. The response's `X-Correlation-Id` was the trace id.
- **Logs verified end to end (2026-09-14, `grafana/otel-lgtm:0.33.0`):**
  - Loki's only label is `service_name`; `trace_id`, `span_id`, `scope_name` and `severity_text` are structured metadata.
  - `{service_name="appstore"} | trace_id="<id>"` returned the search's upstream line (`apple call api=search … termLength=17`), and the rejected token request was exported as well.
  - No record contained the search term, the client secret, `Bearer` or a JWT, and no record carried an MDC key.

### Dashboard

The stack provisions one dashboard, **App Store service** (uid `appstore-service`), and Grafana opens on it. Grafana's own "RED Metrics" and "JVM Overview (OpenTelemetry)" dashboards stay available.

- **Files:** `ops/grafana/dashboards/appstore-service.json` and the provider `ops/grafana/provisioning/dashboards.yaml`, mounted read-only by `compose.observability.yml` ([`deployment.md`](deployment.md#optional-observability-stack)). UI edits aren't saved: change the dashboard in Grafana, export its JSON (Share → Export), replace the file and commit it. Grafana rereads the directory every 30 s.
- **Open it:** http://localhost:3000, logged in as `admin` with the password from `.env.observability`.
- **Panels and the OTLP metric names they use** (confirmed in the running stack on 2026-09-14; every panel returned data after one run of `scripts/demo-traffic.sh`):

| Row | Panels | Metrics |
|---|---|---|
| API | Requests by URI and status, error ratio, requests in range by status, mean and max latency by URI | `http_server_requests_milliseconds_count`, `_sum`, `http_server_requests_max_milliseconds` |
| API | Latency p50 and p95 | `traces_spanmetrics_latency_bucket{service="appstore", span_kind="SPAN_KIND_SERVER"}` (Tempo span metrics, seconds). The OTLP `http_server_requests_milliseconds_bucket` has only `le="+Inf"`, because the timer publishes no percentile histogram |
| Apple upstream | Calls by API and outcome, calls in range, rejected or failed calls, latency p50 and p95 by API | `appstore_apple_requests_milliseconds_count`, `appstore_apple_requests_milliseconds_bucket` |
| Apple upstream | Circuit breaker state (closed, half open, open) | `resilience4j_circuitbreaker_state{name, state}` |
| Caches and storefronts | Cache hit ratio over time and in range | `cache_gets_total{cache="app-search\|app-details", result}` |
| Caches and storefronts | Storefront allowlist mismatches in range | `appstore_storefront_allowlist_mismatch_total{direction}` |
| Traces | Recent server traces of `appstore` | Tempo, TraceQL `{resource.service.name="appstore" && kind=server}` |
| Logs | Log lines by level, application logs (newest first) | Loki, `sum by (severity_text) (count_over_time({service_name="appstore"} [$__auto]))` and `{service_name="appstore"}` |

- **One request's logs:** the logs panel shows all records; filter by trace in Explore as described in the demo above.
- **Freshness:** the app pushes metrics once a minute and Prometheus' `timeInterval` is 60 s, so rate panels use `$__rate_interval` (at least 4 minutes) and a fresh stack shows data after one or two exports.

### Demo traffic

`scripts/demo-traffic.sh` fills every panel with a small, mixed load. It reads `APPSTORE_AUTH_CLIENT_ID` and `APPSTORE_AUTH_CLIENT_SECRET` from the environment or `.env`, like `scripts/smoke.sh`, and never prints the token or the secret.

```bash
scripts/demo-traffic.sh                                   # 2 rounds, 70 s apart, against http://localhost:8080
ROUNDS=3 PAUSE=70 BASE_URL=http://localhost:8080 scripts/demo-traffic.sh
```

- **One round, 18 requests:** repeated searches for two terms plus one term without results (cache hits, outcomes `success` and `empty`); details for Pages (iOS and Mac), Final Cut Pro (Mac), 1234094465 (iOS; Mac gives 404) and the unknown id 1 (404, `not_found`); `cc=cu` on both endpoints (400, unsupported storefront, no Apple call), a search without `cc` (400) and one without a token (401).
- **Why two rounds:** a series pushed over OTLP first appears with its running total, so `rate` and `increase` can't see the burst that created it. The pause spans one export, and the second round uses another storefront (`us` after `de`, then `gb`) and other terms, so it reaches Apple again and shows up in the rate panels. The "in range" panels also count series that first appeared inside the range.
- **Apple budget:** at most 3 Search and 6 Lookup calls per round, so the default run makes 6 Search calls, far below the Search budget of 20 per minute. Rounds after the third repeat its requests and are served from the caches (search 10 minutes, details 15 minutes).
- **Output:** one summary line with the request count per HTTP status.
