# ADR-0052: Generate the frontend API types from the OpenAPI contract

- **Status:** Accepted
- **Date:** 2026-09-14 (Discovery Day, generated API types track)

- **Context:**
  - `frontend/src/app/core/api/api.types.ts` was written by hand from `docs/api/README.md`. Nothing checked it against the service, so a renamed or removed field would only show up at runtime.
  - The normalized contract snapshot `docs/api/openapi.json` is committed, and a backend test fails when it differs from `/v3/api-docs`. It is a reliable input for code generation.
  - The frontend Docker build uses `frontend/` as its context, so it can't read `docs/`.
  - Generating from the contract revealed mismatches:
    1. `AppSearchResponse.Storefront` and `AppDetailsResponse.Storefront` share a simple name, so springdoc documented one `Storefront` schema with only `cc`; `language` and `platform` of the details response were missing.
    2. No field is marked required or nullable, so every generated property is optional and never `null`.
    3. `ProblemDetail` shows Spring's `properties` map, but the service writes `correlationId` and `errors[]` as flat members.
    4. `POST /auth/token` documents its 200 body as an untyped object.
- **Options:**
  1. Keep the hand-written types and review them against the snapshot diff.
  2. Generate types only (`openapi-typescript`, a devDependency) and keep the app's names as aliases in `api.types.ts`.
  3. Generate a full Angular client (`openapi-generator` services and models, needs a JVM or another tool).
  4. Generate in the Docker build instead of committing the output.
- **Decision:** Option 2.
  - **Tool:** `openapi-typescript` 7.13.0, pinned exactly. It declares `typescript@^5.x` as a peer dependency, and the frontend uses TypeScript 6. `package.json` sets `overrides` so the generator uses the project's TypeScript and `npm ci` stays strict (no `--legacy-peer-deps`). The generator ran cleanly with TypeScript 6.0.3.
  - **Output:** `npm run generate:api` writes `src/app/core/api/generated/openapi.ts` from `../docs/api/openapi.json`. The file is committed, never edited, and excluded from Prettier.
  - **CI:** the `frontend` job regenerates the file and fails on `git diff --exit-code`.
  - **Client types:** `api.types.ts` derives the existing names from the generated schemas and operations. Because the contract has no required or nullable markers, it applies the nullability rules of `docs/api/README.md` in one small mapped type. `AppSummary` keeps `id`, `name` and `kind` non-null, as before.
  - **Mismatches:**
    - Mismatch 1 is fixed in the backend: the two records get distinct schema names (`SearchStorefront`, `DetailsStorefront`). The JSON on the wire doesn't change.
    - Mismatches 2–4 stay documented in the contract as they are and are handled in `api.types.ts`; `TokenResponse`, `FieldError` and the flat problem members stay hand-written. Describing them in OpenAPI (required and nullable markers, a problem schema customizer, a typed token response) is a later backend change, after which the hand-written parts can go.
  - Option 3 was rejected: it adds a much larger dependency and replaces working services. Option 4 was rejected: the Docker context doesn't include `docs/`, and reviewers should see type changes in the diff.
- **Consequences:**
  - A backend change to the public API now updates `docs/api/openapi.json` and the generated types in the same pull request; a field the client uses that disappears breaks `ng build`.
  - A Dependabot update of `openapi-typescript` that changes the output fails CI until the file is regenerated in that pull request.
  - When a later `openapi-typescript` release supports TypeScript 6 in its peer range, the `overrides` entry can be removed.
  - Presence and nullability are still a documented rule rather than part of the contract, until the backend describes them.
