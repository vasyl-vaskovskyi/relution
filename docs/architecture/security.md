# Security and privacy

## Authentication

See [ADR-0004](../adr/0004-self-issued-hs256-jwt-no-fallback.md) and [ADR-0034](../adr/0034-security-and-privacy-hardening.md).

**Token issuing:** `POST /auth/token`
- The client authenticates with HTTP Basic, using `appstore.auth.client.id` and `appstore.auth.client.secret`.
- The secret is compared in constant time (`MessageDigest.isEqual`).
- Failed attempts are logged without credentials.

**Tokens:** HS256 JWTs signed with `appstore.auth.jwt.secret`. Property details are in [`../operations/configuration.md`](../operations/configuration.md).

| Claim | Value |
|---|---|
| `iss` | `appstore.auth.jwt.issuer` (default `appstore`) |
| `aud` | `appstore.auth.jwt.audience` (default `appstore-api`) |
| `sub` | client id |
| `scope` | `apps:read` |
| `exp` | now + `appstore.auth.jwt.ttl` (default 15 min, validated maximum 1 h) |

**Validation:**
- `NimbusJwtDecoder.withSecretKey(...)` with `JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(issuer), <audience JwtClaimValidator>)`.
- Spring Boot's `...resourceserver.jwt.audiences` property does not apply to a secret-key decoder, so the audience check is explicit.

**After authentication:** the client id (`sub`) is put into the MDC as `clientId`, so logs can be grouped by API client.

## Authorization (public port 8080)

| Path | Rule |
|---|---|
| `POST /auth/token` | permit all |
| `/livez`, `/readyz`, `/error` | permit all |
| `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | permit all (disabled entirely in the `prod` profile) |
| `/api/v1/**` | `hasAuthority("SCOPE_apps:read")` |
| **anything else** | `denyAll()`: 401 without a token, 403 with a token |

- **Error format:** the authentication entry point and the access-denied handler write the standard ProblemDetail through the `api` package's `ProblemDetails` factory ([`error-handling.md`](error-handling.md)).
- **Management port 8081** ([ADR-0044](../adr/0044-secure-the-management-port-as-a-whole.md)): a separate `SecurityFilterChain` (`@Order(1)`) matches every request on the management server, via `WebServerApplicationContext.hasServerNamespace(context, "management")`, and permits it. The exposure list alone decides what exists there, so unexposed paths return 404. Matching only `EndpointRequest.toAnyEndpoint()` is not enough: other paths on 8081 would fall through to the public catch-all and return 401 (seen in the kickoff spike). The port is protected by the network, not by tokens.

**Web client:** users log in with the client credentials (a shortcut for this demo). The token is kept in memory only.

**Production path:**
- A real identity provider issuing per-user OIDC tokens with the same `iss`, `aud` and `scope` semantics. Replacing our token endpoint is then a configuration change.
- Asymmetric keys with `kid` and rotation.

## Secrets

- Secrets come only from the environment (`.env` locally, never committed). There are no defaults in compose.
- **Fail-fast:** the context refuses to start if a required secret is missing or too short.
- **Rotation:** see [`../operations/runbook.md`](../operations/runbook.md).

## Management and API docs exposure

See [ADR-0032](../adr/0032-management-port-and-probes.md).

- Actuator runs on **port 8081**. The exposure list and probe settings are in [`../operations/configuration.md`](../operations/configuration.md).
- Health details are never shown.
- The management port is not routed by nginx or an ingress, and compose binds it to `127.0.0.1`.
- **Tests:** `/actuator/env` returns 404 on 8081 (not exposed) and 401 on 8080 (catch-all deny).
- The API docs are disabled in the `prod` profile.

## Logging and privacy

**This section is the binding list.** Other documents link here.

- **Never logged, at any level:**
  - tokens and `Authorization` headers;
  - client secrets and other credentials;
  - search terms (GDPR; log `termLength` instead);
  - Apple response bodies;
  - stack traces in API responses (server-side logs only).
- **Search terms travel in the query string**, so query strings are excluded from logs:
  - nginx access logs use a format without `$args` ([`../operations/deployment.md`](../operations/deployment.md#nginx-frontendnginxconf));
  - when tracing is enabled, an `ObservationFilter` removes the query from the high-cardinality URL key values (*verify* the key names on the day).
- **Correlation ids are validated** to prevent log injection ([`error-handling.md`](error-handling.md#correlation-id)).
- **A log-capture test** (`LogSafetyIntegrationTest`) runs the whole application with ECS JSON logs and asserts that no search term, `Bearer` value or Apple response body appears in the application's log lines. The fake Apple server (WireMock) logs the requests it receives. It stands in for Apple's side, which receives the term by design, so its lines are excluded. The auth block adds the configured secrets to the assertion.

## Upstream

- **Never accept or forward Apple's `itvt` sToken cookie.**
- Pin `version=2&p=mdm-lockup&caller=MDM`. Client-supplied values reach Apple only after validation.

## Containers

Details are in [`../operations/deployment.md`](../operations/deployment.md).

- Non-root runtime images: Temurin 25 JRE with a dedicated user, and `nginxinc/nginx-unprivileged`.
- Exact image versions pinned and updated by Dependabot.
- Compose publishes ports on `127.0.0.1` only.
- JVM flags `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`.

## Frontend

**Security headers from nginx:**

```
Content-Security-Policy: default-src 'self'; img-src 'self' https://*.mzstatic.com data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
server_tokens off;
```

- **`style-src 'unsafe-inline'`** stays, because Angular and Material styles need it when nginx serves static files without per-request nonces. On the day, *verify* whether the production build emits inline scripts.
- **No third-party origins besides Apple's icons** ([ADR-0046](../adr/0046-frontend-assets-stay-same-origin-no-cdn-fonts-or-icons.md)): fonts and icons are served same-origin (system fonts, inline SVG), so the CSP needs no font or style CDN, and no visitor IP address reaches Google.
- **Apple texts** (description, what's new) are rendered as text, never via `[innerHTML]`.
- **Browser console:** tokens, the `Authorization` header, credentials and search terms are never logged. The runtime debug switch exists only in the `demo` build configuration ([`frontend.md`](frontend.md#debug-logging)).

## Known gaps

| Gap | Mitigation now | Proper fix |
|---|---|---|
| No rate limit or lockout on `/auth/token` | Constant-time comparison; failed attempts logged; limit at the ingress | Identity provider, or a per-IP limiter |
| Shared HS256 secret, no key rotation | Short token TTL, runbook rotation | Asymmetric keys with `kid` via an identity provider |
| Web users log in with the API client credentials | Token kept in memory only | Per-user OIDC login |
| `style-src 'unsafe-inline'` | Strict `default-src`, no `[innerHTML]` | Nonce-based CSP with server-side rendering of `index.html` |
