package com.example.appstore.integration.apple;

import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the iTunes Search API. Translates every failure into an {@link UpstreamException}
 * ({@code docs/architecture/error-handling.md}); Apple bodies never leave this class.
 */
@Component
public class ItunesSearchClient {

    /** Used when Apple's 429 has no usable {@code Retry-After} (observed value: 30 s). */
    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public ItunesSearchClient(
            @Qualifier(AppleClientConfiguration.SEARCH_REST_CLIENT) RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Calls {@code GET /search?media=software&entity=software}. The term is sent as given (already trimmed); every query
     * value is fully URI-encoded.
     */
    public ItunesSearchResponse search(String term, String countryCode, int limit) {
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
            throw translate(e);
        }
    }

    private ItunesSearchResponse handle(ConvertibleClientHttpResponse response, String countryCode) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return parse(readBody(response));
        }
        if (status.value() == 429) {
            throw new UpstreamRateLimitedException(retryAfter(response.getHeaders()), "Search rate limited by Apple");
        }
        if (status.is5xxServerError()) {
            throw new UpstreamServerErrorException(status.value(), "Search failed with status " + status.value(), null);
        }
        if (status.value() == 400 && rejectsCountry(readBody(response))) {
            throw new StorefrontNotServedException(countryCode, null);
        }
        throw new UpstreamContractException(
                status.value(), "Search returned unexpected status " + status.value(), null);
    }

    private ItunesSearchResponse parse(byte[] body) {
        try {
            ItunesSearchResponse parsed = jsonMapper.readValue(body, ItunesSearchResponse.class);
            if (parsed == null) {
                throw new UpstreamContractException(0, "Search returned an empty payload", null);
            }
            return parsed;
        } catch (JacksonException e) {
            // Apple sends JSON as text/javascript, so the body is parsed regardless of the content type
            throw new UpstreamContractException(0, "Search returned a malformed payload", e);
        }
    }

    /** Apple's 400 names the rejected key, e.g. {@code Invalid value(s) for key(s): [country]}. */
    private static boolean rejectsCountry(byte[] body) {
        return new String(body, StandardCharsets.UTF_8).contains("[country]");
    }

    private static byte[] readBody(ConvertibleClientHttpResponse response) throws IOException {
        byte[] raw = response.getBody().readAllBytes();
        boolean gzipped = raw.length > 1 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b;
        if (!gzipped) {
            return raw;
        }
        // Apple's 400 bodies were observed gzipped; the JDK client doesn't decompress
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return in.readAllBytes();
        }
    }

    static Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            long seconds = Long.parseLong(value.strip());
            return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_RETRY_AFTER;
        } catch (NumberFormatException e) {
            // the HTTP-date form has not been observed from Apple
            return DEFAULT_RETRY_AFTER;
        }
    }

    /**
     * Classifies I/O failures by cause. {@link HttpConnectTimeoutException} extends {@link HttpTimeoutException}, so the
     * connect types are checked first on each cause.
     */
    static UpstreamException translate(ResourceAccessException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException) {
                return new UpstreamConnectException("Search connection failed", e);
            }
            if (cause instanceof HttpTimeoutException) {
                return new UpstreamReadTimeoutException("Search read timed out", e);
            }
        }
        return new UpstreamServerErrorException(0, "Search connection broke after it was established", e);
    }
}
