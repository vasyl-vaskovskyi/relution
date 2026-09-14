package com.example.appstore.integration.apple;

import com.example.appstore.catalog.AppSearchGateway;
import com.example.appstore.catalog.AppSummary;
import com.example.appstore.catalog.SearchQuery;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import io.micrometer.core.instrument.MeterRegistry;
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
    private static final String TERM_LENGTH = "termLength";

    private final ItunesSearchClient client;
    private final SearchRateLimitGuard guard;
    private final AppleCallRecorder recorder;
    private final AppleMissingFieldDetector missingFields;

    public SearchGatewayAdapter(ItunesSearchClient client, SearchRateLimitGuard guard, MeterRegistry registry) {
        this.client = client;
        this.guard = guard;
        this.recorder = new AppleCallRecorder(registry, "search");
        this.missingFields = new AppleMissingFieldDetector(registry);
    }

    @Override
    public List<AppSummary> search(SearchQuery query) {
        int termLength = query.term().length();
        Optional<Duration> blocked = guard.remainingBlock();
        if (blocked.isPresent()) {
            UpstreamRateLimitedException shortCircuit = new UpstreamRateLimitedException(
                    blocked.get(), Reason.SHORT_CIRCUIT, "Search short-circuited while Apple's Retry-After runs");
            recorder.recordFailure(shortCircuit, Duration.ZERO, TERM_LENGTH, termLength);
            throw shortCircuit;
        }
        long start = System.nanoTime();
        try {
            ItunesSearchResponse response = client.search(query.term(), query.countryCode(), query.limit());
            missingFields.inspect(response);
            List<AppSummary> items = ItunesSearchMapper.toSummaries(response);
            int rows = response.results() == null ? 0 : response.results().size();
            if (rows > items.size()) {
                log.debug("dropped {} search rows without id or name", rows - items.size());
            }
            String outcome = items.isEmpty() ? "empty" : "success";
            recorder.record(outcome, 200, elapsed(start), Level.INFO, TERM_LENGTH, termLength);
            return items;
        } catch (UpstreamRateLimitedException e) {
            UpstreamRateLimitedException reported = e;
            if (e.reason() == Reason.APPLE) {
                // only Apple's own 429 starts the short-circuit; an exhausted budget refills by itself
                Duration applied = guard.block(e.retryAfter());
                if (!applied.equals(e.retryAfter())) {
                    reported = new UpstreamRateLimitedException(applied, Reason.APPLE, e.getMessage());
                }
            }
            recorder.recordFailure(reported, elapsed(start), TERM_LENGTH, termLength);
            throw reported;
        } catch (UpstreamException e) {
            recorder.recordFailure(e, elapsed(start), TERM_LENGTH, termLength);
            throw e;
        }
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
