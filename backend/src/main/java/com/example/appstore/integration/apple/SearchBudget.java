package com.example.appstore.integration.apple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The outbound Search budget as a token bucket (ADR-0045): {@code appstore.apple.search.budget} permits per minute,
 * refilled continuously, with a burst of at most one minute's budget. Per instance.
 */
@Component
public class SearchBudget {

    private static final long NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();

    private final Clock clock;
    private final int capacity;
    private final double nanosPerPermit;
    private double permits;
    private Instant lastRefill;

    @Autowired
    public SearchBudget(AppleProperties properties) {
        this(properties.search().budget(), Clock.systemUTC());
    }

    SearchBudget(int permitsPerMinute, Clock clock) {
        if (permitsPerMinute < 1) {
            throw new IllegalArgumentException("permitsPerMinute must be at least 1");
        }
        this.clock = clock;
        this.capacity = permitsPerMinute;
        this.nanosPerPermit = (double) NANOS_PER_MINUTE / permitsPerMinute;
        this.permits = permitsPerMinute;
        this.lastRefill = clock.instant();
    }

    /**
     * Takes one permit.
     *
     * @return empty if a permit was taken, otherwise the time until the next permit, rounded up to whole seconds
     */
    public synchronized Optional<Duration> tryAcquire() {
        refill();
        if (permits >= 1) {
            permits -= 1;
            return Optional.empty();
        }
        long missingNanos = (long) Math.ceil((1 - permits) * nanosPerPermit);
        long seconds = Math.max(1, Math.ceilDiv(missingNanos, 1_000_000_000L));
        return Optional.of(Duration.ofSeconds(seconds));
    }

    private void refill() {
        Instant now = clock.instant();
        long elapsedNanos = Duration.between(lastRefill, now).toNanos();
        if (elapsedNanos <= 0) {
            return;
        }
        permits = Math.min(capacity, permits + elapsedNanos / nanosPerPermit);
        lastRefill = now;
    }
}
