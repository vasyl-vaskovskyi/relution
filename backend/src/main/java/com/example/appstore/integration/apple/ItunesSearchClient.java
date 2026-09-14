package com.example.appstore.integration.apple;

import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import com.example.appstore.catalog.UpstreamServerErrorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the iTunes Search API. Translates every failure into an {@link UpstreamException}
 * ({@code docs/architecture/error-handling.md}); Apple bodies never leave this class.
 */
@Component
public class ItunesSearchClient {

    private static final String API = "Search";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final SearchBudget budget;

    public ItunesSearchClient(
            @Qualifier(AppleClientConfiguration.SEARCH_REST_CLIENT) RestClient restClient,
            JsonMapper jsonMapper,
            SearchBudget budget) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.budget = budget;
    }

    /**
     * Calls {@code GET /search?media=software&entity=software}. The term is sent as given (already trimmed); every query
     * value is fully URI-encoded.
     *
     * <p>Only connection failures are retried, through the Spring proxy, so callers must use the bean. Every attempt,
     * retries included, takes a permit from the outbound budget first ({@code docs/architecture/caching-resilience.md}).
     */
    @Retryable(
            includes = UpstreamConnectException.class,
            maxRetriesString = "${appstore.apple.retry.max}",
            timeoutString = "${appstore.apple.retry.timeout}",
            delay = 200,
            multiplier = 2,
            jitter = 100,
            timeUnit = TimeUnit.MILLISECONDS)
    public ItunesSearchResponse search(String term, String countryCode, int limit) {
        Optional<Duration> wait = budget.tryAcquire();
        if (wait.isPresent()) {
            throw new UpstreamRateLimitedException(wait.get(), Reason.BUDGET, "Outbound Search budget exhausted");
        }
        try {
            return restClient
                    .get()
                    .uri(builder -> builder.path("/search")
                            .queryParam("media", "software")
                            .queryParam("entity", "software")
                            .queryParam("term", "{term}")
                            .queryParam("country", "{country}")
                            .queryParam("limit", "{limit}")
                            .build(term, countryCode, limit))
                    .exchange((request, response) -> handle(response, countryCode));
        } catch (ResourceAccessException e) {
            throw AppleHttpSupport.translate(e, API);
        }
    }

    private ItunesSearchResponse handle(ConvertibleClientHttpResponse response, String countryCode) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return AppleHttpSupport.parse(
                    jsonMapper, AppleHttpSupport.readBody(response), ItunesSearchResponse.class, API);
        }
        if (status.value() == 429) {
            throw new UpstreamRateLimitedException(
                    AppleHttpSupport.retryAfter(response.getHeaders()), Reason.APPLE, "Search rate limited by Apple");
        }
        if (status.is5xxServerError()) {
            throw new UpstreamServerErrorException(status.value(), "Search failed with status " + status.value(), null);
        }
        if (status.value() == 400 && rejectsCountry(AppleHttpSupport.readBody(response))) {
            throw new StorefrontNotServedException(countryCode, null);
        }
        throw new UpstreamContractException(
                status.value(), "Search returned unexpected status " + status.value(), null);
    }

    /** Apple's 400 names the rejected key, e.g. {@code Invalid value(s) for key(s): [country]}. */
    private static boolean rejectsCountry(byte[] body) {
        return new String(body, StandardCharsets.UTF_8).contains("[country]");
    }
}
