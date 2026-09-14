package com.example.appstore.integration.apple;

import com.example.appstore.catalog.UpstreamCircuitOpenException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One circuit breaker per Apple API (ADR-0047), used by the gateway adapters around the retried client call, never on
 * the same method as {@code @Retryable}. Only failures that say Apple is unhealthy count: connection failures, read
 * timeouts and 5xx. Rate limits, contract errors and rejected storefronts don't.
 */
@Component
public class AppleCircuitBreakers {

    static final String SEARCH = "search";
    static final String LOOKUP = "lookup";

    private static final Logger log = LoggerFactory.getLogger(AppleCircuitBreakers.class);

    private final CircuitBreakerRegistry registry;
    private final AppleCircuitProperties properties;

    public AppleCircuitBreakers(AppleCircuitProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.window())
                .minimumNumberOfCalls(properties.minimumCalls())
                .failureRateThreshold(properties.failureRate())
                .waitDurationInOpenState(properties.open())
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls())
                .recordException(AppleCircuitBreakers::countsAsFailure)
                .build());
        for (String api : new String[] {SEARCH, LOOKUP}) {
            registry.circuitBreaker(api)
                    .getEventPublisher()
                    .onStateTransition(
                            // name() gives a stable, greppable value such as CLOSED_TO_OPEN (toString is prose)
                            event -> log.warn(
                                    "circuit breaker api={} transition={}",
                                    api,
                                    event.getStateTransition().name()));
        }
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
    }

    /**
     * Runs the call through the breaker of {@code api}.
     *
     * @throws UpstreamCircuitOpenException if the breaker is open or its half-open test calls are used up
     */
    <T> T call(String api, Supplier<T> upstreamCall) {
        CircuitBreaker breaker = registry.circuitBreaker(api);
        try {
            return breaker.executeSupplier(upstreamCall);
        } catch (CallNotPermittedException e) {
            throw new UpstreamCircuitOpenException(properties.open(), "Circuit breaker for " + api + " is open");
        }
    }

    CircuitBreaker.State state(String api) {
        return registry.circuitBreaker(api).getState();
    }

    static boolean countsAsFailure(Throwable failure) {
        return failure instanceof UpstreamConnectException
                || failure instanceof UpstreamReadTimeoutException
                || failure instanceof UpstreamServerErrorException;
    }
}
