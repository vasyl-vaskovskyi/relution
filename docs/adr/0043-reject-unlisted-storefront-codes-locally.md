# ADR-0043: Reject unlisted storefront codes locally

- **Status:** Accepted; amends [ADR-0008](0008-storefront-allowlist-that-reports-its-own-staleness.md) and [ADR-0030](0030-details-cache-and-bounded-retry.md)
- **Date:** 2026-09-14 (prep)

- **Context:**
  - **The old flow:** [ADR-0008](0008-storefront-allowlist-that-reports-its-own-staleness.md) sent a valid ISO country code that isn't on the allowlist to Apple once, cached Apple's verdict for 24 h (the `storefront-verdict` cache, [ADR-0030](0030-details-cache-and-bounded-retry.md)) and logged a WARN.
  - **The review:** it called this the most elaborate and least-used part of the design. It needs a cache, its own tests and a verdict flow, for codes Apple almost never adds (the last batch of storefronts was in 2020).
- **Options:**
  1. Keep the verdict flow.
  2. Reject unlisted codes locally and keep only the maintenance signals.
- **Decision:** Option 2.
  - **Valid ISO code not on the allowlist:** 400 `unsupported-storefront`, without calling Apple. WARN `storefront missing from allowlist, verify the list`, and counter `appstore.storefront.allowlist.mismatch{direction=missing}`.
  - **On the allowlist, but Apple rejects it:** unchanged. 400 `unsupported-storefront`, ERROR `storefront allowlist outdated`, and counter `{direction=outdated}`.
  - The `storefront-verdict` cache and its properties are removed.
- **Consequences:**
  - A simpler policy with simpler tests, and no extra Apple calls for unknown codes.
  - If Apple adds a storefront, requests for it are rejected until the allowlist is updated. The WARN log and the counter make that visible, and the runbook's refresh procedure covers it.
