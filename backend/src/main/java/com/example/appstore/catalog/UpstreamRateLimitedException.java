package com.example.appstore.catalog;

import java.time.Duration;
import java.util.Objects;

/**
 * Apple answered 429, or a local guard (429 short-circuit, outbound budget) rejected the call without reaching Apple.
 * {@code retryAfter} is what the client is told to wait.
 */
public final class UpstreamRateLimitedException extends UpstreamException {

    private final Duration retryAfter;

    public UpstreamRateLimitedException(Duration retryAfter, String message) {
        super(message, null);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
