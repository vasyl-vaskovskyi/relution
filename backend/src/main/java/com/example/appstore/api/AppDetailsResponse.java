package com.example.appstore.api;

import com.example.appstore.catalog.AppDetails;
import com.example.appstore.catalog.DeviceFamily;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Response of {@code GET /api/v1/apps/{id}} ({@code docs/api/README.md}). */
public record AppDetailsResponse(
        String id,
        String name,
        String kind,
        String subtitle,
        String developer,
        String seller,
        String bundleId,
        String watchBundleId,
        String version,
        String minimumOsVersion,
        LocalDate firstReleaseDate,
        PriceDto price,
        List<String> platforms,
        boolean universal,
        String description,
        String whatsNew,
        String iconUrl,
        List<String> genres,
        RatingDto rating,
        Links links,
        Storefront storefront) {

    public record Links(String store, String support, String privacyPolicy) {}

    /** The storefront Apple served; {@code language} can differ from the requested {@code l}. */
    public record Storefront(String cc, String language, String platform) {}

    static AppDetailsResponse from(AppDetails app) {
        return new AppDetailsResponse(
                app.id(),
                app.name(),
                app.kind().name(),
                app.subtitle(),
                app.developer(),
                app.seller(),
                app.bundleId(),
                app.watchBundleId(),
                app.version(),
                app.minimumOsVersion(),
                app.firstReleaseDate(),
                PriceDto.from(app.price()),
                app.platforms().stream().map(DeviceFamily::name).toList(),
                app.universal(),
                app.description(),
                app.whatsNew(),
                app.iconUrl(),
                app.genres(),
                RatingDto.from(app.rating()),
                app.links() == null
                        ? new Links(null, null, null)
                        : new Links(
                                app.links().store(),
                                app.links().support(),
                                app.links().privacyPolicy()),
                new Storefront(
                        app.storefront().countryCode(),
                        app.storefront().language(),
                        app.storefront().platform().name().toLowerCase(Locale.ROOT)));
    }
}
