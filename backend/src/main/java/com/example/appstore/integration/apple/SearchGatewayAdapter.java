package com.example.appstore.integration.apple;

import com.example.appstore.catalog.AppSearchGateway;
import com.example.appstore.catalog.AppSummary;
import com.example.appstore.catalog.SearchQuery;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Implements {@link AppSearchGateway} with the iTunes Search API. Applies the 429 short-circuit and records exactly one
 * {@code appstore.apple.requests} sample and one log line per logical call ({@code docs/operations/observability.md}).
 * The search term is never logged, only its length.
 */
@Component
public class SearchGatewayAdapter implements AppSearchGateway {

    private static final Logger log = LoggerFactory.getLogger(SearchGatewayAdapter.class);
    private static final String API = "search";

    private final ItunesSearchClient client;
    private final SearchRateLimitGuard guard;
    private final MeterRegistry registry;

    public SearchGatewayAdapter(ItunesSearchClient client, SearchRateLimitGuard guard, MeterRegistry registry) {
        this.client = client;
        this.guard = guard;
        this.registry = registry;
    }

    @Override
    public List<AppSummary> search(SearchQuery query) {
        Optional<Duration> blocked = guard.remainingBlock();
        if (blocked.isPresent()) {
            record(query, "short_circuited", 0, Duration.ZERO, Level.DEBUG);
            throw new UpstreamRateLimitedException(
                    blocked.get(), "Search short-circuited while Apple's Retry-After runs");
        }
        long start = System.nanoTime();
        try {
            ItunesSearchResponse response = client.search(query.term(), query.countryCode(), query.limit());
            List<AppSummary> items = ItunesSearchMapper.toSummaries(response);
            int rows = response.results() == null ? 0 : response.results().size();
            if (rows > items.size()) {
                log.debug("dropped {} search rows without id or name", rows - items.size());
            }
            record(query, items.isEmpty() ? "empty" : "success", 200, elapsed(start), Level.INFO);
            return items;
        } catch (UpstreamRateLimitedException e) {
            Duration applied = guard.block(e.retryAfter());
            record(query, outcomeOf(e), 429, elapsed(start), levelOf(e));
            throw applied.equals(e.retryAfter()) ? e : new UpstreamRateLimitedException(applied, e.getMessage());
        } catch (UpstreamException e) {
            record(query, outcomeOf(e), statusOf(e), elapsed(start), levelOf(e));
            throw e;
        }
    }

    static String outcomeOf(UpstreamException e) {
        return switch (e) {
            case UpstreamConnectException ignored -> "connect_error";
            case UpstreamReadTimeoutException ignored -> "read_timeout";
            case UpstreamServerErrorException ignored -> "server_error";
            case UpstreamRateLimitedException ignored -> "rate_limited";
            case UpstreamContractException ignored -> "contract_error";
            case StorefrontNotServedException ignored -> "storefront_rejected";
        };
    }

    private static int statusOf(UpstreamException e) {
        return switch (e) {
            case UpstreamServerErrorException s -> s.status();
            case UpstreamContractException c -> c.status();
            case UpstreamRateLimitedException ignored -> 429;
            case StorefrontNotServedException ignored -> 400;
            case UpstreamConnectException ignored -> 0;
            case UpstreamReadTimeoutException ignored -> 0;
        };
    }

    /** WARN for 429, timeouts and 5xx; ERROR for contract errors (our bug or drift); INFO otherwise. */
    private static Level levelOf(UpstreamException e) {
        return switch (e) {
            case UpstreamContractException ignored -> Level.ERROR;
            case StorefrontNotServedException ignored -> Level.INFO;
            default -> Level.WARN;
        };
    }

    private void record(SearchQuery query, String outcome, int status, Duration duration, Level level) {
        Timer.builder(MetricNames.APPLE_REQUESTS)
                .tag(MetricNames.TAG_API, API)
                .tag(MetricNames.TAG_OUTCOME, outcome)
                .description("Logical Apple calls, recorded after retries")
                .register(registry)
                .record(duration);
        log.atLevel(level)
                .addKeyValue("api", API)
                .addKeyValue("outcome", outcome)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", duration.toMillis())
                .addKeyValue("termLength", query.term().length())
                .log(
                        "apple call api={} outcome={} status={} durationMs={} termLength={}",
                        API,
                        outcome,
                        status,
                        duration.toMillis(),
                        query.term().length());
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
