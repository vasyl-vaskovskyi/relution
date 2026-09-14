package com.example.appstore.catalog;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import org.springframework.stereotype.Service;

/**
 * App search use case: storefront policy first, so invalid input never costs Apple budget, then the gateway. Caching and
 * request deduplication wrap this flow in the caching and resilience block.
 */
@Service
public class AppSearchService {

    private final StorefrontPolicy storefrontPolicy;
    private final AppSearchGateway gateway;

    public AppSearchService(StorefrontPolicy storefrontPolicy, AppSearchGateway gateway) {
        this.storefrontPolicy = storefrontPolicy;
        this.gateway = gateway;
    }

    /**
     * @throws com.example.appstore.catalog.storefront.InvalidCountryCodeException if {@code countryCode} is not a country
     * @throws com.example.appstore.catalog.storefront.UnsupportedStorefrontException if the country has no storefront
     * @throws UpstreamException if the upstream call fails
     */
    public AppSearchResult search(String term, String countryCode, int limit) {
        String storefront = storefrontPolicy.requireSupported(countryCode);
        SearchQuery query = new SearchQuery(term, storefront, limit);
        try {
            return new AppSearchResult(gateway.search(query), storefront);
        } catch (StorefrontNotServedException e) {
            storefrontPolicy.reportRejectedByApple(storefront);
            throw e;
        }
    }
}
