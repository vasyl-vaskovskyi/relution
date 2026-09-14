package com.example.appstore.catalog;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated details request: a numeric app id (1–15 digits), a lower-case storefront country code that passed the
 * storefront policy, a normalized language tag ({@code de_DE} and {@code de-DE} become {@code de-de}) and the platform.
 */
public record DetailsQuery(String id, String countryCode, String languageTag, Platform platform) {

    private static final Pattern ID = Pattern.compile("\\d{1,15}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}(-[a-z]{2})?");

    public DetailsQuery {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must be 1-15 digits");
        }
        countryCode = Objects.requireNonNull(countryCode, "countryCode").toLowerCase(Locale.ROOT);
        languageTag = normalizeLanguage(Objects.requireNonNull(languageTag, "languageTag"));
        if (!LANGUAGE.matcher(languageTag).matches()) {
            throw new IllegalArgumentException("languageTag must look like de or de-de");
        }
        Objects.requireNonNull(platform, "platform");
    }

    public static String normalizeLanguage(String languageTag) {
        return languageTag.strip().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
