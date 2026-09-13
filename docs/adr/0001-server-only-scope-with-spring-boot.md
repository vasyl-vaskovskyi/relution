# ADR-0001: Server-only scope with Spring Boot

- **Status:** Accepted; amended by [ADR-0017](0017-add-an-angular-web-client-time-boxed.md) (web client added)
- **Date:** 2026-09-13 (prep)

- **Context:** This is a backend position. The task requires only the server; a client is optional and must not come at the expense of the server.
- **Options:** server only; server plus a minimal client.
- **Decision:** Server only. The saved time goes into error tolerance, tests and Part 3.
- **Consequences:** The API must be usable without a UI, so authentication, OpenAPI docs and a smoke script are required.
- **Amendment (prep):** a time-boxed Angular client was added later (ADR-0017). The server is still the graded core, and auth and OpenAPI stay, so the API also works on its own for curl/Bruno users.
