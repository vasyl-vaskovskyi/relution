# ADR-0018: Frontend auth: login form, token in memory

- **Status:** Accepted; amended by [ADR-0033](0033-configuration-namespace.md) (variable names) and [ADR-0034](0034-security-and-privacy-hardening.md) (token claims, constant-time check)
- **Date:** 2026-09-13 (prep)

- **Context:** A browser must never contain `APP_CLIENT_SECRET`.
- **Options:**
  - a login form that calls `/auth/token`;
  - an nginx proxy that injects the token;
  - credentials baked into the environment file.
- **Decision:** A login form. The user enters the client id and secret, the app calls `POST /auth/token`, and keeps the token **in memory only** (not in localStorage). An HTTP interceptor attaches it to requests; a 401 response sends the user back to the login form.
- **Consequences:**
  - Reloading the page means logging in again, which is acceptable for a demo.
  - No secret ends up in the JS bundle.
  - Using client credentials as "user login" is a demo shortcut; a real product would use OIDC.
