# ADR-0019: Serve the frontend from an nginx container, same origin

- **Status:** Accepted; amended by [ADR-0034](0034-security-and-privacy-hardening.md) (unprivileged nginx, security headers)
- **Date:** 2026-09-13 (prep)

- **Context:** `docker compose up` must start everything, without adding CORS configuration to the API.
- **Options:** a separate nginx container; Spring Boot serving the build; `ng serve` plus CORS.
- **Decision:** A multi-stage `frontend` image (Node 24 build → nginx). nginx serves the Angular app and reverse-proxies `/api/` and `/auth/` to `app:8080`.
  - Frontend port: `4200`. The API stays reachable on `8080` for curl and Bruno.
  - Local development: `ng serve` with `proxy.conf.json`, which gives the same paths.
- **Consequences:** Same origin, so no CORS. The Gradle and npm builds stay independent.
