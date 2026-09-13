# ADR-0003: Spring Boot 4.1 with RestClient

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** A blocking servlet app with two outbound HTTP calls.
- **Options:** `RestClient`; `WebClient` (WebFlux); `@HttpExchange` interfaces.
- **Decision:** `RestClient`, built from Boot's injected `RestClient.Builder`, with explicit timeouts.
- **Consequences:**
  - No reactive stack in a servlet app.
  - All Apple HTTP tests use WireMock.
  - `@HttpExchange` was rejected because its proxy layer is harder to explain and debug in a one-day project.
