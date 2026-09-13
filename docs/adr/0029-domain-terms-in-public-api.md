# ADR-0029: Domain terms in the public API, decimal money, extensible contract

- **Status:** Accepted; supersedes [ADR-0014](0014-optional-platform-on-details.md); amends [ADR-0023](0023-frontend-scope-and-tests.md)
- **Date:** 2026-09-13 (prep)

- **Context:**
  - `platform=enterprisestore|macappstore` exposed Apple's distribution-channel names. Once clients depend on them we cannot rename them.
  - Prices were doubles.
  - The 400 field-error shape was undefined.
  - The frontend selected messages by HTTP status.
  - No rule said what clients must do with enum values or problem types added later (e.g. `BOOK` instead of `OTHER`).
- **Options:** keep Apple's terms and document them; translate to domain terms inside the Apple adapter.
- **Decision:**
  - **Platform:** `platform=ios|mac` (default `ios`), echoed as `storefront.platform`. `integration.apple` maps `ios` to `enterprisestore` and `mac` to `macappstore`.
  - **Money:** `price.amount` is a decimal **string** (e.g. `"0.99"`), backed by `BigDecimal`. `currency` may be `null` on details, because the lookup API has no currency field.
  - **Validation errors:** a 400 `invalid-request` carries `errors: [{"field": "...", "message": "..."}]`.
  - **Extensibility:** enums (`kind`, `platforms`) and problem types are documented as extensible. Clients must tolerate unknown values.
  - **Frontend:** maps errors by problem `type` first, and uses the HTTP status only as a fallback.
  - **Unchanged:** `cc` and `l` keep their names, because the task prescribes them. Internally they are `countryCode` and `languageTag`.
- **Consequences:** The API stays stable if Apple renames channels or we add a store. Clients parse the amount as a decimal string, which is a documented trade-off against JSON numbers.
