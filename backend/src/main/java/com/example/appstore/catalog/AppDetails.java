package com.example.appstore.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Details of one app in one storefront. {@code id}, {@code name}, {@code kind} and {@code storefront} are never
 * {@code null}; {@code platforms} and {@code genres} are empty when unknown; every other component may be {@code null}
 * ({@code docs/api/README.md}).
 *
 * @param firstReleaseDate the app's first release, not the current version's
 * @param universal an iOS app that also runs on the Mac
 */
public record AppDetails(
        String id,
        String name,
        AppKind kind,
        String subtitle,
        String developer,
        String seller,
        String bundleId,
        String watchBundleId,
        String version,
        String minimumOsVersion,
        LocalDate firstReleaseDate,
        Price price,
        List<DeviceFamily> platforms,
        boolean universal,
        String description,
        String whatsNew,
        String iconUrl,
        List<String> genres,
        Rating rating,
        Links links,
        Storefront storefront) {

    public AppDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(storefront, "storefront");
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
