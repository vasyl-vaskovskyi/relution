# ADR-0048: Level 2 observability: trace id only with export, no OTLP log export

- **Status:** Accepted; amends [ADR-0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md); amended by [ADR-0051](0051-export-application-logs-over-otlp.md) (logs are exported over OTLP)
- **Date:** 2026-09-14 (Discovery Day, stretch goal)

- **Context:**
  - Level 2 (OpenTelemetry starter, `compose.observability.yml` with `grafana/otel-lgtm`) was built as a stretch goal.
  - **Trace ids without export:** with the starter on the classpath, Boot creates spans with valid trace ids even when trace export is off. Using those ids as correlation ids would have changed the default (Level 1) behavior for every deployment.
  - **Log export:** Boot's OTLP log export needs the OpenTelemetry Logback appender, which was not an approved dependency.
  - **Loader threads:** cache loads ran as separate traces, so the Apple client span was not part of the request's trace.
- **Options:**
  1. Trace id is always the correlation id once the starter is present.
  2. Trace id is the correlation id only when `management.tracing.export.enabled=true`; otherwise keep the Level 1 rule.
  3. Export logs via the Logback appender, or keep logs on stdout and correlate by `traceId`.
- **Decision:**
  - **Option 2** for the correlation id. With export on, a valid incoming `X-Correlation-Id` is kept as `clientCorrelationId` ([`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id)). The filter runs right after Boot's observation filter and before Spring Security.
  - **No OTLP log export.** Logs stay ECS JSON on stdout and carry `traceId`/`spanId`; the demo finds a trace's logs with `docker compose logs app | grep <trace id>`.
  - **Loader executors propagate the current trace** as well as the MDC, so the Apple call is a child span of the request.
  - **Privacy:** an observation filter removes query strings from HTTP server and client URL values (only the client span actually carried the term).
- **Consequences:**
  - Default deployments behave as before; ECS logs now also show `traceId`/`spanId`.
  - Loki shows no application logs in the Grafana stack until the appender is approved.
  - Metrics exported over OTLP are named `appstore_apple_requests_milliseconds_*`; the committed alert rules keep the scraped `_seconds_*` names ([`../operations/observability.md`](../operations/observability.md)).
  - **Open follow-ups:** confirm that Dependabot's `docker-compose` ecosystem picks up `compose.observability.yml` (otherwise the `otel-lgtm` tag must be updated by hand); the app container also receives `APPSTORE_GRAFANA_ADMIN_PASSWORD` from `.env`, which is never logged or exposed but could be split into a separate env file.
