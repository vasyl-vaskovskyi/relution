package com.example.appstore.integration.apple;

import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
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
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP handling shared by the Apple clients: body reading, JSON parsing, {@code Retry-After} and I/O failure
 * classification ({@code docs/architecture/caching-resilience.md}). Messages never contain Apple bodies.
 */
final class AppleHttpSupport {

    /** Used when Apple's 429 has no usable {@code Retry-After} (observed value: 30 s). */
    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(30);

    private AppleHttpSupport() {}

    /** Reads the whole body, decompressing gzip (observed on Search 400s; the JDK client doesn't decompress). */
    static byte[] readBody(ConvertibleClientHttpResponse response) throws IOException {
        byte[] raw = response.getBody().readAllBytes();
        boolean gzipped = raw.length > 1 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b;
        if (!gzipped) {
            return raw;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return in.readAllBytes();
        }
    }

    /** Parses JSON regardless of the content type (Search sends {@code text/javascript}). */
    static <T> T parse(JsonMapper jsonMapper, byte[] body, Class<T> type, String api) {
        try {
            T parsed = jsonMapper.readValue(body, type);
            if (parsed == null) {
                throw new UpstreamContractException(0, api + " returned an empty payload", null);
            }
            return parsed;
        } catch (JacksonException e) {
            throw new UpstreamContractException(0, api + " returned a malformed payload", e);
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
    static UpstreamException translate(ResourceAccessException e, String api) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException) {
                return new UpstreamConnectException(api + " connection failed", e);
            }
            if (cause instanceof HttpTimeoutException) {
                return new UpstreamReadTimeoutException(api + " read timed out", e);
            }
        }
        return new UpstreamServerErrorException(0, api + " connection broke after it was established", e);
    }
}
