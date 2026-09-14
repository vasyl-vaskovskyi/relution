package com.example.appstore.catalog;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import org.springframework.stereotype.Service;

/**
 * App details use case: storefront policy first, then the gateway. A {@link LookupResult.NotFound} becomes
 * {@link AppNotFoundException}. Caching wraps this flow in the caching and resilience block.
 */
@Service
public class AppDetailsService {

    private final StorefrontPolicy storefrontPolicy;
    private final AppDetailsGateway gateway;

    public AppDetailsService(StorefrontPolicy storefrontPolicy, AppDetailsGateway gateway) {
        this.storefrontPolicy = storefrontPolicy;
        this.gateway = gateway;
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
        LookupResult result;
        try {
            result = gateway.lookup(query);
        } catch (StorefrontNotServedException e) {
            storefrontPolicy.reportRejectedByApple(storefront);
            throw e;
        }
        return switch (result) {
            case LookupResult.Found found -> found.details();
            case LookupResult.NotFound ignored -> throw new AppNotFoundException(query.id());
        };
    }
}
