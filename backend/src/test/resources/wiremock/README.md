# WireMock test data: captured Apple responses

These are real Apple API responses, captured on **2026-09-13** from a German IP, plus two samples copied from Apple's
documentation. They are the single copy of the test data: tests own them, and there is no archive elsewhere
([ADR-0041](../../../../../docs/adr/0041-move-captures-into-wiremock-and-remove-stubs.md)). They moved here from the
former `stubs/` folder at the start of the Search block. Background:
[`docs/integrations/apple-api-behavior.md`](../../../../../docs/integrations/apple-api-behavior.md).

```
wiremock/
├── README.md               this file
├── __files/apple/search/   response bodies
├── __files/apple/lookup/   response bodies, incl. doc-sample-*.json
└── mappings/apple/search/  captures whose status and headers matter (429)
```

## Naming convention

```
<api>/<http-status>-<scenario>[-<variant>][-<cc>].json
```

- `api`: `search` (iTunes Search API) or `lookup` (MZStorePlatform lookup).
- `http-status`: the status Apple actually returned. Note that most lookup "failures" come back as 200.
- `scenario`: kebab-case description of what the file demonstrates.
- `variant` (optional): what distinguishes it from a sibling file, e.g. the `platform` value.
- `cc` (optional): the storefront the response was **served** from.
- Bodies live in `__files/`. A capture whose status and headers matter is a mapping in `mappings/` with the same name.
- Exception: samples copied from Apple's documentation are named `doc-sample-<scenario>.json` (no HTTP status, because they were never returned by an API).
- Hand-made variants are named `synthetic-<scenario>.json`, so they're never mistaken for captures.

## Trimming

- Kept: the fields the service maps, plus `id`, `kind`, `meta` and fields that demonstrate documented quirks (e.g. `supportedDevices`, `features`, `latestVersionReleaseDate`, `iconArtwork`, `ageBand`, `circularArtwork`).
- Keys Apple omitted stay absent. Explicit `null`s stay `null`.
- Texts are cut to 120 characters (search descriptions) or 160 characters (lookup descriptions and "what's new"), ending in `…`.
- Search result lists are cut to 2 rows.
- HTTP headers are removed from bodies, and gzipped bodies are decompressed. Mappings keep only the headers the service reads.
- Our source IP was replaced with `203.0.113.10`, an address range reserved for documentation.

## Search — `GET https://itunes.apple.com/search?media=software&…`

| File | Request params | Demonstrates |
|---|---|---|
| `__files/apple/search/200-apps-de.json` | `term=relution&entity=software&country=de&limit=5` | Normal result (2 iOS rows kept) |
| `__files/apple/search/200-mixed-ios-mac-de.json` | `entity=software&country=de` (term not recorded) | A `mac-software` row **without** `supportedDevices`/`features` next to a `software` row that has them |
| `__files/apple/search/200-no-results-de.json` | `term=<nonsense>&entity=software&country=de` | 200 with `resultCount: 0` and no error |
| `__files/apple/search/400-invalid-country.json` | `country=xx` | `errorMessage: "Invalid value(s) for key(s): [country]"`. Valid ISO codes without a storefront (e.g. `cu`) get the same response |
| `__files/apple/search/400-invalid-entity.json` | `entity=bogus` | The error names the key `[resultEntity]` |
| `mappings/apple/search/429-rate-limited.json` | burst of about 40 calls | Status 429, `text/html` body, `Retry-After: 30`, limited per source IP. The mapping answers requests with `term=rate-limited`; tests can register their own stub with the same response instead |

## Lookup — `GET https://uclient-api.itunes.apple.com/WebObjects/MZStorePlatform.woa/wa/lookup?version=2&p=mdm-lockup&caller=MDM&…`

All files are in `__files/apple/lookup/`.

| File | Request params | Demonstrates |
|---|---|---|
| `200-universal-app-enterprisestore-de.json` | `id=361309726&platform=enterprisestore&cc=de&l=de` | Universal app (Pages): iOS metadata, version 15.3, minOS 18.0 |
| `200-universal-app-macappstore-de.json` | `id=361309726&platform=macappstore&cc=de&l=de` | Same app with Mac metadata: version 15.3.1, minOS 15.6. `kind` stays `iosSoftware` |
| `200-ios-app-with-watch-de.json` | `id=310633997&platform=enterprisestore&cc=de&l=de` | WhatsApp: `watchBundleId` present; `releaseDate` is the **first** release (2009) |
| `200-mac-only-app-de.json` | `id=424389933&platform=enterprisestore&cc=de&l=de` | Final Cut Pro: `kind: desktopApp`, `deviceFamilies: ["mac"]`, paid offer |
| `200-ebook-de.json` | `id=492186116&platform=enterprisestore&cc=de&l=de` | `kind: epubBook`, with no `bundleId`, `minimumOSVersion`, `deviceFamilies` or offer version |
| `200-empty-results-de.json` | `id=1&platform=enterprisestore&cc=de&l=de` | `results: {}`. This is how "not found" looks (never a 404) |
| `200-storefront-fallback-us.json` | `id=310633997&cc=xx` | Unsupported `cc` **silently served as `US` / `en-us`** |
| `200-language-fallback-fr-de.json` | `id=361309726&platform=enterprisestore&cc=de&l=fr` | Unsupported `l` **silently served as `de-de`** |
| `doc-sample-artwork-array.json` | Apple doc sample 1 | `artwork` as an **array** of sized URLs (the 216-wide entry points to `360x216bb.png`), `id` as a **number**, top-level `version: 1`, explicit `null`s |
| `doc-sample-artwork-object-watch.json` | Apple doc sample 2 (B2B app) | `artwork` as an **object with a template URL** (same as live), `id` as a string, `watchBundleId`, explicit `null`s in `softwareInfo` |

To refresh a file, follow [Refresh captures and fixtures](../../../../../docs/operations/runbook.md#refresh-captures-and-fixtures) in the runbook.
