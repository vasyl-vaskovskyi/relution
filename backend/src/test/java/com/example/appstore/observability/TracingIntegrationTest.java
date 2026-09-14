package com.example.appstore.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import tools.jackson.databind.json.JsonMapper;

/**
 * Level 2 with tracing on, spans collected in memory instead of OTLP: the trace id is the correlation id, the Apple
 * client span stays in the request's trace (cache loader thread), and no span attribute carries the search term
 * ({@code docs/architecture/security.md#logging-and-privacy}).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "management.tracing.export.enabled=true",
            "management.tracing.sampling.probability=1.0",
            "management.opentelemetry.tracing.export.schedule-delay=50ms",
            "logging.structured.format.console=ecs"
        })
@EnableWireMock(
        @ConfigureWireMock(
                name = "apple",
                baseUrlProperties = {"appstore.apple.search.url", "appstore.apple.lookup.url"},
                filesUnderClasspath = "wiremock"))
@Import(TracingIntegrationTest.InMemorySpans.class)
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

    private static final String SEARCH_TERM = "very-private-traced-term-2b9c";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AttributeKey<String> HTTP_URL = AttributeKey.stringKey("http.url");

    @InjectWireMock("apple")
    WireMockServer apple;

    @Autowired
    InMemorySpanExporter spans;

    @Autowired
    SdkTracerProvider tracerProvider;

    @Value("${local.server.port}")
    int port;

    @Value("${appstore.auth.client.id}")
    String clientId;

    @Value("${appstore.auth.client.secret}")
    String clientSecret;

    @Test
    void theTraceIdIsTheCorrelationIdAndNoSpanCarriesTheSearchTerm(CapturedOutput output) {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/javascript; charset=utf-8")
                        .withBodyFile("apple/search/200-apps-de.json")));
        String token = accessToken();

        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://127.0.0.1:" + port + "/api/v1/apps?term={term}&cc=de", SEARCH_TERM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(CorrelationId.HEADER, "client-corr-42")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String traceId = response.getHeaders().getFirst(CorrelationId.HEADER);
        assertThat(traceId).as("the correlation id is a W3C trace id").matches("[0-9a-f]{32}");

        List<SpanData> trace = awaitServerAndClientSpans(traceId);
        SpanData server = only(trace, SpanKind.SERVER);
        SpanData client = only(trace, SpanKind.CLIENT);
        assertThat(server.getAttributes().get(HTTP_URL)).isEqualTo("/api/v1/apps");
        assertThat(client.getAttributes().get(HTTP_URL))
                .as("the Apple call is in the same trace, without its query string")
                .isEqualTo(apple.baseUrl() + "/search");
        assertThat(trace)
                .allSatisfy(span ->
                        assertThat(span.getName() + span.getAttributes()).doesNotContain(SEARCH_TERM));

        assertThat(output.toString())
                .as("logs use the trace id as correlation id and keep the client's id separately")
                .contains("\"correlationId\":\"" + traceId + "\"")
                .contains("\"clientCorrelationId\":\"client-corr-42\"");
    }

    private List<SpanData> awaitServerAndClientSpans(String traceId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            tracerProvider.forceFlush().join(1, TimeUnit.SECONDS);
            List<SpanData> trace = spans.finished.stream()
                    .filter(span -> span.getTraceId().equals(traceId))
                    .toList();
            boolean complete = trace.stream().anyMatch(span -> span.getKind() == SpanKind.SERVER)
                    && trace.stream().anyMatch(span -> span.getKind() == SpanKind.CLIENT);
            if (complete || Instant.now().isAfter(deadline)) {
                return trace;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return trace;
            }
        }
    }

    private static SpanData only(List<SpanData> trace, SpanKind kind) {
        List<SpanData> ofKind =
                trace.stream().filter(span -> span.getKind() == kind).toList();
        assertThat(ofKind).as("%s spans in %s", kind, trace).hasSize(1);
        return ofKind.getFirst();
    }

    private String accessToken() {
        String body = RestClient.create()
                .post()
                .uri("http://127.0.0.1:" + port + "/auth/token")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                .retrieve()
                .body(String.class);
        return JSON.readTree(body).path("accessToken").asString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InMemorySpans {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return new InMemorySpanExporter();
        }
    }

    /** Boot adds every {@link SpanExporter} bean to its span processor; no test library needed. */
    static final class InMemorySpanExporter implements SpanExporter {

        final List<SpanData> finished = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            finished.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
