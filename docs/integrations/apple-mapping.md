# Mapping Apple responses to the domain

These rules are implemented as pure functions in `integration.apple`. Every rule is backed by a fixture test. The observed Apple behavior they are based on is in [`apple-api-behavior.md`](apple-api-behavior.md).

## Requests

| Our parameter | Search API | Lookup API |
|---|---|---|
| `term` | `term` (URL-encoded) | — |
| `cc` | `country` | `cc` |
| `l` | — (Search's `lang` only supports `en_us`/`ja_jp`) | `l` |
| `limit` | `limit` | — |
| `platform=ios` / `mac` | — | `platform=enterprisestore` / `macappstore` |
| fixed | `media=software&entity=software` | `version=2&p=mdm-lockup&caller=MDM` |

## General rules

1. **Every field is optional and null-tolerant.** A missing key and an explicit `null` are handled the same way.
2. **Raw records use boxed types only** and match Apple's key names exactly (e.g. `minimumOSVersion`). A wrong name silently maps to `null`, so every mapped field has a fixture assertion.
3. **Strings are trimmed**; a blank string becomes `null`.
4. **Ids** are normalized to `String`. Search's `trackId` is a number. The lookup `id` is a string in live responses and a number in Apple's doc samples.
5. **Unknown enum values** are ignored or mapped to `OTHER`. The mapper never throws.

## Lookup responses

| Domain field | Source |
|---|---|
| `name`, `subtitle` | `name`, `subtitle` |
| `developer` / `seller` | `artistName` / `softwareInfo.seller` |
| `bundleId`, `watchBundleId` | `bundleId`, `watchBundleId` |
| `version` | `offers[0].version.display` (`null` if `offers` is missing or empty) |
| `price.amount` / `formatted` | `offers[0].price` as `BigDecimal` / `offers[0].priceFormatted` |
| `minimumOsVersion` | `minimumOSVersion` |
| `firstReleaseDate` | `releaseDate` (ISO). **Never** parse `latestVersionReleaseDate`, which is a localized display string |
| `description`, `whatsNew` | `description.standard`, `whatsNew` |
| `genres` | `genreNames` |
| `rating` | `userRating.value`, `userRating.ratingCount` |
| `links` | `url`, `softwareInfo.supportUrl`, `softwareInfo.privacyPolicyUrl` |
| `storefront` | `meta.storefront.cc` (lower-cased), `meta.language.tag`, requested `platform` |

- **Results location:** the result is `results["<requested id>"]`. An empty `results`, or a missing requested id, becomes `LookupResult.NotFound`.
- **Storefront check:** if `meta.storefront.cc` differs from the requested `cc` (case-insensitive), the client throws `StorefrontNotServedException`.

## Search responses

| Domain field | Source |
|---|---|
| `id`, `name` | `trackId`, `trackName` (rows missing either are dropped and logged at DEBUG) |
| `developer` | `artistName` |
| `iconUrl` | `artworkUrl512`, falling back to `artworkUrl100` |
| `price` | `price` as `BigDecimal`, `currency`, `formattedPrice` |
| `rating` | `averageUserRating`, `userRatingCount` |

Search's `Content-Type` is `text/javascript`, so the client parses the body as JSON regardless of that header.

## Artwork

| Shape | Seen in | Rule |
|---|---|---|
| Array of `{width, height, url}` with concrete URLs | Apple doc sample 1 | Pick the largest `width` ≤ 512. The declared size and the URL can disagree (`216` → `360x216bb.png`) |
| Object with a template `url` (`{w}x{h}bb.{f}` or `{w}x{h}{c}.{f}`) | live responses, doc sample 2 | Substitute `{w}` and `{h}` with 512, `{c}` with `bb`, `{f}` with `png` |
| Object with a concrete `url` | *inferred*, never seen | Use it as is. Tested with a hand-made variant |

Never emit a URL that contains `{` or `}`. No usable artwork → `iconUrl = null`.

## Kind and platforms

| Source | Apple `kind` | `AppKind` |
|---|---|---|
| Search | `software` / `mac-software` | `IOS_APP` / `MAC_APP` |
| Lookup | `iosSoftware` / `desktopApp` | `IOS_APP` / `MAC_APP` |
| either | anything else (`ebook`, `epubBook`, …) | `OTHER` |

- **`deviceFamilies` → `platforms`:** `iphone`→`IPHONE`, `ipad`→`IPAD`, `ipod`→`IPOD`, `mac`→`MAC`, `watch`→`WATCH`, `tvos`→`TV`. Other values are ignored and logged at DEBUG.
- **`universal`:** true when `kind` is `iosSoftware` and `deviceFamilies` contains `mac`.

## Drift signals ([ADR-0037](../adr/0037-legacy-api-drift-detection.md))

When a field that is present in every capture is missing from a live response, the mapper increments `appstore.apple.mapping.missing_field{api, field}`. The monitored fields:
- Search: `trackId`, `trackName`
- Lookup, for app kinds: `name`, `kind`, `artwork`. `offers` is excluded, because it can legitimately be missing for unavailable items.
