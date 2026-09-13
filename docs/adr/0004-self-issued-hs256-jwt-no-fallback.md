# ADR-0004: Self-issued HS256 JWT, no fallback

- **Status:** Accepted; amended by [ADR-0017](0017-add-an-angular-web-client-time-boxed.md), [ADR-0018](0018-frontend-auth-login-form-token-in-memory.md) and [ADR-0034](0034-security-and-privacy-hardening.md) (token claims hardening)
- **Date:** 2026-09-13 (prep)

- **Context:** With no client, the task requires "a simple authentication layer".
- **Options:** static API key; HTTP Basic on every call; self-issued JWT; external IdP.
- **Decision:** `POST /auth/token` takes HTTP Basic client credentials and issues a short-lived HS256 JWT, which the Spring OAuth2 resource server validates. No API-key fallback.
- **Consequences:**
  - The shared-secret signing key isn't production-grade; production would need an IdP or asymmetric keys. This goes into the honest assessment.
  - Security dependencies are added only in the JWT block, so earlier blocks can be checked with plain curl.
  - Budget: 1 h.
- **Amendment (prep):** with the Angular client (ADR-0017) the task no longer strictly *requires* auth. We keep it, because the API must also work on its own for curl/Bruno users. The web client logs in with the same client credentials (ADR-0018).
