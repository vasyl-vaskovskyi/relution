package com.example.appstore.catalog;

import java.util.Objects;

/**
 * The storefront Apple actually served: lower-case country code, the language tag Apple served (which can differ from
 * the requested one, ADR-0009; {@code null} if Apple didn't report it) and the requested platform.
 */
public record Storefront(String countryCode, String language, Platform platform) {

    public Storefront {
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(platform, "platform");
    }
}
