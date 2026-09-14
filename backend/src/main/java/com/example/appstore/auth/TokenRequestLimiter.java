package com.example.appstore.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for {@code POST /auth/token} (ADR-0049): a token bucket of failed attempts per client address.
 * A bucket holds {@code appstore.auth.limit.failures} permits and refills continuously over
 * {@code appstore.auth.limit.window}. Every request takes a permit before the credentials are compared, and a
 * successful request gives it back, so only failures count. Buckets live in a Caffeine cache bounded by
 * {@code appstore.auth.limit.keys} and expire after one idle window, when they would be full again anyway. Per instance.
 */
@Component
class TokenRequestLimiter {

    /** The key for a request whose address can't be parsed; such requests share one bucket. */
    static final String UNKNOWN_KEY = "unknown";

    private final Clock clock;
    private final long nanosPerPermit;
    private final long capacityNanos;
    private final Cache<String, Bucket> buckets;

    @Autowired
    TokenRequestLimiter(AuthProperties properties) {
        this(properties.limit(), Clock.systemUTC());
    }

    TokenRequestLimiter(AuthProperties.Limit limit, Clock clock) {
        this.clock = clock;
        this.nanosPerPermit = Math.max(1, limit.window().toNanos() / limit.failures());
        this.capacityNanos = Math.multiplyExact(nanosPerPermit, limit.failures());
        this.buckets = Caffeine.newBuilder()
                .maximumSize(limit.keys())
                .expireAfterAccess(limit.window())
                .ticker(this::nanos)
                .executor(Runnable::run)
                .build();
    }

    /**
     * Takes one permit for the key.
     *
     * @return empty if a permit was taken, otherwise the time until the next permit, rounded up to whole seconds
     */
    Optional<Duration> tryAcquire(String key) {
        long now = nanos();
        Duration[] wait = new Duration[1];
        buckets.asMap().compute(key, (ignored, current) -> {
            long credit = refilled(current, now);
            if (credit >= nanosPerPermit) {
                return new Bucket(credit - nanosPerPermit, now);
            }
            wait[0] = waitFor(credit);
            return new Bucket(credit, now);
        });
        return Optional.ofNullable(wait[0]);
    }

    /** Gives back the permit of a successful request. */
    void release(String key) {
        long now = nanos();
        buckets.asMap()
                .computeIfPresent(
                        key,
                        (ignored, current) ->
                                new Bucket(Math.min(capacityNanos, refilled(current, now) + nanosPerPermit), now));
    }

    /** For tests: the number of keys currently held, after pending evictions. */
    long trackedKeys() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    /**
     * The bucket key for a client address as the servlet container reports it: the IPv4 address, or the /64 prefix of
     * an IPv6 address (one subscriber usually holds a whole /64). Forwarded headers are not read here (ADR-0049).
     */
    static String key(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN_KEY;
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(remoteAddress.strip());
        } catch (IllegalArgumentException e) {
            return UNKNOWN_KEY;
        }
        if (address instanceof Inet6Address) {
            return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
        }
        return address.getHostAddress();
    }

    private long refilled(Bucket current, long now) {
        if (current == null) {
            return capacityNanos;
        }
        long elapsed = Math.max(0, now - current.updatedNanos());
        return Math.min(capacityNanos, Math.addExact(current.creditNanos(), elapsed));
    }

    private Duration waitFor(long creditNanos) {
        long missingNanos = nanosPerPermit - creditNanos;
        return Duration.ofSeconds(Math.max(1, Math.ceilDiv(missingNanos, 1_000_000_000L)));
    }

    private long nanos() {
        Instant now = clock.instant();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    /** Credit in nanoseconds of refill time, so the arithmetic is exact: one permit costs {@code nanosPerPermit}. */
    private record Bucket(long creditNanos, long updatedNanos) {}
}
