# ADR-0050: Unit-test the alert rules with promtool in CI

- **Status:** Accepted; amends [ADR-0038](0038-alert-rules.md)
- **Date:** 2026-09-14 (Discovery Day)

- **Context:**
  - ADR-0038 accepted that `ops/alerts.yml` is validated only by review. A typo in a metric name, label or threshold would stay silent until an incident.
  - `promtool` (part of Prometheus) can check the rule syntax and run unit tests against synthetic series. The maintainer approved the official `prom/prometheus` image for this, pinned to an exact current stable tag.
  - Every other version in the repository is updated by Dependabot, so the image reference should be too.
- **Options:**
  1. **Job container:** `jobs.alerts.container.image: prom/prometheus:<tag>`.
  2. **`docker run` in a step** with the tag in the command.
  3. **A two-line `ops/promtool/Dockerfile`** (`FROM prom/prometheus:<tag>@sha256:<digest>`, `ENTRYPOINT ["promtool"]`), built and run by the job, with a Dependabot `docker` entry for `/ops/promtool`.
- **Findings on Dependabot** (dependabot-core source, `main`, checked 2026-09-14):
  - The `github-actions` ecosystem collects only `uses:` values (`workflow_file/uses_collector.rb`) and skips `docker://` references (`file_parser.rb`). It never reads `container:` or `services:` images. So neither option 1 nor option 2 is updated by the existing entry.
  - The `docker` ecosystem reads `FROM` lines in Dockerfiles and also any `image:` key in YAML files of its directory (`docker/file_parser.rb`, `deep_fetch_images`). A `docker` entry for `/.github/workflows` would therefore pick up option 1. But the busybox-based `prom/prometheus` image has an empty `/lib` (no glibc), so JavaScript actions such as `actions/checkout` cannot run inside it.
  - Nothing parses a tag inside a `run:` command (option 2).
- **Decision:**
  - **Option 3.** `ops/promtool/Dockerfile` pins `prom/prometheus:v3.14.0` with its multi-arch index digest. A `docker` Dependabot entry for `/ops/promtool` updates the tag and digest, the same mechanism that already works for `/backend` and `/frontend`.
  - **Tests:** `ops/alerts.test.yml` has, for each of the seven rules, a firing case (after its `for` duration where it has one) and a non-firing case. The cases use the metric and label names the rules query, and the expected annotations are compared as rendered.
  - **CI:** a separate job `alerts` in `.github/workflows/ci.yml` builds the image, then runs `promtool check rules` and `promtool test rules` with `ops/` mounted read-only. The existing jobs `backend`, `frontend` and `images` are unchanged.
  - **Required check:** whether branch protection requires `alerts` is left to the maintainer ([`../development/tooling.md`](../development/tooling.md#github-repository)).
- **Consequences:**
  - Rule changes are verified on every pull request. All seven rules passed their tests unchanged, and a mutation check (changed thresholds and a changed label value) made the matching tests fail.
  - One extra small file and one extra Dependabot entry. The job adds an image pull and a build of a few seconds.
  - Tests stay synthetic: they prove the rule logic, not that the application still exports these metric names. The metric names are owned by [`../operations/observability.md`](../operations/observability.md#metrics).
  - `AppstoreDown` fires only while Prometheus has an `up` series for the target. A target missing from the scrape configuration raises nothing. This was already true and is out of scope here.
