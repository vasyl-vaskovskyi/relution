package com.example.appstore.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token issuing and validation settings ({@code appstore.auth.*}). The secrets have no defaults: without them the
 * application refuses to start ({@code docs/operations/configuration.md}). {@code toString} never prints secrets.
 */
@Validated
@ConfigurationProperties("appstore.auth")
public record AuthProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Client client,
        @Valid @NotNull Limit limit) {

    /**
     * Failed-attempt limit on {@code POST /auth/token} per client address (ADR-0049): {@code failures} permits refilled
     * over {@code window}, at most {@code keys} addresses tracked.
     */
    public record Limit(
            @Positive int failures,

            @NotNull @DurationMin(seconds = 1) @DurationMax(days = 1)
            Duration window,

            @Positive int keys) {}

    /** {@code secret} is a Base64 HS256 key of at least 32 bytes, checked when the signing key is built. */
    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotBlank String audience,

            @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 1)
            Duration ttl) {

        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", audience=" + audience + ", ttl=" + ttl + ", secret=***]";
        }
    }

    public record Client(@NotBlank String id, @NotBlank String secret) {

        @Override
        public String toString() {
            return "Client[id=" + id + ", secret=***]";
        }
    }
}
