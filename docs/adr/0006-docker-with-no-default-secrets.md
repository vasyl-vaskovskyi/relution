# ADR-0006: Docker with no default secrets

- **Status:** Accepted; amended by [ADR-0033](0033-configuration-namespace.md) (variable names) and [ADR-0034](0034-security-and-privacy-hardening.md) (container hardening)
- **Date:** 2026-09-13 (prep)

- **Context:** A colleague should be able to clone the repo and start everything with one command, without us committing weak defaults.
- **Options:** dev defaults in compose; require `.env`; Dockerfile only.
- **Decision:** A multi-stage Dockerfile, `docker-compose.yml` and a committed `.env.example`. `cp .env.example .env` is required first, and the app fails fast when variables are missing.
- **Consequences:**
  - One extra setup step, documented in the README.
  - Docker is built on the day after the Error advice block (docs/challenge/plan.md), so the container runs real endpoints.
