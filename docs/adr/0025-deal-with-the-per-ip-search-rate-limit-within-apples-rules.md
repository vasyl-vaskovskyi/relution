# ADR-0025: Deal with the per-IP Search rate limit within Apple's rules

- **Status:** Accepted; amended by [ADR-0045](0045-search-budget-is-configuration-and-the-outbound-limiter-is-core.md) (more requests can be bought; outbound limiter is core)
- **Date:** 2026-09-13 (prep)

- **Context:**
  - **The limit:** Apple documents ~20 Search calls/min, "subject to change". The observed 429 body names the key it counts against: `itunes-apple-com|general|<source IP>`.
  - **Impact:** every user of our server shares one IP budget.
  - **Uncertain:** the exact threshold and time window under steady traffic. Bursts of 36–43 calls passed before the first 429.
  - **The question:** could requests be routed through a proxy service with rotating IPs?
- **Options:**
  1. Rotate IPs through a proxy provider.
  2. Cache, deduplicate identical requests, debounce input, and limit our own outbound calls.
  3. Let the browser call Search directly. Apple sends `Access-Control-Allow-Origin: *`, so each end user uses their own IP.
  4. Ask Apple about higher limits.
- **Decision:** **No IP rotation.**
  - It deliberately circumvents a published provider limit, where Apple's own guidance is to cache.
  - Apple may block the IP ranges we use.
  - A third party would see all of our traffic.
  - It is a poor signal for an MDM vendor that depends on Apple.

  Instead we use option 2: cache and single-flight dedup, with the outbound limiter as a stretch goal (docs/architecture/caching-resilience.md).
- **Consequences:**
  - Throughput is bounded to about 20 unique, uncached searches per minute per server IP. Details lookups are unaffected: no 429 seen.
  - To raise in the presentation as next steps:
    - serve stale cached results on 429;
    - share the cache across instances (e.g. Redis);
    - per-client fairness in the limiter;
    - client-direct Search (option 3). It scales naturally, but it bypasses our validation, error translation and cache. Now tracked as an **open question** in ADR-0026.
- **Research (2026-09-13):**
  - **Can the server tell Apple which user a request is for?** No. The Search API has no API key, account or user field, and the limit is counted per connecting IP. The only ways to look like different users are forged headers (`X-Forwarded-For`) or IP rotation. Both circumvent the limit, and forged headers most likely don't work (*inferred*: the key in the 429 body is the connecting IP). Rejected. Not tested.
  - **Does Apple offer a paid tier?** No. The Search API docs point heavy users to the [Enterprise Partner Feed](https://performance-partners.apple.com/epf). That is a bulk affiliate feed which today covers music and TV & movie downloads, and its page doesn't mention apps. Not a fit.
  - **Apple Ads Platform API** ([Search Apps](https://developer.apple.com/documentation/apple-ads-platform-api/search-apps-endpoints)): searches the full catalog (up to 1,000 results, with paging), but requires an Apple Ads account with authentication. It is intended for campaign setup and has its own rate limits. Using it as a general app search is off-purpose and likely against its terms. Not suitable.
  - **Paid third-party APIs:** [42matters](https://42matters.com/docs/app-market-data/ios/apps/search), [AppTweak](https://www.apptweak.com/en/app-store-api) (from ~$79/mo) and SerpApi (from ~$25/mo for 1,000 searches; prices per [a July 2026 comparison](https://www.socialcrawl.dev/blog/best-app-review-apis-2026)). Legitimate, but they add a vendor dependency, possibly scraped or delayed data, license terms and cost. A business decision, not part of this challenge.
  - **Legitimate ideas for the presentation** (none change the build plan):
    1. **Own search index:** store app metadata from details lookups (no known limit) plus the apps the organization already manages, and search it locally (e.g. Postgres full-text). Call Apple's Search API only for apps we don't know yet. This is the strongest idea for an MDM product.
    2. Serve stale cached results on a 429.
    3. A cache shared across instances (e.g. Redis).
    4. Debounce the input and require a minimum term length (the planned client uses 400 ms and 2 characters, docs/architecture/frontend.md), plus single-flight dedup.
    5. Per-tenant fair queueing with a clear retry message.
    6. Ask Apple for guidance through Relution's MDM vendor relationship.
    7. Browser-direct search (ADR-0026).
  - **Note:** separate deployment regions naturally have separate egress IPs, which is normal architecture. Adding egress IPs only to multiply the quota is IP rotation in disguise.
