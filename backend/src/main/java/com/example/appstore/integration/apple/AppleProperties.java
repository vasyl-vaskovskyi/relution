package com.example.appstore.integration.apple;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Apple upstream settings ({@code appstore.apple.*}). Names, environment variables and defaults are documented in
 * {@code docs/operations/configuration.md}.
 */
@Validated
@ConfigurationProperties("appstore.apple")
public record AppleProperties(
        @Valid @NotNull Search search,
        @Valid @NotNull Lookup lookup,
        @Valid @NotNull Timeout timeout,
        @Valid @NotNull Retry retry,
        @Valid @NotNull RetryAfter retryAfter) {

    /** Search API base URL and the outbound budget in calls per minute (ADR-0045). */
    public record Search(@NotNull URI url, @Positive int budget) {}

    public record Lookup(@NotNull URI url) {}

    public record Timeout(
            @NotNull @DurationMin(millis = 1) Duration connect,
            @NotNull @DurationMin(millis = 1) Duration read) {}

    /** Retries for connection failures only; no new attempt starts after {@code timeout}. */
    public record Retry(
            @PositiveOrZero int max,
            @NotNull @DurationMin(millis = 1) Duration timeout) {}

    /** Upper bound for Apple's {@code Retry-After} in the 429 short-circuit. */
    public record RetryAfter(
            @NotNull @DurationMin(millis = 1) Duration max) {}
}
