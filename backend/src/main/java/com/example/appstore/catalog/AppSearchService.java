package com.example.appstore.catalog;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import com.github.benmanes.caffeine.cache.AsyncCache;
import org.springframework.stereotype.Service;

/**
 * App search use case: storefront policy first, so invalid input never costs Apple budget or cache space, then the
 * {@code app-search} cache. Concurrent identical searches share one upstream call; failures are never cached.
 */
@Service
public class AppSearchService {

    private final StorefrontPolicy storefrontPolicy;
    private final AppSearchGateway gateway;
    private final AsyncCache<SearchCacheKey, AppSearchResult> cache;

    public AppSearchService(
            StorefrontPolicy storefrontPolicy,
            AppSearchGateway gateway,
            AsyncCache<SearchCacheKey, AppSearchResult> appSearchCache) {
        this.storefrontPolicy = storefrontPolicy;
        this.gateway = gateway;
        this.cache = appSearchCache;
    }

    /**
     * @throws com.example.appstore.catalog.storefront.InvalidCountryCodeException if {@code countryCode} is not a country
     * @throws com.example.appstore.catalog.storefront.UnsupportedStorefrontException if the country has no storefront
     * @throws UpstreamException if the upstream call fails
     */
    public AppSearchResult search(String term, String countryCode, int limit) {
        String storefront = storefrontPolicy.requireSupported(countryCode);
        SearchQuery query = new SearchQuery(term, storefront, limit);
        return CacheSupport.getOrLoad(cache, SearchCacheKey.of(query), () -> load(query));
    }

    /** Runs once per upstream call, so the allowlist signal isn't repeated for every waiting caller. */
    private AppSearchResult load(SearchQuery query) {
        try {
            return new AppSearchResult(gateway.search(query), query.countryCode());
        } catch (StorefrontNotServedException e) {
            storefrontPolicy.reportRejectedByApple(query.countryCode());
            throw e;
        }
    }
}
