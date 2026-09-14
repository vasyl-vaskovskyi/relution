package com.example.appstore.integration.apple;

import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Drift signal for the Apple APIs (ADR-0037): counts {@code appstore.apple.mapping.missing_field{api, field}} when a
 * field that is present in every capture is missing from a live response ({@code docs/integrations/apple-mapping.md}).
 * Inspects the raw records only, so the mappers stay pure. Field names come from the fixed lists below.
 */
final class AppleMissingFieldDetector {

    /** Required keys of every Search row. */
    static final List<String> SEARCH_ROW_FIELDS = List.of("trackId", "trackName");

    /** Required keys of lookup items of app kinds. {@code offers} is excluded: it can legitimately be missing. */
    static final List<String> LOOKUP_APP_FIELDS = List.of("name", "kind", "artwork");

    /** Lookup kinds that are apps; other kinds (e.g. {@code epubBook}) are not monitored. */
    static final Set<String> APP_KINDS = Set.of("iosSoftware", "desktopApp");

    private final MeterRegistry registry;

    AppleMissingFieldDetector(MeterRegistry registry) {
        this.registry = registry;
    }

    void inspect(ItunesSearchResponse response) {
        if (response == null || response.results() == null) {
            return;
        }
        response.results().stream().filter(Objects::nonNull).forEach(row -> {
            countIfMissing("search", "trackId", row.trackId());
            countIfMissing("search", "trackName", row.trackName());
        });
    }

    /**
     * A missing {@code kind} is counted for every item, because an item without it can't be classified. {@code name}
     * and {@code artwork} are checked for app kinds only.
     */
    void inspect(MzLookupResponse response) {
        Map<String, MzLookupResponse.Item> results = response == null ? null : response.results();
        if (results == null) {
            return;
        }
        results.values().stream().filter(Objects::nonNull).forEach(item -> {
            countIfMissing("lookup", "kind", item.kind());
            if (item.kind() != null && APP_KINDS.contains(item.kind().strip())) {
                countIfMissing("lookup", "name", item.name());
                countIfMissing("lookup", "artwork", item.artwork());
            }
        });
    }

    private void countIfMissing(String api, String field, Object value) {
        if (value == null) {
            Counter.builder(MetricNames.APPLE_MAPPING_MISSING_FIELD)
                    .tag(MetricNames.TAG_API, api)
                    .tag(MetricNames.TAG_FIELD, field)
                    .description("Fields present in every capture but missing from a live Apple response")
                    .register(registry)
                    .increment();
        }
    }
}
