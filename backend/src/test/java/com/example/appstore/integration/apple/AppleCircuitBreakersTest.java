package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamCircuitOpenException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AppleCircuitBreakersTest {

    /** Opens when at least half of the last 4 calls failed (at least 4 calls); 1 s open; 1 test call. */
    static final AppleCircuitProperties SMALL = new AppleCircuitProperties(50, 4, 4, Duration.ofSeconds(1), 1);

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AppleCircuitBreakers breakers = new AppleCircuitBreakers(SMALL, meters);
    private final AtomicInteger upstreamCalls = new AtomicInteger();

    @Test
    void opensAfterUnhealthyFailuresAndStopsCallingApple(CapturedOutput output) {
        failTimes(4, new UpstreamConnectException("down", null));

        assertThat(breakers.state(AppleCircuitBreakers.SEARCH)).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breakers.call(AppleCircuitBreakers.SEARCH, this::succeed))
                .isInstanceOfSatisfying(
                        UpstreamCircuitOpenException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(1)));
        assertThat(upstreamCalls).hasValue(4);
        assertThat(output).contains("circuit breaker api=search transition=CLOSED_TO_OPEN");
    }

    @Test
    void timeoutsAndServerErrorsCountToo() {
        failTimes(2, new UpstreamReadTimeoutException("slow", null));
        failTimes(2, new UpstreamServerErrorException(503, "down", null));

        assertThat(breakers.state(AppleCircuitBreakers.SEARCH)).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void rateLimitsContractErrorsAndRejectedStorefrontsDoNotOpenIt() {
        failTimes(2, new UpstreamRateLimitedException(Duration.ofSeconds(30), Reason.APPLE, "429"));
        failTimes(2, new UpstreamRateLimitedException(Duration.ofSeconds(3), Reason.BUDGET, "budget"));
        failTimes(2, new UpstreamContractException(400, "drift", null));
        failTimes(2, new StorefrontNotServedException("de", "us"));

        assertThat(breakers.state(AppleCircuitBreakers.SEARCH)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void theApisHaveSeparateBreakers() {
        failTimes(4, new UpstreamConnectException("down", null));

        assertThat(breakers.state(AppleCircuitBreakers.SEARCH)).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breakers.state(AppleCircuitBreakers.LOOKUP)).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breakers.call(AppleCircuitBreakers.LOOKUP, this::succeed)).isEqualTo("ok");
    }

    @Test
    void closesAgainWhenTheTestCallSucceedsAfterTheOpenDuration() throws InterruptedException {
        failTimes(4, new UpstreamConnectException("down", null));
        Thread.sleep(1_100);

        assertThat(breakers.call(AppleCircuitBreakers.SEARCH, this::succeed)).isEqualTo("ok");
        assertThat(breakers.state(AppleCircuitBreakers.SEARCH)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void stateIsExposedAsMetrics() {
        failTimes(4, new UpstreamConnectException("down", null));

        assertThat(meters.get("resilience4j.circuitbreaker.state")
                        .tag("name", "search")
                        .tag("state", "open")
                        .gauge()
                        .value())
                .isEqualTo(1.0);
    }

    private void failTimes(int times, RuntimeException failure) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> breakers.call(AppleCircuitBreakers.SEARCH, () -> {
                        upstreamCalls.incrementAndGet();
                        throw failure;
                    }))
                    .isSameAs(failure);
        }
    }

    private String succeed() {
        upstreamCalls.incrementAndGet();
        return "ok";
    }
}
