# ADR-0002: Java 25 LTS

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** A greenfield project should not start with tech debt. JDKs 17, 21 and 26 are installed locally.
- **Options:** Java 21 (older LTS, installed); Java 25 (current LTS); Java 26 (newest, non-LTS).
- **Decision:** Java 25, via a Gradle toolchain and Docker images.
- **Consequences:**
  - Supported by Spring Boot 4.1 and Gradle ≥ 9.1.
  - Java 26 was rejected because it is non-LTS and Java 27 supersedes it this month.
  - Java 21 was rejected because the only reason for it was that it was already installed. See challenge AI log #1.
