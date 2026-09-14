package com.example.appstore.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QueryStringObservationFilterTest {

    private static final String TERM = "very-private-term-7f3a";

    private final QueryStringObservationFilter filter = new QueryStringObservationFilter();

    @Test
    void theRestClientObservationKeepsTheAppleUrlWithoutTheSearchTerm() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationFilter(filter);
        // handlers see the key values after the filters, like the tracing handler that fills span attributes
        List<String> seenByHandlers = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                context.getAllKeyValues().forEach(keyValue -> seenByHandlers.add(keyValue.getValue()));
            }
        });
        RestClient.Builder builder = RestClient.builder().observationRegistry(registry);
        MockRestServiceServer apple = MockRestServiceServer.bindTo(builder).build();
        apple.expect(requestTo(startsWith("https://itunes.apple.com/search?"))).andRespond(withSuccess());

        builder.build()
                .get()
                .uri(b -> b.scheme("https")
                        .host("itunes.apple.com")
                        .path("/search")
                        .queryParam("term", "{term}")
                        .queryParam("country", "de")
                        .build(TERM))
                .retrieve()
                .toBodilessEntity();

        apple.verify();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("http.client.requests")
                .that()
                .hasHighCardinalityKeyValue("http.url", "https://itunes.apple.com/search");
        assertThat(seenByHandlers).contains("https://itunes.apple.com/search").noneMatch(value -> value.contains(TERM));
    }

    @Test
    void serverUrlKeysLoseQueryAndFragment() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/apps");
        request.setQueryString("term=" + TERM + "&cc=de");
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, new MockHttpServletResponse());
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/v1/apps?term=" + TERM + "&cc=de"));
        context.addHighCardinalityKeyValue(KeyValue.of("url.path", "/api/v1/apps#" + TERM));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/v1/apps"));

        filter.map(context);

        assertThat(context.getHighCardinalityKeyValue("http.url").getValue()).isEqualTo("/api/v1/apps");
        assertThat(context.getHighCardinalityKeyValue("url.path").getValue()).isEqualTo("/api/v1/apps");
        assertThat(context.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/v1/apps");
    }

    @Test
    void urlsWithoutQueryAreUnchanged() {
        assertThat(QueryStringObservationFilter.withoutQuery("/api/v1/apps/361309726"))
                .isEqualTo("/api/v1/apps/361309726");
        assertThat(QueryStringObservationFilter.withoutQuery("https://a.example/x?y#z"))
                .isEqualTo("https://a.example/x");
        assertThat(QueryStringObservationFilter.withoutQuery("/x#a?b")).isEqualTo("/x");
        assertThat(QueryStringObservationFilter.withoutQuery("")).isEmpty();
    }
}
