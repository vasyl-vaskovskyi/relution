# Deployment

## Images

| Image | Build | Runtime |
|---|---|---|
| `backend/Dockerfile` | `eclipse-temurin:25-jdk` (pinned, Ubuntu-based) runs `./gradlew bootJar` | `eclipse-temurin:25-jre` (pinned, **Ubuntu-based, not `-alpine`**: the healthcheck needs bash), non-root user, layered jar, `EXPOSE 8080 8081`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` |
| `frontend/Dockerfile` | `node:24-alpine` (pinned) runs `npm ci && npm run build -- --configuration ${ANGULAR_CONFIGURATION}` (default `production`) | `nginxinc/nginx-unprivileged` stable alpine (pinned, e.g. `1.30.4-alpine`), listens on 8080, uses `frontend/nginx.conf` |

Dependabot updates the pinned tags. Nothing is pushed from CI.

## Compose (local)

`docker-compose.yml`:

| Service | Ports (host → container) | Notes |
|---|---|---|
| `app` | `127.0.0.1:8080 → 8080`, `127.0.0.1:8081 → 8081` | `env_file: .env`; `environment: LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs, LOGGING_STRUCTURED_ECS_SERVICE_ENVIRONMENT=local`; readiness healthcheck (below) |
| `frontend` | `127.0.0.1:4200 → 8080` | Build arg `ANGULAR_CONFIGURATION=demo`; `depends_on: app: condition: service_healthy` |

- **No secret defaults.** Run `cp .env.example .env` first ([`configuration.md`](configuration.md)).
- **One command:** `docker compose up --build`.

### Health checks

- **`app`:** the Temurin JRE image has neither `curl` nor `wget`, so the check uses bash's `/dev/tcp` against the management port (*verify* on the day):
  ```yaml
  test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
  ```
- **`frontend`:** `wget -q -O /dev/null http://127.0.0.1:8080/`, using BusyBox `wget` in the alpine image.

## nginx (`frontend/nginx.conf`)

- **Single-page app:** `try_files $uri /index.html`.
- **Proxy:** `location /api/` and `location /auth/` proxy to `http://app:8080`, forwarding `X-Correlation-Id` and `traceparent`.
- **Timeouts:** `proxy_read_timeout 15s`, above the backend's theoretical worst case of about 12 s ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#retry)).
- **Access log without query strings**, because search terms must not be logged ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)):
  ```nginx
  log_format appstore '$remote_addr [$time_local] "$request_method $uri $server_protocol" '
                      '$status $body_bytes_sent $request_time "$http_x_correlation_id"';
  access_log /dev/stdout appstore;
  ```
- **Security headers** as listed in [`../architecture/security.md`](../architecture/security.md#frontend).
- **The management port is never proxied.**

## Optional observability stack

```bash
docker compose -f docker-compose.yml -f compose.observability.yml up --build
```

`compose.observability.yml`:
- adds `otel-lgtm` (`grafana/otel-lgtm`, pinned tag), publishing only `127.0.0.1:3000` (Grafana);
- sets the OTLP variables on `app` ([`configuration.md`](configuration.md));
- takes the Grafana password from `APPSTORE_GRAFANA_ADMIN_PASSWORD`.

The image is intended for development and demos only ([`observability.md`](observability.md)).

## Production notes

- **Probes:** liveness at `/livez` and readiness at `/readyz` on port 8080, or `/actuator/health/liveness|readiness` on 8081. Neither depends on Apple.
- **Network:** don't route the management port through the public ingress. Scrape `/actuator/prometheus` on 8081 from inside the cluster (job `appstore-management`).
- **Profile:** run with `SPRING_PROFILES_ACTIVE=prod`, which disables the API docs.
- **Ingress:** add a rate limit for `/auth/token` ([`../architecture/security.md`](../architecture/security.md#known-gaps)).
- **Multiple replicas:** caches and the 429 guard are per instance, and Apple's budget is shared across the egress IP ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#known-limits)).
