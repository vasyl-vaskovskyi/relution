package com.example.appstore.api;

import com.example.appstore.catalog.Rating;

/** Average user rating and number of ratings in responses. */
public record RatingDto(Double average, Long count) {

    static RatingDto from(Rating rating) {
        return rating == null ? null : new RatingDto(rating.average(), rating.count());
    }
}
