package com.example.appstore;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.observability.CorrelationId;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole application with ECS JSON logs and WireMock as Apple: the binding privacy list of
 * {@code docs/architecture/security.md#logging-and-privacy} holds in the real log output, on the console and in the
 * log records exported over OTLP (ADR-0051; an in-memory exporter stands in for the collector).
 *
 * <p>The fake Apple server logs every request it receives, including the query string. Those lines stand in for Apple's
 * own logs (Apple receives the term by design), so they are separated from the application's lines instead of being
 * silenced, and each test proves the separation isn't vacuous.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "logging.structured.format.console=ecs",
            "management.server.port=0",
            "management.logging.export.enabled=true",
            "management.opentelemetry.logging.export.schedule-delay=50ms"
        })
@EnableWireMock(
        @ConfigureWireMock(
                name = "apple",
                baseUrlProperties = {"appstore.apple.search.url", "appstore.apple.lookup.url"},
                filesUnderClasspath = "wiremock"))
@Import(LogSafetyIntegrationTest.InMemoryLogs.class)
@ExtendWith(OutputCaptureExtension.class)
class LogSafetyIntegrationTest {

    private static final String SEARCH_TERM = "very-private-term-7f3a";
    private static final String APPLE_BODY = "secret-apple-body-91";
    private static final List<String> FAKE_APPLE_LOGGERS = List.of("WireMock", "org.wiremock", "org.eclipse.jetty");
    private static final List<String> MDC_KEYS = List.of("correlationId", "clientCorrelationId", "clientId");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @InjectWireMock("apple")
    WireMockServer apple;

    @Autowired
    InMemoryLogRecordExporter exported;

    @Autowired
    SdkLoggerProvider loggerProvider;

    @Value("${local.server.port}")
    int port;

    @Value("${appstore.auth.client.id}")
    String clientId;

    @Value("${appstore.auth.client.secret}")
    String clientSecret;

    @Value("${appstore.auth.jwt.secret}")
    String jwtSecret;

    @Test
    void searchTermsTokensAndSecretsNeverReachTheApplicationLogs(CapturedOutput output) {
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
                .header(CorrelationId.HEADER, "corr-log-safety")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("corr-log-safety");

        String applicationLogs = applicationLines(output);
        assertThat(applicationLogs)
                .as("the upstream log line is JSON with the correlation id, the client id and the term length")
                .contains("\"correlationId\":\"corr-log-safety\"")
                .contains("\"clientId\":\"" + clientId + "\"")
                .contains("termLength=" + SEARCH_TERM.length());
        assertThat(applicationLogs)
                .doesNotContain(SEARCH_TERM)
                .doesNotContain(token)
                .doesNotContain(clientSecret)
                .doesNotContain(jwtSecret);
        assertThat(output.toString())
                .as("the term did travel to the fake Apple server, so the filter above is not vacuous")
                .contains(SEARCH_TERM);

        List<LogRecordData> records = exportedApplicationRecords();
        LogRecordData upstreamLine = records.stream()
                .filter(record -> text(record).contains("termLength=" + SEARCH_TERM.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the upstream log line was not exported: " + records));
        assertThat(upstreamLine.getSpanContext().isValid())
                .as("the exported record carries the request's trace context")
                .isTrue();
        assertThat(records)
                .allSatisfy(record -> assertThat(text(record))
                        .doesNotContain(SEARCH_TERM)
                        .doesNotContain(token)
                        .doesNotContain(clientSecret)
                        .doesNotContain(jwtSecret))
                .allSatisfy(record -> assertThat(record.getAttributes().asMap().keySet())
                        .as("no MDC attribute is exported")
                        .noneMatch(key -> MDC_KEYS.contains(key.getKey())));
    }

    @Test
    void appleFailuresAreLoggedWithoutBodies(CapturedOutput output) {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(400).withBody("{\"errorMessage\":\"" + APPLE_BODY + "\"}")));

        int status = RestClient.create()
                .get()
                .uri("http://127.0.0.1:" + port + "/api/v1/apps?term=pages&cc=de")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .exchange((request, clientResponse) ->
                        clientResponse.getStatusCode().value());

        assertThat(status).isEqualTo(502);
        String applicationLogs = applicationLines(output);
        assertThat(applicationLogs).contains("outcome=contract_error").doesNotContain(APPLE_BODY);
        assertThat(output.toString())
                .as("the fake Apple server did send the body")
                .contains(APPLE_BODY);
        assertThat(exportedText()).contains("outcome=contract_error").doesNotContain(APPLE_BODY);
    }

    @Test
    void rejectedTokenRequestsAreLoggedWithoutCredentials(CapturedOutput output) {
        String wrongSecret = "wrong-secret-4c1d";

        int status = RestClient.create()
                .post()
                .uri("http://127.0.0.1:" + port + "/auth/token")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + HttpHeaders.encodeBasicAuth(clientId, wrongSecret, StandardCharsets.UTF_8))
                .exchange((request, clientResponse) ->
                        clientResponse.getStatusCode().value());

        assertThat(status).isEqualTo(401);
        assertThat(applicationLines(output))
                .contains("token request rejected")
                .doesNotContain(wrongSecret)
                .doesNotContain(clientSecret);
        assertThat(exportedText())
                .contains("token request rejected")
                .doesNotContain(wrongSecret)
                .doesNotContain(clientSecret);
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

    /** All captured lines except those of the fake Apple server; lines that aren't ECS JSON are kept. */
    private static String applicationLines(CapturedOutput output) {
        StringBuilder lines = new StringBuilder();
        for (String line : output.toString().split("\\R")) {
            if (!isFakeAppleLine(line)) {
                lines.append(line).append('\n');
            }
        }
        return lines.toString();
    }

    private static boolean isFakeAppleLine(String line) {
        if (!line.startsWith("{")) {
            return false;
        }
        try {
            JsonNode logger = JSON.readTree(line).path("log").path("logger");
            String name = logger.isMissingNode() ? "" : logger.asString();
            return isFakeAppleLogger(name);
        } catch (RuntimeException notJson) {
            return false;
        }
    }

    private static boolean isFakeAppleLogger(String name) {
        return FAKE_APPLE_LOGGERS.stream().anyMatch(name::startsWith);
    }

    /** Exported records, flushed first, except those of the fake Apple server (the scope name is the logger name). */
    private List<LogRecordData> exportedApplicationRecords() {
        loggerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return exported.records.stream()
                .filter(record ->
                        !isFakeAppleLogger(record.getInstrumentationScopeInfo().getName()))
                .toList();
    }

    private String exportedText() {
        StringBuilder text = new StringBuilder();
        exportedApplicationRecords().forEach(record -> text.append(text(record)).append('\n'));
        return text.toString();
    }

    private static String text(LogRecordData record) {
        io.opentelemetry.api.common.Value<?> body = record.getBodyValue();
        return (body == null ? "" : body.asString()) + " " + record.getAttributes();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryLogs {

        @Bean
        InMemoryLogRecordExporter inMemoryLogRecordExporter() {
            return new InMemoryLogRecordExporter();
        }
    }

    /** Boot adds every {@link LogRecordExporter} bean to its log record processor; no test library needed. */
    static final class InMemoryLogRecordExporter implements LogRecordExporter {

        final List<LogRecordData> records = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> batch) {
            records.addAll(batch);
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
