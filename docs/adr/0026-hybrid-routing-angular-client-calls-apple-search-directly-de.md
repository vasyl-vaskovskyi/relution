# ADR-0026: Hybrid routing: Angular client calls Apple Search directly, details via our server

- **Status:** Open — do not implement until decided
- **Date:** 2026-09-13 (prep)

- **Context:**
  - **Only the iTunes Search API has a rate limit.** It is documented (~20/min) and observed (a 429 whose body names the source IP as the key).
  - **The MZStorePlatform lookup has no documented limit.** Apple's device-management "Request limits" (`maxRequestPerSecond` per location/sToken) apply to the authenticated Apps and Books API, not to this public lookup. A test showed no throttling: 150 distinct ids at about 126 req/min, all 200.
  - **Our server is the single IP for all of its users.** Routing search through it gives every user one shared ~20/min budget.
- **Proposal:** The browser calls `https://itunes.apple.com/search` directly, so each user uses their own IP. Details go through our server. The server keeps both endpoints, because the task requires them and API clients (curl/Bruno) use them.
- **Options:**
  1. **Hybrid:** browser → Apple Search; details via our server.
  2. **Hybrid with a server fallback:** try Apple directly, and retry via our server on failure.
  3. **Everything via our server** (current plan). Browser-direct search is mentioned only as a scaling option.
- **For option 1 or 2:**
  - Search load spreads across users' own IPs, so the per-IP limit stops being a server-wide bottleneck.
  - The server's search budget and cache are reserved for API clients.
  - A legitimate way to scale, unlike IP rotation (see ADR-0025).
- **Against / risks:**
  - **Users behind a shared office network (NAT)** share one IP and therefore one ~20/min budget. Relution's customers are organizations, so this is likely, and the server-side cache would have protected them.
  - **A 429 from Apple has no CORS header,** so the browser sees an opaque network error. It can't read `Retry-After` or tell a 429 from an outage.
  - **Search logic is duplicated in the client:** mapping Apple JSON, the storefront allowlist, and handling empty or odd results. Two implementations can drift apart.
  - **No server-side cache, logs or correlation ids for search.** Debugging relies on browser logs.
  - **Option 2 only:** the browser can't recognize a 429, so fallbacks could flood the server's shared budget exactly when Apple is throttling.
- **Questions for the discussion:**
  - How many users share an office IP at typical customers?
  - Is duplicating the mapping in the client acceptable?
  - Should the client share types or mappers with the server (e.g. a generated OpenAPI client for details only)?
- **Decision:** pending.
