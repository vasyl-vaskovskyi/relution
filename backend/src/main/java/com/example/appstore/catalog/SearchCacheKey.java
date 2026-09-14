package com.example.appstore.catalog;

import java.util.Locale;

/**
 * Key of the {@code app-search} cache. The term is lower-cased because Apple's search is case-insensitive; Apple itself
 * still receives the trimmed original term ({@code docs/architecture/caching-resilience.md#caches}).
 */
public record SearchCacheKey(String term, String countryCode, int limit) {

    public static SearchCacheKey of(SearchQuery query) {
        return new SearchCacheKey(query.term().toLowerCase(Locale.ROOT), query.countryCode(), query.limit());
    }
}
