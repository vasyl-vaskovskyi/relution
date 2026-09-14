# Runbook

Every entry follows the same pattern: **signal → impact → check → action**. Metric and log names are defined in [`observability.md`](observability.md).

## Alerts

### `AppleSearchRateLimited`
- **Impact:** searches return 503 for uncached terms while Apple's `Retry-After` window runs or the outbound Search budget refills. Cached searches and details keep working.
- **Check:**
  - `appstore_apple_requests_seconds_count{api="search"}` grouped by `outcome` (`rate_limited`, `short_circuited`, `budget_exhausted`);
  - the number of replicas sharing the egress IP;
  - traffic spikes on `http_server_requests_seconds_count{uri="/api/v1/apps"}`.
- **Action:**
  1. Confirm the short-circuit is working (`short_circuited` > 0, few `rate_limited`). Mostly `budget_exhausted` means the local limiter holds the traffic back before Apple sees it.
  2. Look for abusive clients by grouping the `apple call` log lines by `clientId` (the JWT subject).
  3. Consider raising `APPSTORE_CACHE_SEARCH_TTL`.
  4. Check that `APPSTORE_APPLE_SEARCH_BUDGET` is not above what Apple allows or what was bought, and that replicas sharing one egress IP split the budget ([`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#outbound-rate-limiter-on-search)). If the need is real, buy more requests and raise the budget, or plan the shared cache ([known limits](../architecture/caching-resilience.md#known-limits)).
  5. Never rotate IPs to get around the limit ([ADR-0025](../adr/0025-deal-with-the-per-ip-search-rate-limit-within-apples-rules.md)).

### `AppleContractErrors`
This section also covers the `AppleMissingFields` alert.

- **Impact:** Apple changed a response (Legacy API drift). Fields may be missing, or requests may return 502.
- **Check:**
  - ERROR `apple call` log lines with `outcome=contract_error`;
  - `appstore_apple_mapping_missing_field_total` by `field`;
  - the latest nightly `apple-drift` workflow run.
- **Action:**
  1. Capture a fresh response (see "Refresh captures and fixtures" below) and compare it with the committed capture.
  2. Update the mapper and its fixture in one change.
  3. Add an ADR if the change affects the API contract.

### `StorefrontAllowlistOutdated`
- **Impact:** requests for that storefront get 400, although the code is on the allowlist.
- **Check:** ERROR logs `storefront allowlist outdated: <code>` and the counter `appstore_storefront_allowlist_mismatch_total{direction="outdated"}`.
- **Action:** refresh the allowlist (see below).

### `AppleLatencyHigh`
- **Impact:** slow responses; some requests end in 504 (read timeout).
- **Check:** p95 by `api`, the `read_timeout` and `connect_error` rates, and Apple's status page.
- **Action:** usually wait it out, because the timeouts bound the impact. If it lasts, consider tuning `APPSTORE_APPLE_TIMEOUT_READ`, and document the change.

### `AppstoreDown`
- **Impact:** Prometheus can't scrape the management port: the instance is down or unreachable, and receives no traffic.
- **Check:** `/actuator/health` on 8081 and the startup logs (a configuration fail-fast?).
- **Action:** fix the configuration or roll back. Readiness never depends on Apple, so this is our problem, not Apple's.

## Procedures

### Refresh the storefront allowlist (every 6 months, or after an allowlist alert)
1. Open [App Store localizations](https://developer.apple.com/help/app-store-connect/reference/app-information/app-store-localizations/) and extract the ISO alpha-3 column.
2. Convert the codes to alpha-2 (`Locale.getISO3Country()` mapping; Kosovo `XKS` → `xk`).
3. Diff the result against `SupportedStorefronts`, and spot-check added or removed codes against both Apple APIs.
4. Update `SupportedStorefronts.CODES`, its source and date comment, and [`../integrations/apple-api-behavior.md`](../integrations/apple-api-behavior.md#4-app-store-storefront-allowlist) in one change.
5. Also review the WARN logs `storefront missing from allowlist, verify the list: <code>` (counter `direction=missing`). Requests for these codes are rejected without calling Apple, so a new Apple storefront shows up only there ([ADR-0043](../adr/0043-reject-unlisted-storefront-codes-locally.md)).

### Rotate the JWT signing secret
1. Generate a new secret with `openssl rand -base64 32`.
2. Deploy it as `APPSTORE_AUTH_JWT_SECRET`.
3. Every issued token becomes invalid. Clients get 401 and request a new token; the TTL is at most 1 h.
4. Zero-downtime rotation (multiple keys with `kid`) is not implemented. It is planned together with an identity provider.

### Rotate the client credentials
Set new `APPSTORE_AUTH_CLIENT_ID` and `APPSTORE_AUTH_CLIENT_SECRET` values, deploy, and hand the new values to API clients. Tokens already issued stay valid until they expire.

### Refresh captures and fixtures
1. Capture with `curl -s '<url>' | jq .`. The request URLs and trimming rules are in [`backend/src/test/resources/wiremock/README.md`](../../backend/src/test/resources/wiremock/README.md). Stay within Apple's rate limit.
2. Apply the trimming rules and replace the file under `__files/apple/…`, or the mapping for captures that include headers. There is no second copy.
3. Run the mapper and client tests. If Apple's behavior changed, update the assertions and record the change in [`../integrations/apple-api-behavior.md`](../integrations/apple-api-behavior.md).

### Nightly `apple-drift` workflow failed
The workflow is "Apple drift" (`.github/workflows/apple-drift.yml`); it runs `./gradlew liveTest`.

1. Re-run it once (`gh workflow run apple-drift.yml`), because Apple may have been unavailable or rate-limiting.
2. If it fails again, follow `AppleContractErrors`.
