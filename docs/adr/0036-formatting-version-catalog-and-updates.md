# ADR-0036: Formatter, version catalog, wrapper checksum, dependency updates

- **Status:** Accepted; supersedes [ADR-0016](0016-defer-editorconfig-to-the-day.md) for the backend; amended by [ADR-0042](0042-commit-size-and-parallel-pull-requests.md) (`open-pull-requests-limit: 0` during the Discovery Day)
- **Date:** 2026-09-13 (prep)

- **Context:** With many developers, formatting debates and whitespace noise waste review time. Dependency versions scattered across build files drift, and nobody updates them.
- **Decision:**
  - **Java formatting:** Spotless (`com.diffplug.spotless`) with `palantir-java-format`, applied once in the bootstrap commit and checked in CI via `./gradlew check`.
  - **Frontend formatting:** keeps the generated `.editorconfig` and `.prettierrc`. Adding a Prettier check needs approval if it requires a new dev dependency.
  - **Versions:** all backend dependency and plugin versions live in `gradle/libs.versions.toml`.
  - **Gradle wrapper:** `gradle-wrapper.properties` pins `distributionSha256Sum`.
  - **Dependabot:** `.github/dependabot.yml` (each ecosystem added once its directory exists) updates `gradle` (`/backend`), `npm` (`/frontend`), `docker` (both Dockerfiles), `docker-compose` (`/`) and `github-actions` (`/`) weekly, with minor and patch updates grouped.
- **Consequences:**
  - No formatting discussions in reviews.
  - One place for versions.
  - A steady, reviewable stream of update PRs.
  - The root `.editorconfig` question is closed for Java.
