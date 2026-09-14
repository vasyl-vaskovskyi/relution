package com.example.appstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.appstore.catalog.AppDetailsService;
import com.example.appstore.catalog.AppNotFoundException;
import com.example.appstore.catalog.AppSearchService;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.example.appstore.catalog.storefront.InvalidCountryCodeException;
import com.example.appstore.catalog.storefront.UnsupportedStorefrontException;
import com.example.appstore.observability.CorrelationId;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The error matrix of {@code docs/architecture/error-handling.md}, through the web layer. 401 and 403 are covered by
 * the auth package's SecurityIntegrationTest.
 */
@WebMvcTest({AppSearchController.class, AppDetailsController.class})
@WithMockUser(authorities = "SCOPE_apps:read")
class ApiExceptionHandlerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AppSearchService searchService;

    @MockitoBean
    AppDetailsService detailsService;

    @Test
    void missingParameterIsAnInvalidRequestWithErrors() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/apps")
                .param("term", "pages")
                .header(CorrelationId.HEADER, "corr-1")
                .exchange();

        assertThat(result)
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(CorrelationId.HEADER, "corr-1")
                .bodyJson()
                .isStrictlyEqualTo("""
                        { "type": "urn:appstore:problem:invalid-request", "title": "Invalid request", "status": 400,
                          "detail": "One or more parameters are invalid.", "instance": "/api/v1/apps",
                          "correlationId": "corr-1", "errors": [ { "field": "cc", "message": "is required" } ] }
                        """);
    }

    @Test
    void constraintViolationsNameThePublicParameterWithoutRegexes() {
        assertThat(mvc.get()
                        .uri("/api/v1/apps")
                        .param("term", " ")
                        .param("cc", "d1")
                        .param("limit", "51"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "type": "urn:appstore:problem:invalid-request", "errors": [
                            { "field": "term", "message": "must not be blank" },
                            { "field": "cc", "message": "must be a two-letter country code" },
                            { "field": "limit", "message": "must be between 1 and 50" } ] }
                        """);
        assertThat(mvc.get().uri("/api/v1/apps/abc").param("cc", "de").param("l", "deu"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "errors": [ { "field": "id", "message": "must be 1 to 15 digits" },
                                      { "field": "l", "message": "must be a language tag like de or de-DE" } ] }
                        """);
    }

    @Test
    void unparsableNumberIsAnInvalidRequest() {
        assertThat(mvc.get()
                        .uri("/api/v1/apps")
                        .param("term", "pages")
                        .param("cc", "de")
                        .param("limit", "many"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo(
                        "{ \"errors\": [ { \"field\": \"limit\", \"message\": \"has an invalid format\" } ] }");
    }

    @Test
    void notACountryCodeIsAnInvalidRequestForCc() {
        when(searchService.search(anyString(), anyString(), anyInt())).thenThrow(new InvalidCountryCodeException());

        assertThat(search()).hasStatus(400).bodyJson().isLenientlyEqualTo("""
                        { "type": "urn:appstore:problem:invalid-request",
                          "errors": [ { "field": "cc", "message": "must be a supported storefront country code" } ] }
                        """);
    }

    @Test
    void appNotFoundIs404() {
        when(detailsService.details(anyString(), anyString(), anyString(), any()))
                .thenThrow(new AppNotFoundException("1"));

        assertThat(mvc.get().uri("/api/v1/apps/1").param("cc", "de").param("l", "de"))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "type": "urn:appstore:problem:app-not-found", "title": "App not found", "status": 404,
                          "instance": "/api/v1/apps/1" }
                        """);
    }

    static Stream<Arguments> upstreamFailures() {
        return Stream.of(
                Arguments.of(new UnsupportedStorefrontException("cu"), 400, "unsupported-storefront"),
                Arguments.of(new StorefrontNotServedException("de", "us"), 400, "unsupported-storefront"),
                Arguments.of(new UpstreamReadTimeoutException("Apple body", null), 504, "upstream-timeout"),
                Arguments.of(new UpstreamConnectException("Apple body", null), 502, "upstream-error"),
                Arguments.of(new UpstreamServerErrorException(503, "Apple body", null), 502, "upstream-error"),
                Arguments.of(new UpstreamContractException(403, "Apple body", null), 502, "upstream-error"),
                Arguments.of(new IllegalStateException("Apple body"), 500, "internal"));
    }

    @ParameterizedTest
    @MethodSource("upstreamFailures")
    void failuresMapToTheirProblemTypeWithoutLeakingMessages(RuntimeException failure, int status, String type) {
        when(searchService.search(anyString(), anyString(), anyInt())).thenThrow(failure);

        MvcTestResult result = search();

        assertThat(result)
                .hasStatus(status)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:appstore:problem:" + type);
        assertThat(result).bodyText().doesNotContain("Apple body", "Exception", "at com.example");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.correlationId")
                .asString()
                .isNotBlank();
    }

    @Test
    void rateLimitIs503WithRetryAfterInWholeSeconds() {
        UpstreamException limited =
                new UpstreamRateLimitedException(Duration.ofMillis(29_200), Reason.BUDGET, "budget");
        when(searchService.search(anyString(), anyString(), anyInt())).thenThrow(limited);

        assertThat(search())
                .hasStatus(503)
                .hasHeader(HttpHeaders.RETRY_AFTER, "30")
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:appstore:problem:upstream-unavailable");
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        assertThat(ApiExceptionHandler.retryAfterSeconds(Duration.ZERO)).isEqualTo(1);
        assertThat(ApiExceptionHandler.retryAfterSeconds(Duration.ofSeconds(30)))
                .isEqualTo(30);
    }

    @Test
    void unknownPathKeepsSpringsProblemAndGetsTheCorrelationId() {
        assertThat(mvc.get().uri("/api/v1/nothing-here").header(CorrelationId.HEADER, "corr-404"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.correlationId")
                .isEqualTo("corr-404");
    }

    private MvcTestResult search() {
        return mvc.get()
                .uri("/api/v1/apps")
                .param("term", "pages")
                .param("cc", "de")
                .exchange();
    }
}
