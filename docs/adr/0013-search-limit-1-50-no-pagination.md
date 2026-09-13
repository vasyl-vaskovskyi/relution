# ADR-0013: Search `limit` 1–50, no pagination

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** Apple ignores `offset` (observed) and caps results at ~200.
- **Decision:** `limit` defaults to 25 with a maximum of 50. No pagination parameters, because we cannot honor them.
- **Consequences:** Deep result sets can't be reached. This is stated in the API docs and the presentation.
