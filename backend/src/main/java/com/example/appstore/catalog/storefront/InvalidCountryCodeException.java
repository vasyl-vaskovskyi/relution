package com.example.appstore.catalog.storefront;

/**
 * The value is not a country code at all: not two letters, or neither an ISO 3166-1 country nor on the allowlist. Maps
 * to 400 {@code invalid-request} for the {@code cc} parameter. The message never echoes the raw input.
 */
public final class InvalidCountryCodeException extends RuntimeException {

    public InvalidCountryCodeException() {
        super("must be a supported storefront country code");
    }
}
