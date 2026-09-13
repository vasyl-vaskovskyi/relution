# ADR-0028: Package boundaries, ports and ArchUnit enforcement

- **Status:** Accepted; renames sub-packages of [ADR-0015](0015-package-com-example-appstore.md)
- **Date:** 2026-09-13 (prep)

- **Context:** The first package plan (`api`, `apps`, `apple`, `auth`, `config`) had a dependency cycle: `apps` called the Apple clients, while the Apple mappers returned records owned by `apps`. The error advice in `api` also had to know Apple exception types. Nothing enforced the rules, `apps` and `apple` differed by one letter, and `config` would grow into a shared dumping ground.
- **Options:**
  1. Keep the layout and document the rules.
  2. Ports in the domain: the domain owns records, exceptions and gateway interfaces; the Apple adapter implements them; ArchUnit enforces the rules.
  3. Separate Gradle modules per layer.
- **Decision:** Option 2.
  - `catalog`: domain records, `Platform`, `AppKind`, sealed upstream exceptions, `AppSearchGateway`, `AppDetailsGateway`, services, cache setup.
  - `catalog.storefront`: storefront policy and allowlist.
  - `integration.apple`: gateway adapters implementing the gateways (429 guard, one metric and log line per logical call), `@Retryable` HTTP clients, raw records, mappers, `RestClient` beans, Apple properties.
  - `api`: controllers, DTOs, validation, ProblemDetail advice, `ProblemType`, the `ProblemDetails` factory, OpenAPI config.
  - `auth`: token endpoint, security configuration, auth properties.
  - `observability`: correlation-id filter, MDC context propagation, metric names.
  - No global `config` package: configuration lives next to the feature it configures.
  - Rules, enforced by an ArchUnit test (`com.tngtech.archunit:archunit-junit6`):
    - `catalog` depends only on `observability`.
    - `api` must not depend on `integration` or `auth`.
    - `integration` must not depend on `api` or `auth`.
    - `auth` may depend only on `api` (the shared `ProblemDetails` factory, so security errors use the same format) and `observability`.
- **Consequences:**
  - A second upstream or a second media type (books) plugs in behind the gateways without touching `api`.
  - The architecture rules fail the build instead of eroding over time.
  - Adds one test dependency. Separate Gradle modules were rejected as too heavy for the current size; the ArchUnit rules make a later split mechanical.
