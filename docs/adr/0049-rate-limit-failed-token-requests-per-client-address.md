# ADR-0049: Rate-limit failed token requests per client address

- **Status:** Accepted; amends [ADR-0034](0034-security-and-privacy-hardening.md)
- **Date:** 2026-09-14 (Discovery Day)

- **Context:**
  - `POST /auth/token` compares the client credentials in constant time, but nothing limits how often it can be tried. [ADR-0034](0034-security-and-privacy-hardening.md) left this as a known gap, "mitigated at the ingress", and the local stack has no ingress limit.
  - The approved stack already has Caffeine, Resilience4j and an in-house token bucket (`SearchBudget`, [ADR-0045](0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md)). No new dependency is approved.
  - **Client address:** the app has no forwarded-header handling locally (`server.forward-headers-strategy` defaults to `none`), so `HttpServletRequest.getRemoteAddr()` is the TCP peer. Behind the web client's nginx that peer is the nginx container, for every browser. nginx sends `X-Forwarded-For` (`$proxy_add_x_forwarded_for`), but port 8080 is also published directly on `127.0.0.1`, and those connections arrive from the Docker bridge gateway, an address Tomcat's default internal-proxy list (10/8, 172.16/12, 192.168/16, 127/8) trusts. Trusting the header would let a direct caller choose its own key.
  - The web client logs in with the API client credentials ([ADR-0018](0018-frontend-auth-login-form-token-in-memory.md)), so a legitimate login and an attack look the same except for success.
  - Client ids, secrets and addresses must not be logged ([`../architecture/security.md`](../architecture/security.md#logging-and-privacy)).
- **Options:**
  1. Resilience4j `RateLimiter` per key: fixed refresh periods, one limiter object per key with its own registry entry, and no refund for successes.
  2. A lockout after N failures (fixed ban): simple, but a full ban window is harsh and gives no graded `Retry-After`.
  3. An in-house token bucket of failed attempts per client address, kept in a bounded Caffeine cache. Each request takes a permit before the credentials are compared, and a success gives it back.
  4. Limit in nginx (`limit_req`): covers only traffic through the web client; direct API callers and other ingresses are unprotected.
- **Decision:** Option 3.
  - **Key:** `getRemoteAddr()` as the app sees it. IPv4 addresses are used as they are; IPv6 addresses are reduced to their /64 prefix, because one subscriber usually holds a whole /64. An unparsable address falls into one shared `unknown` bucket. `X-Forwarded-For` and `Forwarded` are **not** trusted. `application.yml` sets `server.forward-headers-strategy: none` explicitly, because Spring Boot defaults it to `native` on a supported cloud platform (for example Kubernetes), where Tomcat's `RemoteIpValve` would take the address from `X-Forwarded-For` sent by any internal-network peer and let a caller choose its own key.
  - **What counts:** only failures. The permit is taken before the comparison (so parallel guesses can't all pass the check) and given back when the credentials match. A missing or malformed `Authorization` header counts as a failure.
  - **Over the limit:** every request from that key gets **429** `urn:appstore:problem:too-many-requests` (a new problem type) with `Retry-After` in whole seconds (at least 1), valid credentials included, so the endpoint gives no oracle while blocked. No `WWW-Authenticate` header is sent.
  - **Limits** (`appstore.auth.limit.*`, [`../operations/configuration.md`](../operations/configuration.md)): `failures=10` permits, refilled continuously over `window=PT5M` (one failure every 30 s sustained), `keys=10000` addresses tracked. The window is validated between 1 s and 1 day.
  - **Memory:** the buckets live in a Caffeine cache with `maximumSize(keys)` and `expireAfterAccess(window)`. An entry idle for one window would be full again, so expiry loses nothing. Size eviction can forget an active attacker's bucket only when more than `keys` addresses fail at the same time; that is accepted.
  - **Observability:** counter `appstore.auth.token.requests` with `outcome=issued|rejected|rate_limited`. Each 429 is logged at DEBUG only (an attacker controls their frequency; the metric is the signal). Rejections stay INFO. No log line or problem body contains the client id, the secret or the address.
  - **Scope:** per instance, in memory, like the Search budget. The limiter is a Spring component in `auth` and depends only on Caffeine and the `api` `ProblemDetails` factory.
- **Consequences:**
  - A sustained guessing rate is capped at `failures / window` per address (default 2 per minute, about 2,900 per day). The 32-byte secrets recommended in the configuration make this far too slow to matter.
  - **Behind the bundled nginx, all browser logins share one bucket.** Ten wrong secrets in five minutes, from anyone using the web client, block web logins for everyone for up to 30 s per missing permit. Successful logins don't consume the budget, so normal use isn't affected. A deployment with a trusted ingress should limit at the ingress, or enable forwarded-header handling with an explicit trusted-proxy list that excludes directly reachable networks (a new ADR).
  - Replicas each keep their own buckets, so the effective limit is multiplied by the number of instances.
  - Attackers with many addresses (botnets, many IPv6 /64s) are slowed per address only. The proper fix stays an identity provider ([`../architecture/security.md`](../architecture/security.md#known-gaps)).
  - The web client shows a login-specific message for `too-many-requests` ([`../architecture/frontend.md`](../architecture/frontend.md#error-messages-problem-messagets)).
