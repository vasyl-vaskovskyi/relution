package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.SearchQuery;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SearchGatewayAdapterTest {

    private static final SearchQuery QUERY = new SearchQuery("secret term", "de", 5);

    private final MutableClock clock = new MutableClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ItunesSearchClient client = mock(ItunesSearchClient.class);
    private final SearchRateLimitGuard guard = new SearchRateLimitGuard(properties(Duration.ofMinutes(5)), clock);
    private final SearchGatewayAdapter adapter = new SearchGatewayAdapter(client, guard, registry);

    @Test
    void mapsResultsAndRecordsSuccess(CapturedOutput output) {
        when(client.search("secret term", "de", 5)).thenReturn(response(row("1", "Pages"), row(null, "dropped")));

        assertThat(adapter.search(QUERY)).singleElement().satisfies(app -> {
            assertThat(app.id()).isEqualTo("1");
            assertThat(app.kind()).isEqualTo(AppKind.IOS_APP);
        });

        assertThat(samples("success")).isEqualTo(1);
        assertThat(output)
                .contains("apple call api=search outcome=success status=200")
                .contains("termLength=11");
        assertThat(output).doesNotContain("secret term");
    }

    @Test
    void noResultsAreRecordedAsEmpty() {
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(new ItunesSearchResponse(0, List.of()));

        assertThat(adapter.search(QUERY)).isEmpty();
        assertThat(samples("empty")).isEqualTo(1);
    }

    @Test
    void appleRateLimitBlocksFurtherCallsUntilRetryAfterPassed(CapturedOutput output) {
        when(client.search(anyString(), anyString(), anyInt()))
                .thenThrow(new UpstreamRateLimitedException(Duration.ofSeconds(30), "limited"))
                .thenReturn(new ItunesSearchResponse(0, List.of()));

        assertThatThrownBy(() -> adapter.search(QUERY)).isInstanceOf(UpstreamRateLimitedException.class);
        clock.advance(Duration.ofMillis(10_500));
        assertThatThrownBy(() -> adapter.search(QUERY))
                .isInstanceOfSatisfying(
                        UpstreamRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20)));
        verify(client, times(1)).search(anyString(), anyString(), anyInt());

        clock.advance(Duration.ofSeconds(20));
        assertThat(adapter.search(QUERY)).isEmpty();
        verify(client, times(2)).search(anyString(), anyString(), anyInt());

        assertThat(samples("rate_limited")).isEqualTo(1);
        assertThat(samples("short_circuited")).isEqualTo(1);
        assertThat(output).doesNotContain("secret term");
    }

    @Test
    void retryAfterIsCappedAtTheConfiguredMaximum() {
        when(client.search(anyString(), anyString(), anyInt()))
                .thenThrow(new UpstreamRateLimitedException(Duration.ofHours(1), "limited"));

        assertThatThrownBy(() -> adapter.search(QUERY))
                .isInstanceOfSatisfying(
                        UpstreamRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(5)));
        assertThat(guard.remainingBlock()).contains(Duration.ofMinutes(5));
    }

    @Test
    void shorterRetryAfterNeverShortensARunningBlock() {
        guard.block(Duration.ofSeconds(60));
        guard.block(Duration.ofSeconds(5));

        assertThat(guard.remainingBlock()).contains(Duration.ofSeconds(60));
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new UpstreamConnectException("x", null), "connect_error"),
                Arguments.of(new UpstreamReadTimeoutException("x", null), "read_timeout"),
                Arguments.of(new UpstreamServerErrorException(503, "x", null), "server_error"),
                Arguments.of(new UpstreamContractException(400, "x", null), "contract_error"),
                Arguments.of(new StorefrontNotServedException("de", null), "storefront_rejected"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void failuresAreRethrownAndRecordedWithTheirOutcome(UpstreamException failure, String outcome) {
        when(client.search(anyString(), anyString(), anyInt())).thenThrow(failure);

        assertThatThrownBy(() -> adapter.search(QUERY)).isSameAs(failure);
        assertThat(samples(outcome)).isEqualTo(1);
        assertThat(guard.remainingBlock()).isEmpty();
    }

    private double samples(String outcome) {
        return registry.get(MetricNames.APPLE_REQUESTS)
                .tag(MetricNames.TAG_API, "search")
                .tag(MetricNames.TAG_OUTCOME, outcome)
                .timer()
                .count();
    }

    private static ItunesSearchResponse response(ItunesSearchResponse.Row... rows) {
        return new ItunesSearchResponse(rows.length, List.of(rows));
    }

    private static ItunesSearchResponse.Row row(String id, String name) {
        return new ItunesSearchResponse.Row("software", id, name, null, null, null, null, null, null, null, null);
    }

    private static AppleProperties properties(Duration maxRetryAfter) {
        return new AppleProperties(
                new AppleProperties.Search(URI.create("http://127.0.0.1:9"), 20),
                new AppleProperties.Lookup(URI.create("http://127.0.0.1:9")),
                new AppleProperties.Timeout(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                new AppleProperties.Retry(2, Duration.ofSeconds(8)),
                new AppleProperties.RetryAfter(maxRetryAfter));
    }

    private static final class MutableClock extends Clock {

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
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
