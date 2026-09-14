# ADR-0051: Export application logs over OTLP in the Level 2 stack

- **Status:** Accepted; amends [ADR-0048](0048-level-2-observability-trace-id-rule-and-no-log-export.md) (logs are exported over OTLP)
- **Date:** 2026-09-14 (Discovery Day, stretch goal)

- **Context:**
  - ADR-0048 kept logs on stdout because Boot's OTLP log exporter only receives Logback events through the OpenTelemetry Logback appender, which wasn't approved. Loki in the Grafana stack showed no application logs.
  - The maintainer approved `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`.
  - **Spring Boot 4.1.1** (reference docs, "Loggers > OpenTelemetry", and the configuration metadata, checked 2026-09-14): the exporter is created when `management.opentelemetry.logging.export.otlp.endpoint` is set and `management.logging.export.enabled` isn't `false`. The appender isn't part of Boot and isn't version-managed. The docs configure it in `logback-spring.xml` and call `OpenTelemetryAppender.install(openTelemetry)` from a bean.
  - Boot 4.1.1 manages `opentelemetry-api` 1.62.0. Appender 2.28.1-alpha is built against 1.62.0; the newest, 2.31.1-alpha, against 1.65.0, which Boot's dependency management would downgrade to 1.62.0 at runtime.
  - Console logs switch between plain text and ECS JSON through `logging.structured.format.console`. A `logback-spring.xml` would have to reproduce that choice, and a conditional in Logback XML needs Janino, which isn't a dependency.
- **Options:**
  1. `logback-spring.xml` that includes Boot's defaults and adds the appender (always attached, buffering events until `install`).
  2. Attach the appender programmatically from a bean, only when `management.logging.export.enabled=true`.
  3. Collect container stdout with a log shipper instead.
- **Decision:**
  - **Option 2.** `LogExportAppender` (package `observability`, `@ConditionalOnBooleanProperty("management.logging.export.enabled")`) creates the appender, gives it Boot's `OpenTelemetry` bean, starts it, adds it to the root logger and detaches it when the context closes. Boot's Logback configuration, including the console encoder, stays untouched; with export off (the default in `application.yml`) nothing is attached.
  - **Version:** pin `2.28.1-alpha` in the version catalog, the release that matches the `opentelemetry-api` Boot manages. Move to a newer appender together with a Boot upgrade that brings the matching API.
  - **No MDC, arguments, markers or key-value pairs are captured** (the appender's defaults). A record carries the formatted message, level, logger name, exception and the trace context. `correlationId` and `clientId` stay console-only; with tracing on, the correlation id is the trace id anyway.
  - **Enabled only by `compose.observability.yml`**, next to trace and metric export: `MANAGEMENT_LOGGING_EXPORT_ENABLED=true` and the endpoint `http://otel-lgtm:4318/v1/logs`.
  - Option 3 was rejected: another container and configuration for a demo stack, while Boot already exports.
- **Consequences:**
  - Loki receives the application's log records with `trace_id`, so Grafana can link logs and traces.
  - Events logged before the context has created the bean (early startup) are console-only.
  - The exported records are a second path for log content; `LogSafetyIntegrationTest` runs with export on and checks them against the binding privacy list ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)).
  - Dependabot proposes appender updates that may need a newer OpenTelemetry API than Boot manages; those updates wait for the Boot upgrade ([`../development/tooling.md`](../development/tooling.md)).
