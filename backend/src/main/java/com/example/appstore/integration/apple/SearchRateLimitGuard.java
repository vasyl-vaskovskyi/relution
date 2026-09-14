package com.example.appstore.integration.apple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Remembers Apple's Search {@code Retry-After} so that no call reaches Apple while it runs (ADR-0031). Per instance;
 * Search only.
 */
@Component
public class SearchRateLimitGuard {

    private final Clock clock;
    private final Duration maxRetryAfter;
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(Instant.MIN);

    @Autowired
    public SearchRateLimitGuard(AppleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SearchRateLimitGuard(AppleProperties properties, Clock clock) {
        this.clock = clock;
        this.maxRetryAfter = properties.retryAfter().max();
    }

    /** The remaining block, rounded up to whole seconds, or empty when calls may proceed. */
    public Optional<Duration> remainingBlock() {
        Duration remaining = Duration.between(clock.instant(), blockedUntil.get());
        if (!remaining.isPositive()) {
            return Optional.empty();
        }
        long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
        return Optional.of(Duration.ofSeconds(seconds));
    }

    /**
     * Blocks calls for Apple's {@code Retry-After}, capped at {@code appstore.apple.retryafter.max}. A shorter value
     * never shortens a block that is already running.
     *
     * @return the applied duration
     */
    public Duration block(Duration retryAfter) {
        Duration applied = retryAfter.compareTo(maxRetryAfter) > 0 ? maxRetryAfter : retryAfter;
        Instant until = clock.instant().plus(applied);
        blockedUntil.accumulateAndGet(until, (current, candidate) -> current.isAfter(candidate) ? current : candidate);
        return applied;
    }
}
