# ADR-0014: Optional `platform` on details

- **Status:** Superseded by [ADR-0029](0029-domain-terms-in-public-api.md) (`platform=ios|mac`)
- **Date:** 2026-09-13 (prep)

- **Context:** `enterprisestore` returns iOS metadata for universal apps, while `macappstore` returns Mac metadata (Pages: 15.3 vs 15.3.1).
- **Decision:** An optional `platform` parameter accepting `enterprisestore` (default) or `macappstore`. `volumestore` is left out.
- **Consequences:** It is part of the cache key. MDM-relevant, and cheap to support.
