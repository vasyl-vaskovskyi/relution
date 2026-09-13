# ADR-0027: Observability: logs and metrics in the app, OpenTelemetry + Grafana LGTM as a stretch goal

- **Status:** Accepted; amended by [ADR-0033](0033-configuration-namespace.md) (Boot-native OTLP properties, variable names), [ADR-0034](0034-security-and-privacy-hardening.md) (trace id as correlation id) and [ADR-0038](0038-alert-rules.md) (alert rules, `appstore.` metric prefix)
- **Date:** 2026-09-13 (prep)

- **Context:**
  - The service depends on two external APIs, one of them rate-limited per IP and one Legacy. Operators need to see upstream latency and outcomes, 429s, the cache hit rate and storefront allowlist mismatches.
  - Monitoring should be easy to integrate and to install locally.
- **Options:**
  1. **App-only (Level 1):** Spring Boot structured JSON logs, correlation ids, Actuator and Micrometer metrics.
  2. **Level 1 + OpenTelemetry export** (`spring-boot-starter-opentelemetry`, new in Boot 4) to **`grafana/otel-lgtm`**: a single container with an OTel Collector, Prometheus, Loki, Tempo and Grafana.
  3. Prometheus + Grafana as separate containers (metrics only, more setup).
  4. Sentry: an external account; data leaves our infrastructure; Boot 4 support unverified.
  5. ELK/OpenSearch: too heavy to run for a one-day project.
- **Decision:** **Level 1 is core**, built together with the features. **Level 2 is a stretch goal:** OTLP export plus `otel-lgtm` through an optional `compose.observability.yml` override. It is only started after all planned blocks are done. The details are in docs/operations/observability.md.
- **Consequences:**
  - The metrics that were previously a Part 3 stretch goal (`apple.upstream.requests`, `cache.gets`, `storefront.allowlist.mismatch`) become core.
  - OTLP export is off unless `APP_OTLP_ENDPOINT` is set, so the app never depends on the monitoring stack.
  - The setup is vendor-neutral: production would only change the endpoint.
  - `otel-lgtm` is explicitly meant for dev, demo and testing, with Grafana's default `admin/admin` login. This goes into the honest assessment.
  - Artifact and property names for Boot 4's OpenTelemetry support must be verified when implementing.
  - Sources:
    - [Spring blog: OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
    - [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm)
