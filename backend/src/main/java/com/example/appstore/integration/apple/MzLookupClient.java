package com.example.appstore.integration.apple;

import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the MZStorePlatform lookup API (Legacy). Pins {@code version}, {@code p} and {@code caller}, never
 * sends or accepts Apple's {@code itvt} cookie, and translates every failure into an {@link UpstreamException}.
 */
@Component
public class MzLookupClient {

    private static final String API = "Lookup";
    static final String PATH = "/WebObjects/MZStorePlatform.woa/wa/lookup";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public MzLookupClient(
            @Qualifier(AppleClientConfiguration.LOOKUP_REST_CLIENT) RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Looks up one id. An empty {@code results} is returned as is ("not found" is the caller's decision).
     *
     * @throws StorefrontNotServedException if Apple served another storefront than {@code countryCode}
     */
    public MzLookupResponse lookup(String id, String countryCode, String languageTag, Platform platform) {
        MzLookupResponse response;
        try {
            response = restClient
                    .get()
                    .uri(builder -> builder.path(PATH)
                            .queryParam("version", "2")
                            .queryParam("p", "mdm-lockup")
                            .queryParam("caller", "MDM")
                            .queryParam("id", "{id}")
                            .queryParam("platform", "{platform}")
                            .queryParam("cc", "{cc}")
                            .queryParam("l", "{l}")
                            .build(id, channel(platform), countryCode, languageTag))
                    .exchange((request, clientResponse) -> handle(clientResponse));
        } catch (ResourceAccessException e) {
            throw AppleHttpSupport.translate(e, API);
        }
        requireServedStorefront(response, countryCode);
        return response;
    }

    /** Apple's channel names stay inside the adapter (ADR-0029). */
    static String channel(Platform platform) {
        return switch (platform) {
            case IOS -> "enterprisestore";
            case MAC -> "macappstore";
        };
    }

    private MzLookupResponse handle(ConvertibleClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return AppleHttpSupport.parse(jsonMapper, AppleHttpSupport.readBody(response), MzLookupResponse.class, API);
        }
        if (status.value() == 429) {
            throw new UpstreamRateLimitedException(
                    AppleHttpSupport.retryAfter(response.getHeaders()), "Lookup rate limited by Apple");
        }
        if (status.is5xxServerError()) {
            throw new UpstreamServerErrorException(status.value(), "Lookup failed with status " + status.value(), null);
        }
        // 400 status 7011/7012 (p), 403 (caller): our integration is wrong; the lookup never answers 404
        throw new UpstreamContractException(
                status.value(), "Lookup returned unexpected status " + status.value(), null);
    }

    /** Apple silently serves the US storefront for codes it doesn't support; {@code meta.storefront.cc} tells. */
    private static void requireServedStorefront(MzLookupResponse response, String countryCode) {
        String served = response.meta() == null || response.meta().storefront() == null
                ? null
                : response.meta().storefront().cc();
        if (served == null || served.isBlank()) {
            throw new UpstreamContractException(0, "Lookup response without a storefront", null);
        }
        if (!served.strip().equalsIgnoreCase(countryCode)) {
            throw new StorefrontNotServedException(countryCode, served.strip().toLowerCase(Locale.ROOT));
        }
    }
}
