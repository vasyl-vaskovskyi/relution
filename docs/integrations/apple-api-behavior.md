# Apple API behavior

Reconnaissance of the two upstream APIs, done on **2026-09-13** from a German IP. Each claim is tagged:
- **documented**: from Apple's docs.
- **observed**: from live calls. The captured responses are in [`stubs/`](../../stubs/README.md) until the Discovery Day, then in `backend/src/test/resources/wiremock/` ([ADR-0041](../adr/0041-move-captures-into-wiremock-and-remove-stubs.md)).
- **inferred**: reasoned from the above, not seen directly.

---

## 1. iTunes Search API

- **Docs:** https://performance-partners.apple.com/search-api
- **Endpoint:** `GET https://itunes.apple.com/search`

### 1.1 Parameters (documented)
| Param | Required | Default | Values |
|---|---|---|---|
| `term` | yes | – | URL-encoded text |
| `country` | yes | US | ISO 3166-1 alpha-2 (in practice: App Store storefronts only, see §4) |
| `media` | no | all | `software` for apps |
| `entity` | no | depends on media | for software: `software`, `iPadSoftware`, `desktopSoftware` |
| `limit` | no | 50 | 1–200 |
| `lang` | no | en_us | only `en_us` and `ja_jp` |
| `version`, `explicit`, `callback` | no | – | `callback` enables JSONP |

- **Rate limit (documented):** "approximately 20 calls per minute (subject to change)". Apple recommends caching.

### 1.2 Response (observed)
- `Content-Type: text/javascript; charset=utf-8`, but the body is JSON.
- `content-disposition: attachment; filename=1.txt`, `cache-control: max-age=86400`, `access-control-allow-origin: *`.

```
{ resultCount, results: [ { kind: "software"|"mac-software", trackId (number), trackName, bundleId,
  artistName, sellerName, artworkUrl60/100/512, price, currency, formattedPrice, version,
  averageUserRating, userRatingCount, primaryGenreName, genres[], description, releaseNotes,
  releaseDate, currentVersionReleaseDate (ISO), minimumOsVersion, supportedDevices[], … } ] }
```

- **Field presence** (1,088 results across 6 searches):
  - `sellerUrl` is present in about 86% of results.
  - `supportedDevices`, `features`, `advisories`, `isGameCenterEnabled`, `ipadScreenshotUrls` and `appletvScreenshotUrls` are **absent on every `mac-software` row**.
  - `releaseNotes` is missing on 5 iOS apps.
  - `screenshotUrls` can be `[]`.

### 1.3 Edge cases (observed)
| Case | Result |
|---|---|
| nonsense, empty, missing or 1 200-char `term` | 200 `{"resultCount":0,"results":[]}` |
| `term=äöü & co` (URL-encoded) | 200. An unencoded `&` would truncate the term |
| `country` without a storefront (`xx`, `cu`, `kp`, `aq`) | **400**, gzipped `{"errorMessage":"Invalid value(s) for key(s): [country]"}` |
| `entity=bogus` | **400**, and the error names the key `[resultEntity]` |
| `limit=500` / `200` | capped at ~180–200 results |
| `limit=0` | 200, 18 results (not zero) |
| `offset=0/5/10/200` | identical results, so **no pagination** |
| `limit=5` on term "app" | 4 results. Fewer than `limit` can come back |
| `lang=ja_jp` on `country=de` | German names. `lang` doesn't work outside JP |
| `term=WhatsApp` / `whatsapp` / `WHATSAPP` / `wHaTsApP` (2026-09-14) | Identical results in identical order: **term matching is case-insensitive** |
| `resultCount` vs `results.length` | equal in every capture. Iterate `results` anyway |

### 1.4 Rate limiting (observed)
- The first **429** came after about 36–43 rapid sequential calls, which is looser than the documented 20/min. The response came through Akamai (`x-cache: TCP_MISS`) from Apple's origin (`server: daiquiri/5`, `x-apple-partner: origin.0`). Where exactly the limiter runs is *inferred*, not known.
- **Not measured:** the exact threshold and time window under steady traffic. Two short bursts only show the limit is looser for bursts. Design for the documented ~20/min.
- The response is `HTTP/2 429`, `content-type: text/html`, **`retry-after: 30`**, with the body `Rate limit has been exceeded for: itunes-apple-com|general|<source IP>` and **no CORS header**.
- The limit is **per source IP**. A server-side integration therefore shares one budget across all of its clients.

---

## 2. MZStorePlatform lookup (Legacy)

- **Docs:** https://developer.apple.com/documentation/devicemanagement/getting-app-and-book-information-legacy. The URL in the task spec (`…/app_and_book_management/service_configuration/getting_app_and_book_information`) now returns **404**.
- **Context:** This is the `contentMetadataLookup` URL from the VPP Service-Config response ([VppClientConfigResponse](https://developer.apple.com/documentation/devicemanagement/vppclientconfigresponse)). MDM servers use it to show app and book metadata.

```
GET https://uclient-api.itunes.apple.com/WebObjects/MZStorePlatform.woa/wa/lookup
    ?version=2&id=361309726&p=mdm-lockup&caller=MDM&platform=enterprisestore&cc=de&l=de
```

### 2.1 Parameters
| Param | Source | Notes |
|---|---|---|
| `id` | documented | App or book id. Comma-separated lists work; result order is not preserved (observed) |
| `platform` | documented | `enterprisestore` (enterprise/B2B), `volumestore` (education), `macappstore` (Mac metadata). Enterprise and volume return **iOS** metadata for universal apps ([forum answer](https://developer.apple.com/forums/thread/711127)) |
| `cc`, `l` | example only | Country and language. No validation errors: unsupported values fall back silently (§2.4) |
| `p`, `caller`, `version` | example only | Undocumented. Missing `p` → 400 `{"status":7011}`; invalid `p` → 400 `{"status":7012}`; missing `caller` → **403**. `version` and `platform` can be omitted (observed) |
| cookie `itvt` | documented | The organization's sToken, for B2B/custom apps. **Out of scope. Never accept or forward it.** |

### 2.2 Response (observed; Apple documents it only through samples)
- `content-type: application/json`, `cache-control: max-age=900`, `access-control-allow-origin: *` on GET.
- The CORS preflight `OPTIONS` returns 200 **without** CORS headers, which is irrelevant server-side.
- Latency is ~0.4 s.
- **Rate limits:**
  - **Documented:** none for this lookup. The "Request limits" on Apple's [Service Config](https://developer.apple.com/documentation/devicemanagement/service-config) page (`maxRequestPerSecond` "for a location or sToken") and error `9646` / 429 in [Handling error responses](https://developer.apple.com/documentation/devicemanagement/handling-error-responses) apply to the authenticated Apps and Books API, not to this public URL.
  - **Observed:** 150 sequential calls with **150 distinct ids** at about 126 req/min all returned 200. There was no 429, no throttling header and no `x-cache` header, so the calls most likely reached Apple's servers rather than a CDN cache.
  - **Not tested:** higher or parallel rates, or long-running traffic. "No limit observed" does not mean there is no limit, and Apple can change this without notice (Legacy API).

```
{ isAuthenticated: false, version: 2,
  meta: { language: { tag: "de-de" }, storefront: { cc: "DE", id: "143443" } },
  results: { "<id>": {
    id: "361309726" (string live; number in doc sample), kind: "iosSoftware"|"desktopApp"|"epubBook",
    name, subtitle, artistName, bundleId, watchBundleId?, minimumOSVersion,
    releaseDate: "2010-05-26" (ISO, FIRST release), latestVersionReleaseDate: "9.09.2026" (LOCALIZED),
    deviceFamilies: ["mac","iphone","ipad",…], genreNames: [], description: { standard }, whatsNew,
    offers: [ { type: "get"|"buy", price, priceFormatted: "0,00 €", version: { display, externalId } } ],
    artwork: { url: ".../{w}x{h}bb.{f}", width, height, bgColor, … }, iconArtwork: { url: ".../{w}x{h}{c}.{f}" },
    softwareInfo: { seller, supportUrl, privacyPolicyUrl, eulaUrl, websiteUrl, … },
    userRating: { value, ratingCount, … }, url, shortUrl } } }
```

### 2.3 Documented semantics
- `isAuthenticated` is **always `false`**.
- An **empty `results`** means one of three things: the caller has no access to the item, the `id` is invalid, or authentication failed.
- A universal app has `kind == "iosSoftware"` and `mac` in `deviceFamilies`.
- `watchBundleId` marks an app that runs on Apple Watch. The prose spells it `watchBundleID`; the JSON uses `watchBundleId`.

### 2.4 Edge cases (observed)
| Case | Result |
|---|---|
| valid id | 200, `results["<id>"]` |
| unknown (`1`), non-numeric (`abc`) or missing id | **200 `{"results":{}}`**, never 404 |
| mixed `id=310633997,1,abc` | 200, only the valid id returned, with no signal about the misses |
| `cc=xx`, `cu`, `kp` or `aq`, or `cc` missing | 200, **silently served as `US` / `en-us`** |
| `cc=DE` (upper case) | accepted |
| `l=de`, `de-de` or `de_DE` on `cc=de` | `de-de` |
| `l=en` / `en-gb` on `cc=de` | `en-gb` |
| `l=fr`, `xx` or `1` on `cc=de` | **silently `de-de`** |
| Pages, `platform=enterprisestore` vs `macappstore` | version 15.3 / minOS 18.0 vs 15.3.1 / 15.6; `kind` stays `iosSoftware` |
| Final Cut Pro (Mac-only) | `kind: desktopApp`, `deviceFamilies: ["mac"]`. No `hasInAppPurchases`, `requiredCapabilities` or `watchBundleId` |
| ebook | `kind: epubBook`. No `bundleId`, `minimumOSVersion`, `deviceFamilies`, `whatsNew`, `softwareInfo` or offer version |

### 2.5 Doc vs. reality
| Topic | Doc sample 1 | Doc sample 2 (B2B / watch) | Live |
|---|---|---|---|
| `artwork` | **array** of `{width, height, url}` with concrete sized URLs | **object** with a `{w}x{h}bb.{f}` template URL (same as live) | **object** with a `{w}x{h}bb.{f}` template URL |
| `id` type | number | string | string |
| explicit `null`s | `privacyPolicyTextUrl`, `ageBand`, `circularArtwork`, … | `eulaUrl`, `websiteUrl`, `privacyPolicyTextUrl` | `softwareInfo.privacyPolicyTextUrl` is `null` in most live captures |

Doc sample 1 also has top-level `version: 1` (live: `2`), and its 216-wide artwork entry points to a `360x216bb.png` URL, so the declared size and the URL can disagree.

An artwork template URL that isn't substituted returns 404. Substituting `{w}`/`{h}` = 512, `{c}` = `bb` and `{f}` = `png` returns an image.

---

## 3. Gotchas a robust implementation must handle
1. **Search:** 200 with zero results for any bad term. A non-2xx status is not the only failure mode.
2. **Search:** `text/javascript` content type, with a gzipped 400 body.
3. **Search:** no pagination; `limit` is capped.
4. **Search:** 429 comes back as HTML with `Retry-After`, **per IP**. Cache, and limit outbound calls.
5. **MZ:** never returns 404. Empty `results` has three possible causes.
6. **MZ:** an unsupported `cc` silently becomes US. Check `meta.storefront.cc`.
7. **MZ:** an unsupported `l` is silently replaced. Report `meta.language.tag`.
8. **MZ:** `latestVersionReleaseDate` is localized. `releaseDate` is the *first* release.
9. **MZ:** `id` can be a string or a number.
10. **MZ:** the version lives at `offers[0].version.display`, and `offers` may be missing.
11. **MZ:** artwork comes in two observed shapes (an array of sized URLs, or an object with a template URL), and templates must be substituted.
12. **MZ:** fields depend on `kind`. Treat every field as optional, including explicit `null`.
13. **MZ:** `p` and `caller` are mandatory but undocumented. Pin them.
14. **MZ:** the API is Legacy and unversioned. Keep it behind one adapter and test against captured fixtures.

---

## 4. App Store storefront allowlist

- **Source:** App Store Connect Help, "App Store localizations". URL: https://developer.apple.com/help/app-store-connect/reference/app-information/app-store-localizations/
- **Seen 2026-09-13.** The page shows no "last updated" date.
- **Format:** 175 rows with ISO **alpha-3** codes, converted to alpha-2 with `Locale.getISO3Country()` mapping. Kosovo `XKS` (not ISO) → `xk`, which both APIs accept.
- **Rejected sources:**
  - The App Store Connect API `TerritoryCode` enum: 233 codes, including `CUB`, `CUW`, `GUM` and the obsolete `ANT`. `cw` and `gu` are rejected by both APIs.
  - The Apple Music API `/v1/storefronts`: needs a developer token.

**The 175 alpha-2 codes:**

```
ae,af,ag,ai,al,am,ao,ar,at,au,az,ba,bb,be,bf,bg,bh,bj,bm,bn,bo,br,bs,bt,bw,by,bz,ca,cd,cg,ch,ci,cl,cm,
cn,co,cr,cv,cy,cz,de,dk,dm,do,dz,ec,ee,eg,es,fi,fj,fm,fr,ga,gb,gd,ge,gh,gm,gr,gt,gw,gy,hk,hn,hr,hu,id,
ie,il,in,iq,is,it,jm,jo,jp,ke,kg,kh,kn,kr,kw,ky,kz,la,lb,lc,lk,lr,lt,lu,lv,ly,ma,md,me,mg,mk,ml,mm,mn,
mo,mr,ms,mt,mu,mv,mw,mx,my,mz,na,ne,ng,ni,nl,no,np,nr,nz,om,pa,pe,pg,ph,pk,pl,pt,pw,py,qa,ro,rs,ru,rw,
sa,sb,sc,se,sg,si,sk,sl,sn,sr,st,sv,sz,tc,td,th,tj,tm,tn,to,tr,tt,tw,tz,ua,ug,us,uy,uz,vc,ve,vg,vn,vu,
xk,ye,za,zm,zw
```

**Sample validation (2026-09-13).** 20 codes were checked; Search and MZ agreed with each other and with the list in every case:

| Codes | In list | Search | MZ `meta.storefront.cc` |
|---|---|---|---|
| de, us, gb, jp, ch, ua, ru, cn, xk, nr, ms, cd, ly | yes | 200 | same code |
| cu, kp, ir, sy, aq, cw, gu | no | 400 `[country]` | US (fallback) |

**Keeping the list current:** follow [Refresh the storefront allowlist](../operations/runbook.md#refresh-the-storefront-allowlist-every-6-months-or-after-an-allowlist-alert) in the runbook. At runtime, the service logs any mismatch ([`../architecture/error-handling.md`](../architecture/error-handling.md)).
