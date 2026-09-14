# ADR-0023: Frontend scope and tests

- **Status:** Accepted; amended by [ADR-0029](0029-domain-terms-in-public-api.md) (errors mapped by problem type) and [ADR-0053](0053-pre-fill-the-german-storefront.md) (`de`/`de` pre-fill instead of the browser locale)
- **Date:** 2026-09-13 (prep)

- **Decision:**
  - **Screens and states:**
    - Login.
    - Search field and result list showing name and icon.
    - A loading state, plus a "still loading" hint after 3 s.
    - "No results found" and error states with a retry action.
    - A details view: bundle id, version, price, platforms, description, what's new.
  - **Locale:** `cc` and `l` are pre-filled from the browser locale.
  - **Extras:** the language Apple actually served is shown, and an iOS/Mac platform toggle.
  - **Tests** (a few focused unit tests):
    - the auth interceptor (attaches the token; a 401 leads to login);
    - locale → `cc`/`l` pre-fill;
    - ProblemDetail → user message mapping.
- **Consequences:** No component tests. The justification is that the logic most likely to break silently is covered, while the templates are checked by the demo.
- **Amendment (day):** component tests were added at the maintainer's request (Discovery Day track "frontend component tests"). They go through the DOM with the real router and mocked HTTP; the cases are listed in [`../development/testing.md`](../development/testing.md#strategy). The decision on screens, states and the focused logic tests above is unchanged.
