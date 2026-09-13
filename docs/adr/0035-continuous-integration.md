# ADR-0035: Continuous integration with GitHub Actions

- **Status:** Accepted; amends [ADR-0007](0007-part-3-direction-resilience-caching.md) (CI is merge hygiene, not a Part 3 topic)
- **Date:** 2026-09-13 (prep)

- **Context:** There was no pipeline, so nothing proved on every change that the code builds, is formatted, passes its tests and produces images.
- **Decision:**
  - **Workflow:** `.github/workflows/ci.yml` runs on every push and pull request.
  - **`backend` job:** `actions/setup-java` (Temurin 25) and `gradle/actions/setup-gradle`, then `./gradlew check` (Spotless, unit, WireMock, web and ArchUnit tests).
  - **`frontend` job:** `actions/setup-node` with `node-version-file: .nvmrc`, then `npm ci`, `npm test -- --watch=false`, `npm run build`.
  - **`images` job:** `docker build` for both images, without pushing.
  - **Versions:** action versions are pinned and updated by Dependabot.
- **Consequences:**
  - Every change is verified before review.
  - About 20 minutes of setup on the day.
  - Deployment pipelines are out of scope.
