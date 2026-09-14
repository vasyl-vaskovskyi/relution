package com.example.appstore.integration.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Raw MZStorePlatform lookup response. Boxed types only and Apple's exact key names
 * ({@code docs/integrations/apple-mapping.md}); unknown keys are ignored. {@code results} is keyed by the requested id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MzLookupResponse(Integer version, Meta meta, Map<String, Item> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Language language, StorefrontMeta storefront) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(String tag) {}

    /** {@code cc} is upper case (e.g. {@code DE}) and may differ from the requested one (silent US fallback). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StorefrontMeta(String cc, String id) {}

    /**
     * One store item. {@code id} is a string live and a number in Apple's doc samples. {@code artwork} has two observed
     * shapes, so it binds untyped: a list of {@code {width, height, url}} maps, or one map with a template {@code url}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            String kind,
            String name,
            String subtitle,
            String artistName,
            String bundleId,
            String watchBundleId,
            String minimumOSVersion,
            String releaseDate,
            List<String> deviceFamilies,
            List<String> genreNames,
            Description description,
            String whatsNew,
            List<Offer> offers,
            Object artwork,
            SoftwareInfo softwareInfo,
            UserRating userRating,
            String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(String standard) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Offer(String type, BigDecimal price, String priceFormatted, OfferVersion version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfferVersion(String display) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SoftwareInfo(String seller, String supportUrl, String privacyPolicyUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRating(Double value, Long ratingCount) {}
}
