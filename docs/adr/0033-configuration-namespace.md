# ADR-0033: One configuration namespace `appstore.*`

- **Status:** Accepted; amends [ADR-0006](0006-docker-with-no-default-secrets.md), [ADR-0018](0018-frontend-auth-login-form-token-in-memory.md) and [ADR-0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md)
- **Date:** 2026-09-13 (prep)

- **Context:** Environment variables used two generic prefixes (`APP_*`, `APPLE_*`) that would collide in a large product. Timeouts, TTLs and retry settings were hard-coded. A custom `APP_OTLP_ENDPOINT` duplicated Spring Boot's native OTLP properties.
- **Options:** keep ad-hoc names; one application prefix with typed properties.
- **Decision:**
  - **Prefix:** all application settings live under `appstore.*` and bind to validated `@ConfigurationProperties` records (`appstore.auth.*`, `appstore.apple.*`, `appstore.cache.*`).
  - **Naming:** every path segment is a single lowercase word without dashes (e.g. `appstore.apple.timeout.read`). Each environment variable is then Spring's canonical form (`APPSTORE_APPLE_TIMEOUT_READ`), with no legacy mapping involved.
  - **Tunable settings:** timeouts, retry, cache TTLs and sizes are configurable, so tests can use short durations.
  - **OpenTelemetry:** uses Boot's native properties (`management.otlp.metrics.export.*`, `management.opentelemetry.tracing.export.otlp.endpoint`, `management.opentelemetry.logging.export.otlp.endpoint`), disabled by default and enabled only by the observability compose override.
  - **Fail-fast:** a test (`ApplicationContextRunner`) proves that a missing or short secret stops the context.
  - The full list is in `docs/operations/configuration.md`.
- **Consequences:** One predictable namespace, no collisions, no custom indirection. Renaming now costs nothing because no code exists yet.
