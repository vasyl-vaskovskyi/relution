# ADR-0044: Secure the management port as a whole

- **Status:** Accepted; amends [ADR-0032](0032-management-port-and-probes.md)
- **Date:** 2026-09-14 (Discovery Day, kickoff spike)

- **Context:**
  - **The plan:** a separate `SecurityFilterChain` matched by `EndpointRequest.toAnyEndpoint()` permits the exposed endpoints on port 8081. The public chain ends with a catch-all `denyAll`.
  - **The spike** (Spring Boot 4.1.1, Spring Security 7.1.1, throwaway project):
    - the exposed endpoints on 8081 answered 200 without a token;
    - every other path on 8081 (`/actuator/env`, `/anything`) did not match the endpoint matcher, fell through to the public chain, and answered **401**.
  - **The problem:** [ADR-0032](0032-management-port-and-probes.md) expects 404 for unexposed paths on the management port. A 401 also suggests that something exists behind authentication.
- **Options:**
  1. Keep the endpoint matcher and expect 401 on 8081.
  2. Match every request that arrives on the management server (its web server namespace) and permit it. The exposure list alone decides what exists there.
  3. Permit the exposed endpoints inside the public chain with `EndpointRequest` matchers.
- **Decision:** Option 2.
  - The management chain (`@Order(1)`) uses a request matcher that checks `WebServerApplicationContext.hasServerNamespace(context, "management")` for the request's web application context, and permits all.
  - The public chain is unchanged, including the catch-all `denyAll`.
- **Consequences:**
  - Port 8081: exposed endpoints answer 200 and everything else 404, as ADR-0032 expects. Port 8080 is unchanged (401 for `/actuator/**`). Both variants were compared in the spike.
  - The management port stays protected by the network only (ADR-0032).
  - If the management port is ever not separate, no request carries the `management` namespace, so the public chain's catch-all applies (the safe default). The security tests in the auth block cover the 8080 and 8081 expectations.
