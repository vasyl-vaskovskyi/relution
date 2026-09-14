package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.appstore.catalog.DetailsQuery;
import com.example.appstore.catalog.LookupResult;
import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LookupGatewayAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MzLookupClient client = mock(MzLookupClient.class);
    private final LookupGatewayAdapter adapter = new LookupGatewayAdapter(client, registry);

    @Test
    void foundAppIsRecordedAsSuccess(CapturedOutput output) throws IOException {
        when(client.lookup("361309726", "de", "de", Platform.MAC))
                .thenReturn(MzLookupMapperTest.fixture("200-universal-app-macappstore-de.json"));

        LookupResult result = adapter.lookup(new DetailsQuery("361309726", "de", "de", Platform.MAC));

        assertThat(result)
                .isInstanceOfSatisfying(
                        LookupResult.Found.class,
                        found -> assertThat(found.details().version()).isEqualTo("15.3.1"));
        assertThat(samples("success")).isEqualTo(1);
        assertThat(output)
                .contains("apple call api=lookup outcome=success status=200")
                .contains("id=361309726");
    }

    @Test
    void emptyResultsAreRecordedAsNotFound() throws IOException {
        when(client.lookup("1", "de", "de", Platform.IOS))
                .thenReturn(MzLookupMapperTest.fixture("200-empty-results-de.json"));

        assertThat(adapter.lookup(new DetailsQuery("1", "de", "de", Platform.IOS)))
                .isInstanceOf(LookupResult.NotFound.class);
        assertThat(samples("not_found")).isEqualTo(1);
    }

    @Test
    void failuresAreRecordedAndRethrown() {
        StorefrontNotServedException rejected = new StorefrontNotServedException("de", "us");
        when(client.lookup("2", "de", "de", Platform.IOS)).thenThrow(rejected);
        UpstreamReadTimeoutException timeout = new UpstreamReadTimeoutException("slow", null);
        when(client.lookup("3", "de", "de", Platform.IOS)).thenThrow(timeout);

        assertThatThrownBy(() -> adapter.lookup(new DetailsQuery("2", "de", "de", Platform.IOS)))
                .isSameAs(rejected);
        assertThatThrownBy(() -> adapter.lookup(new DetailsQuery("3", "de", "de", Platform.IOS)))
                .isSameAs(timeout);

        assertThat(samples("storefront_rejected")).isEqualTo(1);
        assertThat(samples("read_timeout")).isEqualTo(1);
    }

    @Test
    void anItemWithoutANameIsRecordedAsContractError() {
        when(client.lookup("4", "de", "de", Platform.IOS))
                .thenReturn(new MzLookupResponse(2, null, Map.of("4", itemWithoutName())));

        assertThatThrownBy(() -> adapter.lookup(new DetailsQuery("4", "de", "de", Platform.IOS)))
                .isInstanceOf(com.example.appstore.catalog.UpstreamContractException.class);
        assertThat(samples("contract_error")).isEqualTo(1);
        assertThat(registry.get(MetricNames.APPLE_MAPPING_MISSING_FIELD)
                        .tag(MetricNames.TAG_FIELD, "name")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    private double samples(String outcome) {
        return registry.get(MetricNames.APPLE_REQUESTS)
                .tag(MetricNames.TAG_API, "lookup")
                .tag(MetricNames.TAG_OUTCOME, outcome)
                .timer()
                .count();
    }

    private static MzLookupResponse.Item itemWithoutName() {
        return new MzLookupResponse.Item(
                "4",
                "iosSoftware",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
