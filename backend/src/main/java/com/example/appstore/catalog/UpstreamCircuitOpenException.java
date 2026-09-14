package com.example.appstore.catalog;

import java.time.Duration;
import java.util.Objects;

/**
 * The circuit breaker for an Apple API is open after repeated connection failures, timeouts or 5xx, so the call was
 * not made (ADR-0047). Maps to 503 with {@code Retry-After}.
 */
public final class UpstreamCircuitOpenException extends UpstreamException {

    private final Duration retryAfter;

    public UpstreamCircuitOpenException(Duration retryAfter, String message) {
        super(message, null);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    /** What the client is told to wait: the time the breaker stays open before it tests Apple again. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
