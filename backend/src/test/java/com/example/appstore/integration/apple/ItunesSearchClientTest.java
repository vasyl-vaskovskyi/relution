package com.example.appstore.integration.apple;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ItunesSearchClientTest {

    @RegisterExtension
    static final WireMockExtension apple = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private final ItunesSearchClient client = clientFor(apple.baseUrl(), Duration.ofMillis(500));

    @Test
    void parsesResultsServedAsTextJavascript() {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/javascript; charset=utf-8")
                        .withBodyFile("apple/search/200-apps-de.json")));

        ItunesSearchResponse response = client.search("relution", "de", 5);

        assertThat(response.results())
                .extracting(ItunesSearchResponse.Row::trackName)
                .containsExactly("Relution", "Relution Teacher");
    }

    @Test
    void sendsFixedParametersAndEncodesTheTermCompletely() {
        apple.stubFor(get(urlPathEqualTo("/search")).willReturn(okNoResults()));

        client.search("äöü & co+1=2", "de", 5);

        apple.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("media", equalTo("software"))
                .withQueryParam("entity", equalTo("software"))
                .withQueryParam("term", equalTo("äöü & co+1=2"))
                .withQueryParam("country", equalTo("de"))
                .withQueryParam("limit", equalTo("5")));
    }

    @Test
    void rejectedCountryMeansStorefrontNotServed() {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(400).withBodyFile("apple/search/400-invalid-country.json")));

        assertThatThrownBy(() -> client.search("pages", "cu", 5))
                .isInstanceOfSatisfying(StorefrontNotServedException.class, e -> {
                    assertThat(e.requestedCountryCode()).isEqualTo("cu");
                    assertThat(e.servedCountryCode()).isNull();
                })
                .hasMessageNotContaining("Invalid value");
    }

    @Test
    void gzippedRejectedCountryBodyIsDecompressed() throws IOException {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(400).withBody(gzip("apple/search/400-invalid-country.json"))));

        assertThatThrownBy(() -> client.search("pages", "cu", 5)).isInstanceOf(StorefrontNotServedException.class);
    }

    @Test
    void otherBadRequestIsAContractError() {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(400).withBodyFile("apple/search/400-invalid-entity.json")));

        assertThatThrownBy(() -> client.search("pages", "de", 5))
                .isInstanceOfSatisfying(
                        UpstreamContractException.class,
                        e -> assertThat(e.status()).isEqualTo(400))
                .hasMessageNotContaining("resultEntity");
    }

    @Test
    void capturedRateLimitCarriesRetryAfter() {
        // served by mappings/apple/search/429-rate-limited.json
        assertThatThrownBy(() -> client.search("rate-limited", "de", 5))
                .isInstanceOfSatisfying(
                        UpstreamRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void retryAfterFallsBackToThirtySecondsWhenMissingOrInvalid() {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .withQueryParam("term", equalTo("missing"))
                .willReturn(aResponse().withStatus(429)));
        apple.stubFor(get(urlPathEqualTo("/search"))
                .withQueryParam("term", equalTo("invalid"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")));
        apple.stubFor(get(urlPathEqualTo("/search"))
                .withQueryParam("term", equalTo("long"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "120")));

        assertThat(retryAfterFor("missing")).isEqualTo(Duration.ofSeconds(30));
        assertThat(retryAfterFor("invalid")).isEqualTo(Duration.ofSeconds(30));
        assertThat(retryAfterFor("long")).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void serverErrorIsNotAContractError() {
        apple.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.search("pages", "de", 5))
                .isInstanceOfSatisfying(
                        UpstreamServerErrorException.class,
                        e -> assertThat(e.status()).isEqualTo(503));
    }

    @Test
    void malformedPayloadIsAContractError() {
        apple.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(200).withBody("<html>not json</html>")));

        assertThatThrownBy(() -> client.search("pages", "de", 5))
                .isInstanceOfSatisfying(
                        UpstreamContractException.class,
                        e -> assertThat(e.status()).isZero());
    }

    @Test
    void slowAnswerIsAReadTimeout() {
        apple.stubFor(get(urlPathEqualTo("/search")).willReturn(okNoResults().withFixedDelay(2_000)));

        assertThatThrownBy(() -> client.search("pages", "de", 5)).isInstanceOf(UpstreamReadTimeoutException.class);
    }

    @Test
    void refusedConnectionIsAConnectFailure() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ItunesSearchClient unreachable = clientFor("http://127.0.0.1:" + closedPort, Duration.ofMillis(500));

        assertThatThrownBy(() -> unreachable.search("pages", "de", 5)).isInstanceOf(UpstreamConnectException.class);
    }

    @Test
    void connectionClosedAfterConnectIsAServerErrorWithoutStatus() throws IOException {
        // A raw socket, because WireMock's CONNECTION_RESET_BY_PEER fault answers 500 on Jetty 12.
        // Every connection is closed without an answer: the JDK client itself retries an idempotent GET once
        // when the connection closes before any response, so a single close would end in a read timeout.
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (java.net.Socket socket = server.accept()) {
                        connections.incrementAndGet();
                        socket.getInputStream().read(new byte[1024]);
                    } catch (IOException ignored) {
                        // server closed at the end of the test
                    }
                }
            });
            ItunesSearchClient closing = clientFor("http://127.0.0.1:" + server.getLocalPort(), Duration.ofSeconds(2));

            assertThatThrownBy(() -> closing.search("pages", "de", 5))
                    .isInstanceOfSatisfying(
                            UpstreamServerErrorException.class,
                            e -> assertThat(e.status()).isZero());
            assertThat(connections).as("connections opened by the JDK client").hasValue(2);
        }
    }

    private Duration retryAfterFor(String term) {
        try {
            client.search(term, "de", 5);
        } catch (UpstreamRateLimitedException e) {
            return e.retryAfter();
        }
        throw new AssertionError("expected a rate-limited exception");
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okNoResults() {
        return aResponse().withStatus(200).withBodyFile("apple/search/200-no-results-de.json");
    }

    private static byte[] gzip(String bodyFile) throws IOException {
        try (InputStream in = ItunesSearchClientTest.class.getResourceAsStream("/wiremock/__files/" + bodyFile)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
                zip.write(in.readAllBytes());
            }
            return out.toByteArray();
        }
    }

    static ItunesSearchClient clientFor(String baseUrl, Duration readTimeout) {
        AppleProperties properties = new AppleProperties(
                new AppleProperties.Search(URI.create(baseUrl), 20),
                new AppleProperties.Lookup(URI.create("http://127.0.0.1:9")),
                new AppleProperties.Timeout(Duration.ofMillis(500), readTimeout),
                new AppleProperties.Retry(2, Duration.ofSeconds(8)),
                new AppleProperties.RetryAfter(Duration.ofMinutes(5)));
        RestClient restClient = new AppleClientConfiguration().itunesSearchRestClient(RestClient.builder(), properties);
        // a budget that never runs out here; SearchBudgetTest and AppleRetryTest cover the budget
        return new ItunesSearchClient(
                restClient, JsonMapper.builder().build(), new SearchBudget(10_000, java.time.Clock.systemUTC()));
    }
}
