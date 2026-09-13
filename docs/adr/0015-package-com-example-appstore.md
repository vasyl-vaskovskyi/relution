# ADR-0015: Package `com.example.appstore`

- **Status:** Accepted; sub-packages renamed by [ADR-0028](0028-package-boundaries-and-ports.md)
- **Date:** 2026-09-13 (prep)

- **Context:** A group id and base package are needed at kickoff.
- **Options:** a personal namespace; `io.relution.challenge.appstore`; `com.example.appstore`.
- **Decision:** `com.example.appstore`, with the package name set explicitly in Initializr (Initializr derives the package from group + artifact; start.spring.io metadata shows `com.example.demo` for artifact `demo`).
- **Consequences:** Neutral naming that doesn't imply an official Relution package.
