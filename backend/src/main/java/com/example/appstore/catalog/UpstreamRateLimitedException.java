package com.example.appstore.catalog;

import java.time.Duration;
import java.util.Objects;

/**
 * The call was rate limited, by Apple or by a local guard that answered without reaching Apple. All reasons map to 503
 * with {@code Retry-After}; the reason decides the metric outcome and whether the 429 short-circuit starts.
 */
public final class UpstreamRateLimitedException extends UpstreamException {

    public enum Reason {
        /** Apple answered 429. */
        APPLE,
        /** Apple's {@code Retry-After} is still running, so Apple wasn't called (ADR-0031). */
        SHORT_CIRCUIT,
        /** The outbound Search budget has no permit left, so Apple wasn't called (ADR-0045). */
        BUDGET
    }

    private final Duration retryAfter;
    private final Reason reason;

    public UpstreamRateLimitedException(Duration retryAfter, Reason reason, String message) {
        super(message, null);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** What the client is told to wait. */
    public Duration retryAfter() {
        return retryAfter;
    }

    public Reason reason() {
        return reason;
    }
}
