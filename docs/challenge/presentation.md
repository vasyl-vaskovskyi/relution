# Presentation checklist (10–15 min)

## 1. Demo (3–4 min)

**In the browser** (after `docker compose up`, at `localhost:4200`, with the console open):
- Log in and search, showing the loading and no-results states.
- Open details, then switch the platform to Mac.
- Set `l=fr` to show the language Apple actually served.
- Show the error states: `cc=cu` and an unknown id.
- Point out the debug logs, including correlation ids.

**With curl or Swagger:** 401 without a token, 400 with `errors[]` for a missing `cc`, 401 for `/actuator/env` on the public port (catch-all deny).

**Observability:**
- Find a JSON log line by its correlation id.
- Show `localhost:8081/actuator/prometheus`: `appstore_apple_requests_seconds_count` by `outcome`, and the cache hits.
- If Level 2 was built: the Grafana trace of a search.

**CI:** the green pipeline, and the nightly drift workflow.

## 2. Decisions (3 min)

- **Scope:** server first, client time-boxed. Java 25 and Node 24 LTS.
- **Boundaries:** ports and adapters with ArchUnit enforcement. Apple terms stay inside the adapter (`platform=ios|mac`, decimal prices).
- **Error tolerance:** the self-monitoring storefront allowlist, the error matrix, served-language reporting.
- **Resilience:** async caches with single-flight and a negative cache, connection-only retry bounded to 8 s, the 429 short-circuit.
- **Operability:** management port 8081, probes that don't depend on Apple, one `appstore.*` config namespace, security and privacy hardening.
- **Merge hygiene:** CI, Spotless, version catalog, Dependabot, drift detection, alert rules, and ADR-based docs separated from challenge material.
- **Left out, and why:**
  - pagination (Apple ignores `offset`);
  - books;
  - browser-direct search (open, [ADR-0026](../adr/0026-hybrid-routing-angular-client-calls-apple-search-directly-de.md));
  - the stretch goals: Level 2 observability, rate limiter, circuit breaker.

## 3. Working with AI (2–3 min)

- Where AI helped: API reconnaissance, research, drafting, multi-perspective reviews.
- Where it got in the way: overconfident "facts", a schedule that didn't add up, acting on planning answers.
- Walk through the strongest [`ai-log.md`](ai-log.md) entry, ideally a code-level one from the day. Prep examples: #3 (a client input error mapped to 502) and #7 (a package cycle and blocking cache found by review).

## 4. Part 3 (2–3 min)

- Why resilience + caching over the alternatives.
- The per-IP rate-limit insight and why IP rotation was rejected ([ADR-0025](../adr/0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md)).
- What was trimmed for the client, and how far it got.

## 5. Honest assessment (2 min)

Confirm or extend these on the day:
- **Legacy API:** the MZ API is Legacy and undocumented. The drift test and metric detect changes but can't prevent them.
- **Storefront allowlist:** static. Staleness is detected and logged, not fixed.
- **Per instance:** caches, the 429 guard and the stretch-goal limiter are per instance, while Apple's limit is per IP. At scale this needs a shared cache and budget.
- **Auth:** HS256 shared secret, self-issued tokens, no key rotation, no rate limit on `/auth/token`. The web client logs in with client credentials. Production would use OIDC with per-user tokens.
- **Latency:** retries can stretch one call to about 12 s in the theoretical worst case.
- **Frontend contract:** `api.types.ts` is hand-written, and there is no OpenAPI contract snapshot test, so the two can drift. Generating the types from OpenAPI is the fix.
- **CSP:** keeps `style-src 'unsafe-inline'`.
- **Naming:** `com.example.appstore` must be renamed on merge into a product namespace.
- **Language:** names in the result list and the details view can differ, because Search has no usable `lang`.
- **Frontend tests:** cover logic only, not components.
- **Observability stack:** `otel-lgtm` is for dev and demos only. Alert rules are reviewed but not exercised against a running Prometheus.
- **Nightly drift workflow:** depends on Apple's availability and rate limit.
