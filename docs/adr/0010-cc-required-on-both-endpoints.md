# ADR-0010: `cc` required on both endpoints

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** The first draft had an optional `country` defaulting to `de` on search and a required `cc` on details. That was the same concept with two names and two rules.
- **Options:** `cc` on both and required on both; `cc` on both with an optional default on search; keep as is.
- **Decision:** `cc` on both endpoints, required on both. `l` is required on details. This matches the task wording ("id, cc and l are parameters").
- **Consequences:** No hidden server-side locale default. Clients (and curl users) must always send `cc`.
