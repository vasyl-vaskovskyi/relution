package com.example.appstore.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cache settings ({@code appstore.cache.*}). Names, environment variables and defaults are documented in
 * {@code docs/operations/configuration.md}.
 */
@Validated
@ConfigurationProperties("appstore.cache")
public record CacheProperties(
        @Valid @NotNull Entry search,
        @Valid @NotNull Entry details,
        @Valid @NotNull NotFound notFound) {

    public record Entry(
            @NotNull @DurationMin(millis = 1) Duration ttl,
            @Positive long size) {}

    /** Time to live of a {@code NotFound} lookup result in {@code app-details}. */
    public record NotFound(@NotNull @DurationMin(millis = 1) Duration ttl) {}
}
