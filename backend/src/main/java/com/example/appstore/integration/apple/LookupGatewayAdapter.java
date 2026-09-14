package com.example.appstore.integration.apple;

import com.example.appstore.catalog.AppDetailsGateway;
import com.example.appstore.catalog.DetailsQuery;
import com.example.appstore.catalog.LookupResult;
import com.example.appstore.catalog.UpstreamException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Implements {@link AppDetailsGateway} with the MZStorePlatform lookup API. Records one {@code appstore.apple.requests}
 * sample and one log line per logical call; the lookup API has no known rate limit, so there is no short-circuit.
 */
@Component
public class LookupGatewayAdapter implements AppDetailsGateway {

    private final MzLookupClient client;
    private final AppleCallRecorder recorder;

    public LookupGatewayAdapter(MzLookupClient client, MeterRegistry registry) {
        this.client = client;
        this.recorder = new AppleCallRecorder(registry, "lookup");
    }

    @Override
    public LookupResult lookup(DetailsQuery query) {
        long start = System.nanoTime();
        try {
            MzLookupResponse response =
                    client.lookup(query.id(), query.countryCode(), query.languageTag(), query.platform());
            LookupResult result = MzLookupMapper.toResult(response, query);
            String outcome = result instanceof LookupResult.Found ? "success" : "not_found";
            recorder.record(outcome, 200, elapsed(start), Level.INFO, "id", query.id());
            return result;
        } catch (UpstreamException e) {
            recorder.recordFailure(e, elapsed(start), "id", query.id());
            throw e;
        }
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
