# App Store Search Service

> **Status:** search, details, caching, resilience (retry, outbound budget, 429 short-circuit, circuit breaker), JWT authentication, OpenAPI, metrics, the Angular web client, the Docker images and `scripts/smoke.sh` all work end to end.

A Spring Boot service with two JSON endpoints: **search apps** in the Apple App Store and **look up app details**. It comes with an **Angular web client** that demonstrates both.

The service wraps two Apple APIs:
- the public [iTunes Search API](https://performance-partners.apple.com/search-api) for search;
- the [MZStorePlatform lookup](https://developer.apple.com/documentation/devicemanagement/getting-app-and-book-information-legacy) (marked Legacy by Apple) for details.

It turns Apple's responses into small, stable objects and returns upstream failures as RFC 9457 problem responses. It adds caching, bounded retry, protection against Apple's rate limit, JWT authentication, structured logs and metrics.

```
browser ──▶ frontend (nginx: Angular app, proxies /api and /auth)
                 └─▶ app (Spring Boot :8080) ──▶ itunes.apple.com / uclient-api.itunes.apple.com
curl / Bruno ─JWT─▶ app :8080            operators ──▶ app management :8081 (health, metrics)
```

---

## Quick start (Docker)

**Prerequisites:** Git and Docker with Compose v2.

```bash
git clone <repo-url> && cd <repo>
cp .env.example .env          # fill in the required values (see docs/operations/configuration.md)
docker compose up --build
```

`.env` is mandatory: Compose reads it (`env_file`) and fails without it, because the compose file contains no secrets. The app refuses to start without the required values. Generate the signing secret with `openssl rand -base64 32`. For a quick local test, uncomment the sample values under each required variable in `.env`; they are public, so never use them on a shared or deployed instance.

| What | URL (bound to 127.0.0.1) |
|---|---|
| Web client | http://localhost:4200. Log in with `APPSTORE_AUTH_CLIENT_ID` / `APPSTORE_AUTH_CLIENT_SECRET` from `.env` |
| API | http://localhost:8080 |
| Readiness | http://localhost:8080/readyz |
| Management (health, metrics, Prometheus) | http://localhost:8081/actuator |
| API docs (not in the `prod` profile) | http://localhost:8080/swagger-ui.html |

The local compose build of the web client uses the `demo` configuration, which logs every API call to the browser console. Production builds don't.

**Optional observability stack:** run `cp .env.observability.example .env.observability`, set `APPSTORE_GRAFANA_ADMIN_PASSWORD` there (not in `.env`, which the app container receives), then run `docker compose --env-file .env.observability -f docker-compose.yml -f compose.observability.yml up --build`. It exports traces and metrics to Grafana at http://localhost:3000 (demo use only; see [`docs/operations/observability.md`](docs/operations/observability.md#level-2-opentelemetry--grafana-lgtm-stretch-goal)).

## Local development

**Backend.** Any JDK that can run Gradle is enough; the Gradle toolchain downloads Java 25.

```bash
set -a; source .env; set +a
(cd backend && ./gradlew bootRun)
```

**Frontend.** Requires [nvm](https://github.com/nvm-sh/nvm); `.nvmrc` pins Node 24 LTS.

```bash
nvm install && nvm use
cd frontend && npm ci && npm start     # http://localhost:4200, proxies /api and /auth to localhost:8080
```

## Using the API

```bash
set -a; source .env; set +a
TOKEN=$(curl -s -u "$APPSTORE_AUTH_CLIENT_ID:$APPSTORE_AUTH_CLIENT_SECRET" -X POST localhost:8080/auth/token | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/v1/apps?term=relution&cc=de' | jq
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/v1/apps/361309726?cc=de&l=de&platform=mac' | jq
```

Tokens expire after 15 minutes (`APPSTORE_AUTH_JWT_TTL`); request a new one when the API answers 401.

Parameters, response fields and error types are documented in [`docs/api/README.md`](docs/api/README.md).

## Tests

```bash
# from the repository root
(cd backend && ./gradlew check)                   # formatting, unit, WireMock, web and architecture tests (no live Apple calls)
(cd backend && ./gradlew liveTest)                # at most 3 real Apple calls to detect API drift (nightly in CI)
(cd frontend && npm test -- --watch=false)        # focused unit tests
scripts/smoke.sh                                  # 13 end-to-end checks against a running instance (reads .env; makes a few real Apple calls)
```

CI runs `check`, the frontend tests and build, and the image builds on every pull request and every push to `main`. `liveTest` runs nightly, and `smoke.sh` will be run manually. See [`docs/development/tooling.md`](docs/development/tooling.md).

## Troubleshooting

- **The app exits at startup with a configuration error.** A required variable is missing, the JWT secret isn't Base64 or is shorter than 32 bytes after decoding, or another `APPSTORE_*` value is invalid (for example a search budget of 0 or a token TTL above 1 hour).
- **A port is already in use.** Change the host side of the port mapping in `docker-compose.yml`.
- **`npm ci` or `ng` complains about the Node version.** Run `nvm use`. Angular 22 does not support Node 25.
- **Searches return 503.** The outbound Search budget (`APPSTORE_APPLE_SEARCH_BUDGET`, default 20 calls/min) is used up, or Apple's per-IP rate limit was hit. The service doesn't call Apple until `Retry-After` expires; cached searches keep working.
- **The web client shows the login page again.** The token is kept in memory only and expires after 15 minutes. Reloading the page also clears it.

More in [`docs/operations/runbook.md`](docs/operations/runbook.md).

## Documentation

| Area | Where |
|---|---|
| Architecture, errors, caching, security, frontend | [`docs/architecture/`](docs/architecture/) |
| API contract | [`docs/api/README.md`](docs/api/README.md) |
| Apple API behavior and mapping | [`docs/integrations/`](docs/integrations/) |
| Configuration, deployment, observability, runbook | [`docs/operations/`](docs/operations/) |
| Testing and tooling | [`docs/development/`](docs/development/) |
| Decisions | [`docs/adr/README.md`](docs/adr/README.md) |
| Glossary | [`docs/glossary.md`](docs/glossary.md) |
| Captured Apple responses (WireMock test data) | [`backend/src/test/resources/wiremock/README.md`](backend/src/test/resources/wiremock/README.md) |
| How to contribute | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| Discovery Day challenge material (temporary) | [`docs/challenge/`](docs/challenge/) |

## Repository layout

See [`docs/architecture/overview.md`](docs/architecture/overview.md#repository-layout).
