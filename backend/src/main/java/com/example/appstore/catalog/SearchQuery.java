package com.example.appstore.catalog;

import java.util.Locale;
import java.util.Objects;

/**
 * A validated search request: the trimmed term (1–100 characters), a lower-case storefront country code that passed the
 * storefront policy, and a limit of 1–50.
 */
public record SearchQuery(String term, String countryCode, int limit) {

    public static final int MAX_TERM_LENGTH = 100;
    public static final int MAX_LIMIT = 50;

    public SearchQuery {
        term = Objects.requireNonNull(term, "term").strip();
        if (term.isEmpty() || term.length() > MAX_TERM_LENGTH) {
            throw new IllegalArgumentException("term must be 1-" + MAX_TERM_LENGTH + " characters after trimming");
        }
        countryCode = Objects.requireNonNull(countryCode, "countryCode").toLowerCase(Locale.ROOT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1-" + MAX_LIMIT);
        }
    }
}
