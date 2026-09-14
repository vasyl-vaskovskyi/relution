package com.example.appstore;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.observability.CorrelationId;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * {@code docs/architecture/security.md#logging-and-privacy} holds in the real log output.
 *
 * <p>The fake Apple server logs every request it receives, including the query string. Those lines stand in for Apple's
 * own logs (Apple receives the term by design), so they are separated from the application's lines instead of being
 * silenced, and each test proves the separation isn't vacuous.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.structured.format.console=ecs", "management.server.port=0"})
@EnableWireMock(
        @ConfigureWireMock(
                name = "apple",
                baseUrlProperties = {"appstore.apple.search.url", "appstore.apple.lookup.url"},
                filesUnderClasspath = "wiremock"))
@ExtendWith(OutputCaptureExtension.class)
class LogSafetyIntegrationTest {

    private static final String SEARCH_TERM = "very-private-term-7f3a";
    private static final String BEARER_TOKEN = "eyJhbGciOi.secret-payload.signature";
    private static final String APPLE_BODY = "secret-apple-body-91";
    private static final List<String> FAKE_APPLE_LOGGERS = List.of("WireMock", "org.wiremock", "org.eclipse.jetty");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @InjectWireMock("apple")
    WireMockServer apple;

    @Value("${local.server.port}")
    int port;

    @Test
    void searchTermsAndTokensNeverReachTheApplicationLogs(CapturedOutput output) {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/javascript; charset=utf-8")
                        .withBodyFile("apple/search/200-apps-de.json")));

        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://127.0.0.1:" + port + "/api/v1/apps?term={term}&cc=de", SEARCH_TERM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN)
                .header(CorrelationId.HEADER, "corr-log-safety")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("corr-log-safety");

        String applicationLogs = applicationLines(output);
        assertThat(applicationLogs)
                .as("the upstream log line is JSON with the correlation id and the term length")
                .contains("\"correlationId\":\"corr-log-safety\"")
                .contains("termLength=" + SEARCH_TERM.length());
        assertThat(applicationLogs).doesNotContain(SEARCH_TERM).doesNotContain(BEARER_TOKEN);
        assertThat(output.toString())
                .as("the term did travel to the fake Apple server, so the filter above is not vacuous")
                .contains(SEARCH_TERM);
    }

    @Test
    void appleFailuresAreLoggedWithoutBodies(CapturedOutput output) {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(400).withBody("{\"errorMessage\":\"" + APPLE_BODY + "\"}")));

        int status = RestClient.create()
                .get()
                .uri("http://127.0.0.1:" + port + "/api/v1/apps?term=pages&cc=de")
                .exchange((request, clientResponse) ->
                        clientResponse.getStatusCode().value());

        assertThat(status).isEqualTo(502);
        String applicationLogs = applicationLines(output);
        assertThat(applicationLogs).contains("outcome=contract_error").doesNotContain(APPLE_BODY);
        assertThat(output.toString())
                .as("the fake Apple server did send the body")
                .contains(APPLE_BODY);
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
            return FAKE_APPLE_LOGGERS.stream().anyMatch(name::startsWith);
        } catch (RuntimeException notJson) {
            return false;
        }
    }
}
