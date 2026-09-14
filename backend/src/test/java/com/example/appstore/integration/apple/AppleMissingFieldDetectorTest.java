package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppleMissingFieldDetectorTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppleMissingFieldDetector detector = new AppleMissingFieldDetector(registry);

    @ParameterizedTest
    @ValueSource(strings = {"200-apps-de.json", "200-mixed-ios-mac-de.json", "200-no-results-de.json"})
    void searchCapturesProduceNoSignal(String fixture) throws IOException {
        detector.inspect(ItunesSearchMapperTest.fixture(fixture));

        assertThat(missingFieldCounters()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "200-ebook-de.json",
                "200-empty-results-de.json",
                "200-ios-app-with-watch-de.json",
                "200-language-fallback-fr-de.json",
                "200-mac-only-app-de.json",
                "200-storefront-fallback-us.json",
                "200-universal-app-enterprisestore-de.json",
                "200-universal-app-macappstore-de.json",
                "doc-sample-artwork-array.json",
                "doc-sample-artwork-object-watch.json"
            })
    void lookupCapturesProduceNoSignal(String fixture) throws IOException {
        detector.inspect(MzLookupMapperTest.fixture(fixture));

        assertThat(missingFieldCounters()).isEmpty();
    }

    @Test
    void searchRowsWithoutRequiredKeysAreCountedPerField() {
        detector.inspect(new ItunesSearchResponse(3, Arrays.asList(row(null, "Pages"), row(null, null), null)));

        assertThat(count("search", "trackId")).isEqualTo(2);
        assertThat(count("search", "trackName")).isEqualTo(1);
    }

    @Test
    void appItemsWithoutRequiredKeysAreCountedPerField() {
        detector.inspect(new MzLookupResponse(
                2, null, Map.of("1", item("iosSoftware", null, null), "2", item("desktopApp", "Final Cut", null))));

        assertThat(count("lookup", "name")).isEqualTo(1);
        assertThat(count("lookup", "artwork")).isEqualTo(2);
        assertThat(count("lookup", "kind")).isZero();
    }

    @Test
    void aMissingKindIsCountedButOtherKindsAreNotMonitored() {
        detector.inspect(
                new MzLookupResponse(2, null, Map.of("1", item(null, null, null), "2", item("epubBook", null, null))));

        assertThat(count("lookup", "kind")).isEqualTo(1);
        assertThat(count("lookup", "name")).isZero();
        assertThat(count("lookup", "artwork")).isZero();
    }

    @Test
    void emptyResponsesAreIgnored() {
        detector.inspect((ItunesSearchResponse) null);
        detector.inspect(new ItunesSearchResponse(0, null));
        detector.inspect((MzLookupResponse) null);
        detector.inspect(new MzLookupResponse(2, null, null));

        assertThat(missingFieldCounters()).isEmpty();
    }

    private List<Counter> missingFieldCounters() {
        return List.copyOf(
                registry.find(MetricNames.APPLE_MAPPING_MISSING_FIELD).counters());
    }

    private double count(String api, String field) {
        Counter counter = registry.find(MetricNames.APPLE_MAPPING_MISSING_FIELD)
                .tag(MetricNames.TAG_API, api)
                .tag(MetricNames.TAG_FIELD, field)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static ItunesSearchResponse.Row row(String trackId, String trackName) {
        return new ItunesSearchResponse.Row(
                "software", trackId, trackName, null, null, null, null, null, null, null, null);
    }

    private static MzLookupResponse.Item item(String kind, String name, Object artwork) {
        return new MzLookupResponse.Item(
                "1", kind, name, null, null, null, null, null, null, null, null, null, null, null, artwork, null, null,
                null);
    }
}
