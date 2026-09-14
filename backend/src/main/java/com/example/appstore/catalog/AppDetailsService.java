package com.example.appstore.catalog;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import com.github.benmanes.caffeine.cache.AsyncCache;
import org.springframework.stereotype.Service;

/**
 * App details use case: storefront policy first, then the {@code app-details} cache, which holds the whole
 * {@link LookupResult} so unknown ids are deduplicated and cached briefly too (ADR-0030). A {@link LookupResult.NotFound}
 * becomes {@link AppNotFoundException}.
 */
@Service
public class AppDetailsService {

    private final StorefrontPolicy storefrontPolicy;
    private final AppDetailsGateway gateway;
    private final AsyncCache<DetailsQuery, LookupResult> cache;

    public AppDetailsService(
            StorefrontPolicy storefrontPolicy,
            AppDetailsGateway gateway,
            AsyncCache<DetailsQuery, LookupResult> appDetailsCache) {
        this.storefrontPolicy = storefrontPolicy;
        this.gateway = gateway;
        this.cache = appDetailsCache;
    }

    /**
     * @throws com.example.appstore.catalog.storefront.InvalidCountryCodeException if {@code countryCode} is not a country
     * @throws com.example.appstore.catalog.storefront.UnsupportedStorefrontException if the country has no storefront
     * @throws AppNotFoundException if Apple has no result for the id in this storefront
     * @throws UpstreamException if the upstream call fails
     */
    public AppDetails details(String id, String countryCode, String languageTag, Platform platform) {
        String storefront = storefrontPolicy.requireSupported(countryCode);
        DetailsQuery query = new DetailsQuery(id, storefront, languageTag, platform);
        return switch (CacheSupport.getOrLoad(cache, query, () -> load(query))) {
            case LookupResult.Found found -> found.details();
            case LookupResult.NotFound ignored -> throw new AppNotFoundException(query.id());
        };
    }

    /** Runs once per upstream call, so the allowlist signal isn't repeated for every waiting caller. */
    private LookupResult load(DetailsQuery query) {
        try {
            return gateway.lookup(query);
        } catch (StorefrontNotServedException e) {
            storefrontPolicy.reportRejectedByApple(query.countryCode());
            throw e;
        }
    }
}
