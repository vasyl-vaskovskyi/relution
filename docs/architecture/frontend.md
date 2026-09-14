# Frontend

## Stack

- **Angular 22.1.x + Angular Material 22.1.x.** Standalone components, signals, zoneless change detection, functional HTTP interceptors (`provideHttpClient(withInterceptors(...))`).
- **Tests:** Vitest via `ng test` (`npm test -- --watch=false` for a single run).
- **Node:** 24 LTS, pinned in `.nvmrc`.
- **No extra npm dependencies** without the maintainer's approval (no NgRx, no UI kits besides Material).
- **Same-origin assets** ([ADR-0046](../adr/0046-frontend-assets-stay-same-origin-no-cdn-fonts-or-icons.md)): no Google Fonts or icon fonts. Text uses a system font stack set in the Material theme (`styles.scss`); icons are inline SVGs registered once in `core/icons/app-icons.ts` and used as `<mat-icon svgIcon="…">`.

## Structure

```
frontend/src/app/
├── core/
│   ├── api/        api.types.ts (mirrors docs/api/README.md DTOs + ProblemDetail), apps-api.service.ts
│   ├── auth/       auth.service.ts (token signal, in memory only), auth.interceptor.ts, auth.guard.ts
│   ├── debug/      debug-log.service.ts, debug-log.interceptor.ts
│   ├── locale/     locale.service.ts (browser locale → cc/l)
│   └── errors/     problem-message.ts (problem type → user message)
├── features/
│   ├── login/       login.component
│   ├── search/      search.component (field, result list, states)
│   └── app-details/ app-details.component
└── app.routes.ts    /login, /search, /apps/:id  (guard redirects to /login when there is no token)
```

`api.types.ts` is written by hand. Keeping it in sync with the API contract is a known maintenance risk; generating it from OpenAPI would remove that risk.

## Behavior

### Login
- The form sends the client id and secret to `POST /auth/token`.
- The token is kept **in memory only**, as a signal: never in localStorage or sessionStorage.
- The interceptor attaches the `Authorization` header. It doesn't add one to `/auth/token`.
- **A 401 from any endpoint except `/auth/token`** clears the token and redirects to `/login`, keeping the return URL.
- **A 401 from `/auth/token`** keeps the user on the form and shows "Invalid client id or secret."

### Locale pre-fill (`LocaleService`)
- Take the first `navigator.languages` entry that has a region: `de-DE` gives `cc=de`, `l=de`.
- If no entry has a region, set `l` from the language and use `cc=us`.
- Both values are editable, and the pre-fill result is logged.

### Search
- Debounced input (400 ms, minimum 2 characters), sent with `switchMap` so outdated requests are cancelled. This also protects Apple's rate budget.
- `term` and `cc` are kept in the URL query parameters.
- Each result shows the icon (`alt` = app name), the name and the developer.

### States (search and details)
- **Loading:** a progress bar, plus the hint "Still loading, the App Store is slow" after 3 s.
- **No results:** a message that includes the term.
- **Error:** a message mapped from the problem type, plus a **Retry** button.

### Details
- Shows the icon, name, developer, bundle id, version, price, platforms, minimum OS, description, what's new and links.
- Apple texts are rendered as text, never via `[innerHTML]`.
- **Platform toggle:** iOS / Mac, sent as `platform=ios|mac`.
- When `storefront.language` differs from the requested `l`, a chip shows "Requested fr · served de-de".

### Error messages (`problem-message.ts`)

Messages are chosen by **problem type** first ([ADR-0029](../adr/0029-domain-terms-in-public-api.md)):

| Problem type | Message / behavior |
|---|---|
| `invalid-request` | "Please check your input." plus field messages from `errors[]` |
| `unsupported-storefront` | "The App Store is not available in this country." |
| `app-not-found` | "App not found in this storefront." |
| `upstream-unavailable` | "Too many requests, try again in {Retry-After} s." |
| `upstream-timeout`, `upstream-error` | "The App Store is currently unavailable." |
| `unauthorized` | From `/auth/token`: "Invalid client id or secret." Otherwise the interceptor redirects to login. |
| `forbidden` | "You don't have access to this function." |
| `internal` | "Something went wrong. Please try again." |
| unknown type | Fallback by status: 4xx → "Please check your input.", 5xx → "The App Store is currently unavailable." |
| status 0 (network) | "Cannot reach the server." |

## Debug logging

`DebugLogService`, see [ADR-0020](../adr/0020-central-debug-logging-in-the-frontend.md) and [ADR-0034](../adr/0034-security-and-privacy-hardening.md).

- **All console output goes through this service.** There are no direct `console.*` calls elsewhere.
- **Build configurations** (create the environment files with `ng generate environments`):

  | Configuration | `debugLogging` | Runtime override `localStorage['appstore.debug']` |
  |---|---|---|
  | `development` | true | no |
  | `demo` (used by local compose) | true | yes |
  | `production` | false | no |

  `demo` = production optimizations (no source maps) with `debugLogging: true`; the runtime switch accepts `localStorage.setItem('appstore.debug', 'true' | 'false')`.
  In `src/environments/`, the two columns are the flags `debugLogging` and `debugLoggingOverride`. Build or serve with `ng build --configuration demo` / `ng serve --configuration demo`.

- **Per request,** `debug-log.interceptor.ts` logs one `console.groupCollapsed` titled `[appstore] GET /api/v1/apps 200 143ms`, containing:
  - the parameters, without the term value (only its length);
  - `X-Correlation-Id`;
  - the item count or the ProblemDetail;
  - `Retry-After`, if present.
- **Also logged:** auth state changes, the locale pre-fill, navigation to details and platform toggles.
- **Never logged:** tokens, the `Authorization` header, credentials, search terms.

## Serving

See [ADR-0019](../adr/0019-serve-the-frontend-from-an-nginx-container-same-origin.md).

- **Docker:** `nginxinc/nginx-unprivileged` (listens on 8080) serves the build. It proxies `/api/` and `/auth/` to `app:8080` on the same origin, so no CORS is needed. It also sends the security headers from [`security.md`](security.md).
- **Local development:** `npm start` runs `ng serve` with `proxy.conf.json`, which forwards `/api` and `/auth` to `http://localhost:8080`.

## Tests

See [ADR-0023](../adr/0023-frontend-scope-and-tests.md).

1. **`auth.interceptor`:** attaches the bearer token, adds no header to `/auth/token`, a 401 from an API call clears the token and navigates to `/login`, and a 401 from `/auth/token` doesn't redirect.
2. **`locale.service`:** `de-DE` → de/de, `en` without a region → us/en, and the first entry with a region wins.
3. **`problem-message`:** every row of the error table, including the unknown-type fallback and `Retry-After`.

Components and templates aren't unit-tested; the demo covers them.
