package com.example.appstore.integration.apple;

import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Records one {@code appstore.apple.requests} sample and one log line per logical Apple call, for both gateway adapters
 * ({@code docs/operations/observability.md}). The detail is a safe identifier (term length or app id), never the term.
 */
final class AppleCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(AppleCallRecorder.class);

    private final MeterRegistry registry;
    private final String api;

    AppleCallRecorder(MeterRegistry registry, String api) {
        this.registry = registry;
        this.api = api;
    }

    void record(String outcome, int status, Duration duration, Level level, String detailKey, Object detailValue) {
        Timer.builder(MetricNames.APPLE_REQUESTS)
                .tag(MetricNames.TAG_API, api)
                .tag(MetricNames.TAG_OUTCOME, outcome)
                .description("Logical Apple calls, recorded after retries")
                .register(registry)
                .record(duration);
        log.atLevel(level)
                .addKeyValue("api", api)
                .addKeyValue("outcome", outcome)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", duration.toMillis())
                .addKeyValue(detailKey, detailValue)
                .log(
                        "apple call api={} outcome={} status={} durationMs={} " + detailKey + "={}",
                        api,
                        outcome,
                        status,
                        duration.toMillis(),
                        detailValue);
    }

    void recordFailure(UpstreamException failure, Duration duration, String detailKey, Object detailValue) {
        record(outcomeOf(failure), statusOf(failure), duration, levelOf(failure), detailKey, detailValue);
    }

    static String outcomeOf(UpstreamException e) {
        return switch (e) {
            case UpstreamConnectException ignored -> "connect_error";
            case UpstreamReadTimeoutException ignored -> "read_timeout";
            case UpstreamServerErrorException ignored -> "server_error";
            case UpstreamRateLimitedException limited ->
                switch (limited.reason()) {
                    case APPLE -> "rate_limited";
                    case SHORT_CIRCUIT -> "short_circuited";
                    case BUDGET -> "budget_exhausted";
                };
            case UpstreamContractException ignored -> "contract_error";
            case StorefrontNotServedException ignored -> "storefront_rejected";
        };
    }

    /** The HTTP status Apple returned, or {@code 0} when no answer was received or Apple wasn't called. */
    static int statusOf(UpstreamException e) {
        return switch (e) {
            case UpstreamServerErrorException s -> s.status();
            case UpstreamContractException c -> c.status();
            case UpstreamRateLimitedException limited -> limited.reason() == Reason.APPLE ? 429 : 0;
            case StorefrontNotServedException ignored -> 400;
            case UpstreamConnectException ignored -> 0;
            case UpstreamReadTimeoutException ignored -> 0;
        };
    }

    /**
     * WARN for 429, an exhausted budget, timeouts and 5xx; DEBUG for the short-circuit; ERROR for contract errors (our
     * bug or drift); INFO for a rejected storefront ({@code docs/architecture/error-handling.md}).
     */
    static Level levelOf(UpstreamException e) {
        return switch (e) {
            case UpstreamRateLimitedException limited when limited.reason() == Reason.SHORT_CIRCUIT -> Level.DEBUG;
            case UpstreamContractException ignored -> Level.ERROR;
            case StorefrontNotServedException ignored -> Level.INFO;
            default -> Level.WARN;
        };
    }
}
