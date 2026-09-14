package com.example.appstore.integration.apple;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class MzLookupClientTest {

    @RegisterExtension
    static final WireMockExtension apple = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private final MzLookupClient client = clientFor(apple.baseUrl());

    @Test
    void sendsPinnedAndRequestedParameters() {
        stubBody("apple/lookup/200-universal-app-enterprisestore-de.json");

        MzLookupResponse response = client.lookup("361309726", "de", "de-de", Platform.IOS);

        assertThat(response.results()).containsKey("361309726");
        apple.verify(getRequestedFor(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("version", equalTo("2"))
                .withQueryParam("p", equalTo("mdm-lockup"))
                .withQueryParam("caller", equalTo("MDM"))
                .withQueryParam("id", equalTo("361309726"))
                .withQueryParam("platform", equalTo("enterprisestore"))
                .withQueryParam("cc", equalTo("de"))
                .withQueryParam("l", equalTo("de-de"))
                .withoutHeader("Cookie"));
    }

    @Test
    void macPlatformUsesTheMacAppStoreChannel() {
        stubBody("apple/lookup/200-universal-app-macappstore-de.json");

        client.lookup("361309726", "de", "de", Platform.MAC);

        apple.verify(getRequestedFor(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("platform", equalTo("macappstore")));
    }

    @Test
    void emptyResultsAreReturnedForTheCallerToDecide() {
        stubBody("apple/lookup/200-empty-results-de.json");

        assertThat(client.lookup("1", "de", "de", Platform.IOS).results()).isEmpty();
    }

    @Test
    void silentUsFallbackMeansStorefrontNotServed() {
        stubBody("apple/lookup/200-storefront-fallback-us.json");

        assertThatThrownBy(() -> client.lookup("310633997", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(StorefrontNotServedException.class, e -> {
                    assertThat(e.requestedCountryCode()).isEqualTo("de");
                    assertThat(e.servedCountryCode()).isEqualTo("us");
                });
    }

    @Test
    void responseWithoutStorefrontIsAContractError() {
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .willReturn(aResponse().withStatus(200).withBody("{\"results\":{}}")));

        assertThatThrownBy(() -> client.lookup("1", "de", "de", Platform.IOS))
                .isInstanceOf(UpstreamContractException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404})
    void unexpectedClientErrorsAreContractErrors(int status) {
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .willReturn(aResponse().withStatus(status).withBody("{\"status\":7011}")));

        assertThatThrownBy(() -> client.lookup("1", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(
                        UpstreamContractException.class,
                        e -> assertThat(e.status()).isEqualTo(status))
                .hasMessageNotContaining("7011");
    }

    @Test
    void serverErrorRateLimitMalformedAndSlowAnswers() {
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("id", equalTo("500"))
                .willReturn(aResponse().withStatus(502)));
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("id", equalTo("429"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "12")));
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("id", equalTo("2"))
                .willReturn(aResponse().withStatus(200).withBody("not json")));
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .withQueryParam("id", equalTo("3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("apple/lookup/200-empty-results-de.json")
                        .withFixedDelay(2_000)));

        assertThatThrownBy(() -> client.lookup("500", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(
                        UpstreamServerErrorException.class,
                        e -> assertThat(e.status()).isEqualTo(502));
        assertThatThrownBy(() -> client.lookup("429", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(
                        UpstreamRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(12)));
        assertThatThrownBy(() -> client.lookup("2", "de", "de", Platform.IOS))
                .isInstanceOf(UpstreamContractException.class);
        assertThatThrownBy(() -> client.lookup("3", "de", "de", Platform.IOS))
                .isInstanceOf(UpstreamReadTimeoutException.class);
    }

    private static void stubBody(String bodyFile) {
        apple.stubFor(get(urlPathEqualTo(MzLookupClient.PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile(bodyFile)));
    }

    static MzLookupClient clientFor(String baseUrl) {
        AppleProperties properties = new AppleProperties(
                new AppleProperties.Search(URI.create("http://127.0.0.1:9"), 20),
                new AppleProperties.Lookup(URI.create(baseUrl)),
                new AppleProperties.Timeout(Duration.ofMillis(500), Duration.ofMillis(500)),
                new AppleProperties.Retry(2, Duration.ofSeconds(8)),
                new AppleProperties.RetryAfter(Duration.ofMinutes(5)));
        RestClient restClient = new AppleClientConfiguration().mzLookupRestClient(RestClient.builder(), properties);
        return new MzLookupClient(restClient, JsonMapper.builder().build());
    }
}
