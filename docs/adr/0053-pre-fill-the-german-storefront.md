# ADR-0053: Pre-fill the German storefront instead of the browser locale

- **Status:** Accepted; amends [ADR-0023](0023-frontend-scope-and-tests.md)
- **Date:** 2026-09-14 (Discovery Day)

- **Context:**
  - ADR-0023 pre-filled `cc` and `l` from the browser locale. A browser whose first language with a region is, for example, Russian opened the search on `cc=ru`, `l=ru`.
  - The service is demonstrated and used for the German App Store, and the maintainer asked for `de`/`de` as the default.
- **Options:**
  1. Keep the browser locale pre-fill.
  2. Use the browser locale only when it is German, otherwise `de`/`de`.
  3. Always pre-fill `cc=de`, `l=de`; values in the URL still win and both fields stay editable.
- **Decision:** **Option 3.** The browser-language parsing is removed; `LocaleService` returns the fixed default and still logs it ([`../architecture/frontend.md`](../architecture/frontend.md#locale-pre-fill-localeservice)).
- **Consequences:**
  - Every user starts on the German storefront and changes `cc`/`l` by hand for other countries.
  - The locale pre-fill unit tests shrink to the fixed default; the served-language check is unchanged.
  - Making the default configurable per deployment would be a later, separate decision.
