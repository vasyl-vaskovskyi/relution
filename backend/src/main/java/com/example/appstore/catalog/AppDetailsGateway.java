package com.example.appstore.catalog;

/** Port to the app details upstream, implemented by the Apple adapter in {@code integration.apple} (ADR-0028). */
public interface AppDetailsGateway {

    /**
     * Looks up one app. "Not found" is a {@link LookupResult.NotFound}, not an exception.
     *
     * @throws UpstreamException when the upstream call fails, including a storefront Apple didn't serve
     */
    LookupResult lookup(DetailsQuery query);
}
