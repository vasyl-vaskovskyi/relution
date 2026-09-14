package com.example.appstore.catalog;

import java.util.Objects;

/**
 * Apple rejected a storefront that is on our allowlist (Search 400 {@code [country]}), or silently served another one
 * (lookup {@code meta.storefront.cc}). {@code servedCountryCode} is {@code null} when Apple rejected the request.
 */
public final class StorefrontNotServedException extends UpstreamException {

    private final String requestedCountryCode;
    private final String servedCountryCode;

    public StorefrontNotServedException(String requestedCountryCode, String servedCountryCode) {
        super(
                "Apple did not serve storefront " + requestedCountryCode
                        + (servedCountryCode == null ? "" : " (served " + servedCountryCode + ")"),
                null);
        this.requestedCountryCode = Objects.requireNonNull(requestedCountryCode, "requestedCountryCode");
        this.servedCountryCode = servedCountryCode;
    }

    public String requestedCountryCode() {
        return requestedCountryCode;
    }

    public String servedCountryCode() {
        return servedCountryCode;
    }
}
