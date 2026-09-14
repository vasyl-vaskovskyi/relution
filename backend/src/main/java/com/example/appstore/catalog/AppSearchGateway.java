package com.example.appstore.catalog;

import java.util.List;

/** Port to the app search upstream, implemented by the Apple adapter in {@code integration.apple} (ADR-0028). */
public interface AppSearchGateway {

    /**
     * Searches apps in one storefront. An empty list means no results.
     *
     * @throws UpstreamException when the upstream call fails; never for "no results"
     */
    List<AppSummary> search(SearchQuery query);
}
