# Contributing

## Workflow

1. **One logical change per commit, at most 10 changed files** (see [Commit size](#commit-size)). Keep diffs reviewable.
2. **Test first** for mappers, Apple clients and the storefront policy. Test data lives in `backend/src/test/resources/wiremock/` (see [`docs/development/testing.md`](docs/development/testing.md)).
3. **Run the checks locally** before pushing:
   ```bash
   (cd backend && ./gradlew spotlessApply check)
   (cd frontend && npm test -- --watch=false && npm run build)
   ```
4. **Update the docs in the same change.** Each fact has one home (see the table in [`CLAUDE.md`](CLAUDE.md)). Link to it; don't copy it.
5. **Open a pull request** using the template (`.github/pull_request_template.md`). CI must be green, and one maintainer approves.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `build:`, `ci:`, with a scope where it helps (`feat(frontend): …`, `fix(apple): …`). Use the imperative mood, and keep the subject line under 72 characters.

## Commit size

See [ADR-0042](docs/adr/0042-commit-size-and-parallel-pull-requests.md).

- **At most 10 changed files per commit.** Added, modified, deleted and renamed files each count. If a change needs more, split it into several meaningful commits.
- **Exceptions**, each stated in the commit body as `Exception: generated | pure move | lockfile | formatting`:
  - untouched generator output (Initializr, `ng new`, `ng add`);
  - pure moves or renames with no content changes;
  - lockfile-only changes;
  - repository-wide formatting (`./gradlew spotlessApply`).
- **Check before committing:** `git diff --cached --name-only | wc -l`.

## Branches and pull requests

- **One pull request per block of work,** on GitHub. Branch names: `build/…`, `feat/…`, `test/…`, `ci/…`, `ops/…`, `docs/…`.
- **Rebase on `main` before merging.** Merge with **Rebase and merge** only; squash and merge commits are disabled, so the individual commits stay in `main`.
- **CI must be green,** and a maintainer approves.
- **Sequential work** (changes that build on unmerged code) waits until its prerequisite is merged. Don't stack unreviewed PRs.
- **Independent work** can run in parallel worktrees: `git worktree add ../<repo>-<track> -b <branch> main`. At most two PRs should wait for review at once.
- **Shared files** (`.github/workflows/ci.yml`, `.github/dependabot.yml`, `docker-compose.yml`, `backend/gradle/libs.versions.toml`, `README.md`, `docs/operations/configuration.md`): keep edits to them small and in separate commits, so rebases stay trivial.

## Decisions

- Record any non-obvious technical choice as an ADR in [`docs/adr/`](docs/adr/README.md): the next free number, a kebab-case title, Status, Context, Options, Decision and Consequences.
- Never rewrite an accepted ADR. Write a new one that supersedes or amends it, and update only the old one's Status line.
- Don't implement an ADR with status **Open**.

## Dependencies

- Add a dependency only after the maintainer agrees. Record the reason in the PR or an ADR.
- Backend versions live in `backend/gradle/libs.versions.toml`. Pin exact versions everywhere, including Docker images and GitHub Actions.
- Dependabot opens weekly update PRs. Review them like any other change.

## Code style

- **Java:** Spotless with palantir-java-format (`./gradlew spotlessApply`). Don't format by hand.
- **Frontend:** follow the generated `.editorconfig` and `.prettierrc`.
- **Architecture:** follow the package rules in [`docs/architecture/overview.md`](docs/architecture/overview.md). The ArchUnit test enforces them.
- **Types:** Java records for DTOs and domain objects. `Optional` only as a return type. No Lombok.

## Security and privacy

- Never commit secrets. `.env` is git-ignored; `.env.example` holds placeholders only.
- Follow the logging and privacy rules in [`docs/architecture/security.md`](docs/architecture/security.md#logging-and-privacy).
- Report security issues to the maintainers privately, not in public issues.

## Documentation conventions

- American English.
- Lowercase kebab-case file names under `docs/`. Upper case only for root files that tools expect (`README.md`, `CONTRIBUTING.md`, `CLAUDE.md`).
- Roles ("the maintainer"), not personal names, in durable docs.
- Tag claims about external systems as **documented**, **observed** or **inferred**, and mark anything unconfirmed as *verify*.
