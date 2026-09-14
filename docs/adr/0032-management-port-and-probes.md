# ADR-0032: Separate management port, probes, restricted exposure

- **Status:** Accepted; amended by [ADR-0044](0044-secure-the-management-port-as-a-whole.md) (the management chain matches the whole port)
- **Date:** 2026-09-13 (prep)

- **Context:** Actuator and Swagger UI shared the public port. Nothing restricted endpoints such as `env` or `heapdump`, and a heap dump would expose the JWT secret. `/actuator/metrics` JSON is not scrapeable by Prometheus.
- **Options:** keep one port and protect endpoints with the JWT; separate management port with an explicit exposure list.
- **Decision:**
  - **Port and exposure:** `management.server.port=8081`, with `management.endpoints.web.exposure.include=health,info,metrics,prometheus` (Prometheus via `io.micrometer:micrometer-registry-prometheus`).
  - **Health:**
    - `show-details` stays `never` (the Boot 4 default).
    - Liveness and readiness probes (on by default in Boot 4) never include Apple: an Apple outage must not restart or unready the service.
    - `management.endpoint.health.probes.add-additional-paths=true` also serves `/livez` and `/readyz` on the main port.
  - **Protection:** the management port is not authenticated. It is protected by the network: bound to `127.0.0.1` in compose, and never routed by nginx or an ingress.
  - **API docs:** `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are true locally and false in the `prod` profile.
  - **Tests:** `/actuator/env` returns 404 on the management port and 401 on the public port (catch-all `denyAll`).
- **Consequences:**
  - Operational endpoints can't be reached from the public API.
  - Adds one dependency (the Prometheus registry).
  - The compose healthcheck targets `8081/actuator/health/readiness`.
