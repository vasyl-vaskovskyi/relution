# ADR-0038: Committed alert rules

- **Status:** Accepted; amends [ADR-0027](0027-observability-logs-and-metrics-in-the-app-opentelemetry-graf.md)
- **Date:** 2026-09-13 (prep)

- **Context:** Metrics without alerts are only visible when someone looks.
- **Decision:**
  - **Rules file:** `ops/alerts.yml` holds Prometheus alerting rules for:
    - Apple 429 and short-circuit rate;
    - `contract_error` and `missing_field` rate;
    - `appstore.storefront.allowlist.mismatch{direction="outdated"} > 0`;
    - p95 latency of `appstore.apple.requests`;
    - service down (`up == 0` for the management scrape target).
  - **Runbook:** every alert links to a runbook section (`docs/operations/runbook.md`).
  - **Metric prefix:** application metrics use the `appstore.` prefix. `appstore.apple.requests` publishes a percentiles histogram, which the p95 alert needs.
  - **Tags:** `term`, `cc`, `id` and user identifiers are never metric tags.
- **Consequences:** Operators get actionable alerts from day one. The rules are validated by review, not by a running Prometheus, which is acceptable for now.
