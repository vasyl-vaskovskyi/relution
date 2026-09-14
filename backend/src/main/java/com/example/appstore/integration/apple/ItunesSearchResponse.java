package com.example.appstore.integration.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * Raw iTunes Search API response. Boxed types only and Apple's exact key names ({@code docs/integrations/apple-mapping.md});
 * unknown keys are ignored. {@code resultCount} is informational: the mapper iterates {@code results}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesSearchResponse(Integer resultCount, List<Row> results) {

    /** One result row. {@code trackId} is a JSON number that binds to a string. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            String kind,
            String trackId,
            String trackName,
            String artistName,
            String artworkUrl512,
            String artworkUrl100,
            BigDecimal price,
            String currency,
            String formattedPrice,
            Double averageUserRating,
            Long userRatingCount) {}
}
