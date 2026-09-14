package com.example.appstore.integration.apple;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Circuit breaker settings per Apple API ({@code appstore.apple.circuit.*}, ADR-0047). Names, environment variables and
 * defaults are documented in {@code docs/operations/configuration.md}.
 *
 * @param failureRate percentage of failed calls in the window that opens the breaker
 * @param window number of most recent calls the failure rate is computed over
 * @param minimumCalls calls needed before the failure rate is evaluated
 * @param open how long the breaker stays open before it lets test calls through
 * @param halfOpenCalls test calls allowed while half-open
 */
@Validated
@ConfigurationProperties("appstore.apple.circuit")
public record AppleCircuitProperties(
        @Min(1) @Max(100) int failureRate,
        @Min(1) int window,
        @Min(1) int minimumCalls,
        @NotNull @DurationMin(seconds = 1) Duration open,
        @Min(1) int halfOpenCalls) {}
