package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SearchBudgetTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void allowsABurstOfOneMinutesBudgetThenReportsTheWait() {
        SearchBudget budget = new SearchBudget(20, clock);

        for (int i = 0; i < 20; i++) {
            assertThat(budget.tryAcquire()).as("permit %d", i + 1).isEmpty();
        }
        // one permit every 3 s
        assertThat(budget.tryAcquire()).contains(Duration.ofSeconds(3));
    }

    @Test
    void refillsContinuouslyUpToTheCapacity() {
        SearchBudget budget = new SearchBudget(20, clock);
        for (int i = 0; i < 20; i++) {
            budget.tryAcquire();
        }

        clock.advance(Duration.ofMillis(1_500));
        assertThat(budget.tryAcquire()).contains(Duration.ofSeconds(2)); // 1.5 s missing, rounded up

        clock.advance(Duration.ofMillis(1_500));
        assertThat(budget.tryAcquire()).isEmpty();

        clock.advance(Duration.ofHours(1));
        for (int i = 0; i < 20; i++) {
            assertThat(budget.tryAcquire()).isEmpty();
        }
        assertThat(budget.tryAcquire()).isPresent();
    }

    @Test
    void theWaitIsAtLeastOneSecond() {
        SearchBudget budget = new SearchBudget(6_000, clock); // one permit every 10 ms
        for (int i = 0; i < 6_000; i++) {
            budget.tryAcquire();
        }

        assertThat(budget.tryAcquire()).contains(Duration.ofSeconds(1));
    }

    @Test
    void aBudgetBelowOneIsRejected() {
        assertThatThrownBy(() -> new SearchBudget(0, clock)).isInstanceOf(IllegalArgumentException.class);
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
