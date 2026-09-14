package com.example.appstore.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.List;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.server.observation.OpenTelemetryServerHttpObservationDocumentation;
import org.springframework.http.server.observation.ServerHttpObservationDocumentation;
import org.springframework.stereotype.Component;

/**
 * Removes the query string and fragment from the URL key values of HTTP observations, so search terms never reach span
 * attributes ({@code docs/architecture/security.md#logging-and-privacy}). The client value is the full Apple request
 * URI including {@code term=}; the server values are already query-free in Spring Framework 7 and are stripped as a
 * safeguard. Filters run before the tracing handler copies key values into the span.
 */
@Component
public class QueryStringObservationFilter implements ObservationFilter {

    /** High-cardinality URL keys of Spring's HTTP server and client observations (default and OpenTelemetry). */
    static final List<String> URL_KEYS = List.of(
            ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL.asString(),
            OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.URL_PATH.asString(),
            ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL.asString());

    @Override
    public Observation.Context map(Observation.Context context) {
        for (String key : URL_KEYS) {
            KeyValue url = context.getHighCardinalityKeyValue(key);
            if (url != null) {
                context.addHighCardinalityKeyValue(KeyValue.of(key, withoutQuery(url.getValue())));
            }
        }
        return context;
    }

    static String withoutQuery(String url) {
        int end = url.length();
        int query = url.indexOf('?');
        if (query >= 0) {
            end = query;
        }
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        return url.substring(0, end);
    }
}
