package com.example.appstore.catalog;

/** Average user rating and number of ratings; either may be {@code null} when Apple doesn't report it. */
public record Rating(Double average, Long count) {}
