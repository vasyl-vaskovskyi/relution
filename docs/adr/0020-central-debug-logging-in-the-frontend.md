# ADR-0020: Central debug logging in the frontend

- **Status:** Accepted; amended by [ADR-0034](0034-security-and-privacy-hardening.md) (debug override only in a demo build)
- **Date:** 2026-09-13 (prep)

- **Context:** The client should provide debug information via `console.log` without scattering calls across components.
- **Options:** a central service; plain `console.log` where needed; always on, including production.
- **Decision:** A `DebugLogService` wraps `console.*`, and an HTTP interceptor uses it. Enabled in development builds; controlled by an environment flag.
  - **Logged:** method, URL and params, status, duration, `X-Correlation-Id`, ProblemDetail on errors, auth state changes and the locale pre-fill result.
  - **Never logged:** tokens or credentials.
- **Consequences:** One place to change the format or switch logging off. The correlation id lets frontend logs be matched to backend logs.
