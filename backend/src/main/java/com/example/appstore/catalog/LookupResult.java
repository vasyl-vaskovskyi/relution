package com.example.appstore.catalog;

import java.util.Objects;

/**
 * The outcome of a details lookup, cached as a whole: {@link Found} and {@link NotFound} get different TTLs (ADR-0030).
 * Failures are exceptions and never a {@code LookupResult}.
 */
public sealed interface LookupResult permits LookupResult.Found, LookupResult.NotFound {

    record Found(AppDetails details) implements LookupResult {

        public Found {
            Objects.requireNonNull(details, "details");
        }
    }

    /**
     * Apple returned no result for the id: it doesn't exist, isn't available in the storefront, or isn't accessible.
     */
    record NotFound() implements LookupResult {}
}
