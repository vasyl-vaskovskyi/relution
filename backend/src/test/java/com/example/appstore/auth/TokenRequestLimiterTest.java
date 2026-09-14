package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenRequestLimiterTest {

    private static final String KEY = "203.0.113.7";

    private final MutableClock clock = new MutableClock();

    private TokenRequestLimiter limiter(int failures, Duration window, int keys) {
        return new TokenRequestLimiter(new AuthProperties.Limit(failures, window, keys), clock);
    }

    @Test
    void allowsTheConfiguredFailuresThenReportsTheWait() {
        TokenRequestLimiter limiter = limiter(10, Duration.ofMinutes(5), 100);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(KEY)).as("attempt %d", i + 1).isEmpty();
        }
        // one permit every 30 s
        assertThat(limiter.tryAcquire(KEY)).contains(Duration.ofSeconds(30));
    }

    @Test
    void successfulRequestsGiveTheirPermitBack() {
        TokenRequestLimiter limiter = limiter(3, Duration.ofHours(1), 100);

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire(KEY)).isEmpty();
            limiter.release(KEY);
        }
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(KEY)).isEmpty(); // three failures
        }
        assertThat(limiter.tryAcquire(KEY)).isPresent();
    }

    @Test
    void aReleaseNeverRaisesTheBucketAboveItsCapacity() {
        TokenRequestLimiter limiter = limiter(2, Duration.ofHours(1), 100);
        limiter.tryAcquire(KEY);
        limiter.release(KEY);
        limiter.release(KEY);
        limiter.release(KEY);

        assertThat(limiter.tryAcquire(KEY)).isEmpty();
        assertThat(limiter.tryAcquire(KEY)).isEmpty();
        assertThat(limiter.tryAcquire(KEY)).isPresent();
    }

    @Test
    void refillsContinuouslyUpToTheCapacity() {
        TokenRequestLimiter limiter = limiter(10, Duration.ofMinutes(5), 100);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(KEY);
        }

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire(KEY)).contains(Duration.ofSeconds(10));

        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire(KEY)).isEmpty();

        clock.advance(Duration.ofDays(1));
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(KEY)).isEmpty();
        }
        assertThat(limiter.tryAcquire(KEY)).isPresent();
    }

    @Test
    void theWaitIsAtLeastOneSecond() {
        TokenRequestLimiter limiter = limiter(1_000, Duration.ofSeconds(1), 100); // one permit every 1 ms
        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire(KEY);
        }

        assertThat(limiter.tryAcquire(KEY)).contains(Duration.ofSeconds(1));
    }

    @Test
    void keysAreIndependent() {
        TokenRequestLimiter limiter = limiter(1, Duration.ofHours(1), 100);

        assertThat(limiter.tryAcquire(KEY)).isEmpty();
        assertThat(limiter.tryAcquire(KEY)).isPresent();
        assertThat(limiter.tryAcquire("198.51.100.1")).isEmpty();
    }

    @Test
    void memoryIsBoundedByTheKeyLimitAndIdleKeysExpire() {
        TokenRequestLimiter limiter = limiter(5, Duration.ofMinutes(5), 10);

        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire("key-" + i);
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void keysAreTheIpv4AddressOrTheIpv6Slash64() {
        assertThat(TokenRequestLimiter.key("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(TokenRequestLimiter.key("2001:db8:1:2:aaaa::1"))
                .isEqualTo(TokenRequestLimiter.key("2001:db8:1:2:bbbb::2"))
                .isEqualTo("20010db800010002/64");
        assertThat(TokenRequestLimiter.key("2001:db8:1:3::1")).isNotEqualTo(TokenRequestLimiter.key("2001:db8:1:2::1"));
        assertThat(TokenRequestLimiter.key("0:0:0:0:0:0:0:1")).isEqualTo(TokenRequestLimiter.key("::1"));
    }

    @Test
    void unparsableAddressesShareOneKeyWithoutAnyLookup() {
        assertThat(TokenRequestLimiter.key(null)).isEqualTo(TokenRequestLimiter.UNKNOWN_KEY);
        assertThat(TokenRequestLimiter.key(" ")).isEqualTo(TokenRequestLimiter.UNKNOWN_KEY);
        assertThat(TokenRequestLimiter.key("example.com")).isEqualTo(TokenRequestLimiter.UNKNOWN_KEY);
    }

    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-09-14T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
