package com.example.appstore.catalog;

import java.util.List;
import java.util.Objects;

/** Search results for one storefront; {@code countryCode} is the normalized code the search ran against. */
public record AppSearchResult(List<AppSummary> items, String countryCode) {

    public AppSearchResult {
        items = List.copyOf(items);
        Objects.requireNonNull(countryCode, "countryCode");
    }
}
