# API contract (v1)

This document is the contract until the service exists. After that, the generated OpenAPI document is authoritative, and this file only describes the policies that OpenAPI can't express.

- **OpenAPI:** [`/v3/api-docs`](http://localhost:8080/v3/api-docs); **Swagger UI:** [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html). Get a token from `POST /auth/token`, then use **Authorize** (bearer JWT). Both are disabled in the `prod` profile ([`../operations/configuration.md`](../operations/configuration.md#profiles)).
- **Contract snapshot:** [`openapi.json`](openapi.json) is the committed, normalized copy of the generated document. A backend test fails when they differ, so API changes show up in pull request diffs. The update command is in [`../development/testing.md`](../development/testing.md#update-the-openapi-contract-snapshot).

## Conventions

- **Base path:** `/api/v1`. **Media type:** `application/json`; errors use `application/problem+json`.
- **Authentication:** `Authorization: Bearer <jwt>` with scope `apps:read` on every `/api/v1/**` request ([`../architecture/security.md`](../architecture/security.md)).
- **Correlation:** every response carries `X-Correlation-Id`. You may send your own id; the format and precedence rules are in [`../architecture/error-handling.md`](../architecture/error-handling.md#correlation-id).
- **Nullability:** in `AppDetails`, `id`, `name`, `kind`, `storefront` and `universal` are never `null`. Arrays (`platforms`, `genres`) are never `null` either; they are `[]` when unknown. Every other field may be `null`.

### Compatibility rules

These make the API safe to evolve.
- **Additive changes stay in `v1`:** new optional fields, new enum values, new problem types.
- **Clients must ignore unknown fields** and tolerate unknown enum values (`kind`, `platforms`) and unknown problem types. `OTHER` may be split into new values later.
- **Breaking changes** (removing or renaming fields, changing types or semantics) require `/api/v2`. `v1` then stays available during a documented deprecation period.

## `POST /auth/token`

HTTP Basic authentication with the client id and secret.

```
200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }
401 application/problem+json  (type urn:appstore:problem:unauthorized)
```

## `GET /api/v1/apps`: search

| Parameter | Required | Rules |
|---|---|---|
| `term` | yes | Trimmed, 1–100 characters, not blank |
| `cc` | yes | 2-letter storefront country code, case-insensitive (normalized to lower case) |
| `limit` | no | 1–50, default 25 |

```
200 {
  "items": [
    { "id": "361309726", "name": "Pages: Erstelle Dokumente", "developer": "Apple",
      "iconUrl": "https://is1-ssl.mzstatic.com/…/512x512bb.jpg", "kind": "IOS_APP",
      "price": { "amount": "0.00", "currency": "EUR", "formatted": "Gratis" },
      "rating": { "average": 4.1, "count": 12345 } }
  ],
  "count": 1,
  "storefront": { "cc": "de" }
}
```

- The example values are illustrative.
- `count` equals `items.length`.
- There is no pagination, because Apple ignores `offset` ([ADR-0013](../adr/0013-search-limit-1-50-no-pagination.md)).

## `GET /api/v1/apps/{id}`: details

| Parameter | Required | Rules |
|---|---|---|
| `id` (path) | yes | Digits only, 1–15 characters |
| `cc` | yes | As for search |
| `l` | yes | Language tag, format `^[a-zA-Z]{2}([-_][a-zA-Z]{2})?$`. Apple decides which language it serves (see `storefront.language`) |
| `platform` | no | `ios` (default) or `mac`. For universal apps, selects iOS or Mac metadata |

```
200 {
  "id": "361309726", "name": "Pages: Erstelle Dokumente", "kind": "IOS_APP", "subtitle": "…",
  "developer": "Apple", "seller": "Apple Inc.",
  "bundleId": "com.apple.Pages", "watchBundleId": null,
  "version": "15.3", "minimumOsVersion": "18.0", "firstReleaseDate": "2010-05-26",
  "price": { "amount": "0.00", "currency": null, "formatted": "0,00 €" },
  "platforms": ["IPHONE", "IPAD", "MAC"], "universal": true,
  "description": "…", "whatsNew": "…",
  "iconUrl": "https://is1-ssl.mzstatic.com/…/512x512bb.png", "genres": ["Produktivität"],
  "rating": { "average": 4.1, "count": 12345 },
  "links": { "store": "https://apps.apple.com/…", "support": null, "privacyPolicy": "https://…" },
  "storefront": { "cc": "de", "language": "de-de", "platform": "ios" }
}
```

### Field notes

| Field | Note |
|---|---|
| `price.amount` | Decimal as a **string** (e.g. `"0.99"`), so floating-point numbers never distort prices |
| `price.currency` | `null` on details, because Apple's lookup API has no currency field. `formatted` is localized by Apple |
| `firstReleaseDate` | The app's first release date, not the current version's |
| `universal` | Convenience boolean, never `null`: `kind` is `IOS_APP` and `platforms` contains `MAC` (`false` when unknown) |
| `storefront.language` | The language Apple **served**, which may differ from the requested `l` |
| `kind` | `IOS_APP`, `MAC_APP`, `OTHER` (extensible) |
| `platforms` | `IPHONE`, `IPAD`, `IPOD`, `MAC`, `WATCH`, `TV` (extensible) |

## Errors

Every error is an RFC 9457 ProblemDetail with a `correlationId` member.

```
{ "type": "urn:appstore:problem:invalid-request", "title": "Invalid request", "status": 400,
  "detail": "One or more parameters are invalid.", "instance": "/api/v1/apps",
  "correlationId": "5f1c…", "errors": [ { "field": "cc", "message": "must be a supported storefront country code" } ] }
```

| Type (`urn:appstore:problem:…`) | Status | Meaning | What clients should do |
|---|---|---|---|
| `invalid-request` | 400 | Parameters failed validation; see `errors[]` | Fix the input |
| `unsupported-storefront` | 400 | Apple has no store for this country | Choose another `cc` |
| `app-not-found` | 404 | The app doesn't exist, isn't available in this storefront, or isn't accessible | Don't retry immediately |
| `unauthorized` | 401 | Token missing, invalid or expired, or bad client credentials | Get a new token |
| `forbidden` | 403 | Token lacks the required scope | — |
| `upstream-unavailable` | 503 | Apple rate limit reached; `Retry-After` is set | Retry after the given seconds |
| `upstream-timeout` | 504 | Apple didn't answer in time | Retry later |
| `upstream-error` | 502 | Apple failed or returned an unexpected response | Retry later |
| `internal` | 500 | Unexpected server error | Report it with the `correlationId` |

How the service decides which type to return is in [`../architecture/error-handling.md`](../architecture/error-handling.md).
