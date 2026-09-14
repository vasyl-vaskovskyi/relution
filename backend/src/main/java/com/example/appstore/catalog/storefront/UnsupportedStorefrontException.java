package com.example.appstore.catalog.storefront;

/**
 * A valid country code for which Apple has no App Store storefront. Rejected before any Apple call; maps to 400
 * {@code unsupported-storefront} (ADR-0043).
 */
public final class UnsupportedStorefrontException extends RuntimeException {

    private final String countryCode;

    public UnsupportedStorefrontException(String countryCode) {
        super("No App Store storefront for country code " + countryCode);
        this.countryCode = countryCode;
    }

    public String countryCode() {
        return countryCode;
    }
}
