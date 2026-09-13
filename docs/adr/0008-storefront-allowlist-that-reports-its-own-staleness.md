# ADR-0008: Storefront allowlist that reports its own staleness

- **Status:** Accepted
- **Date:** 2026-09-13 (prep)

- **Context:** Some valid ISO countries have no App Store (e.g. `cu`, `kp`). For those, Search returns 400 and MZ **silently serves the US storefront**, so wrong prices and names would look like a success. Java's ISO country list doesn't match Apple's storefronts.
- **Options:**
  - rely on Apple's responses only;
  - a fixed allowlist that rejects before calling Apple;
  - an allowlist plus runtime detection.
- **Decision:** The allowlist comes from Apple's App Store Connect "App Store localizations" page: 175 storefronts, converted from alpha-3 to alpha-2, verified 2026-09-13.
  - **Listed, but Apple rejects the code:** return 400 and log an ERROR saying the allowlist is outdated.
  - **Not listed, but a valid ISO code:** call Apple once, cache Apple's verdict for 24 h, return what Apple does, and log a WARN to update the list.
  - Clients never see any maintenance hints.
- **Consequences:**
  - The list needs periodic re-checking; its source and date sit next to the constant.
  - Adds one cache and one metric.
  - The static list is a known weakness for the honest assessment.
