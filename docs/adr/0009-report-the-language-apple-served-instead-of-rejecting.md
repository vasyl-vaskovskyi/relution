# ADR-0009: Report the language Apple served instead of rejecting

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** MZ silently substitutes unsupported languages. On `cc=de`, `l=fr` becomes `de-de` and `l=en` becomes `en-gb`.
- **Options:** 200 plus the language actually served; 400 when the primary language differs.
- **Decision:** Return 200. `storefront.language` in the response carries `meta.language.tag`. Validation of `l` checks the format only.
- **Consequences:** Clients must read `storefront.language`. Strict matching would have rejected legitimate mappings such as `en` → `en-gb`.
