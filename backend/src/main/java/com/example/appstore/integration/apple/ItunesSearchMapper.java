package com.example.appstore.integration.apple;

import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.AppSummary;
import com.example.appstore.catalog.Price;
import com.example.appstore.catalog.Rating;
import com.example.appstore.integration.apple.ItunesSearchResponse.Row;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps raw Search rows to {@link AppSummary}. Pure: no Spring, no I/O, never throws for unexpected content
 * ({@code docs/integrations/apple-mapping.md}).
 */
public final class ItunesSearchMapper {

    private ItunesSearchMapper() {}

    /** Rows without an id or a name are dropped; the caller can compare sizes to log how many. */
    public static List<AppSummary> toSummaries(ItunesSearchResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(Objects::nonNull)
                .map(ItunesSearchMapper::toSummary)
                .flatMap(Optional::stream)
                .toList();
    }

    static Optional<AppSummary> toSummary(Row row) {
        String id = clean(row.trackId());
        String name = clean(row.trackName());
        if (id == null || name == null) {
            return Optional.empty();
        }
        String iconUrl = Optional.ofNullable(clean(row.artworkUrl512())).orElseGet(() -> clean(row.artworkUrl100()));
        return Optional.of(new AppSummary(
                id, name, clean(row.artistName()), iconUrl, kindOf(row.kind()), priceOf(row), ratingOf(row)));
    }

    static AppKind kindOf(String kind) {
        String value = clean(kind);
        if ("software".equals(value)) {
            return AppKind.IOS_APP;
        }
        if ("mac-software".equals(value)) {
            return AppKind.MAC_APP;
        }
        return AppKind.OTHER;
    }

    private static Price priceOf(Row row) {
        String currency = clean(row.currency());
        String formatted = clean(row.formattedPrice());
        if (row.price() == null && currency == null && formatted == null) {
            return null;
        }
        return new Price(row.price(), currency, formatted);
    }

    private static Rating ratingOf(Row row) {
        if (row.averageUserRating() == null && row.userRatingCount() == null) {
            return null;
        }
        return new Rating(row.averageUserRating(), row.userRatingCount());
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
