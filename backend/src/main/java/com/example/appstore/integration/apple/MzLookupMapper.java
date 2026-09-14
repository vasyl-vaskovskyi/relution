package com.example.appstore.integration.apple;

import com.example.appstore.catalog.AppDetails;
import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.DetailsQuery;
import com.example.appstore.catalog.DeviceFamily;
import com.example.appstore.catalog.Links;
import com.example.appstore.catalog.LookupResult;
import com.example.appstore.catalog.Price;
import com.example.appstore.catalog.Rating;
import com.example.appstore.catalog.Storefront;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.integration.apple.MzLookupResponse.Item;
import com.example.appstore.integration.apple.MzLookupResponse.Meta;
import com.example.appstore.integration.apple.MzLookupResponse.Offer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps a raw lookup response to a {@link LookupResult}. Pure: no Spring, no I/O
 * ({@code docs/integrations/apple-mapping.md}). Every field is optional; the only failure is an item without a name,
 * which breaks the contract of our API.
 */
public final class MzLookupMapper {

    static final int ICON_SIZE = 512;

    private MzLookupMapper() {}

    /**
     * Returns {@link LookupResult.NotFound} when {@code results} has no entry for the requested id.
     *
     * @throws UpstreamContractException if the item has no name
     */
    public static LookupResult toResult(MzLookupResponse response, DetailsQuery query) {
        Map<String, Item> results = response == null ? null : response.results();
        Item item = results == null ? null : results.get(query.id());
        if (item == null) {
            return new LookupResult.NotFound();
        }
        String name = clean(item.name());
        if (name == null) {
            throw new UpstreamContractException(0, "Lookup result without a name", null);
        }
        List<String> deviceFamilies = cleanList(item.deviceFamilies());
        AppKind kind = kindOf(item.kind());
        Offer offer = item.offers() == null || item.offers().isEmpty()
                ? null
                : item.offers().getFirst();
        return new LookupResult.Found(new AppDetails(
                query.id(),
                name,
                kind,
                clean(item.subtitle()),
                clean(item.artistName()),
                item.softwareInfo() == null ? null : clean(item.softwareInfo().seller()),
                clean(item.bundleId()),
                clean(item.watchBundleId()),
                offer == null || offer.version() == null
                        ? null
                        : clean(offer.version().display()),
                clean(item.minimumOSVersion()),
                isoDate(item.releaseDate()),
                priceOf(offer),
                platformsOf(deviceFamilies),
                "iosSoftware".equals(clean(item.kind())) && deviceFamilies.contains("mac"),
                item.description() == null ? null : clean(item.description().standard()),
                clean(item.whatsNew()),
                iconUrl(item.artwork()),
                cleanList(item.genreNames()),
                ratingOf(item),
                linksOf(item),
                storefrontOf(response.meta(), query)));
    }

    static AppKind kindOf(String kind) {
        String value = clean(kind);
        if ("iosSoftware".equals(value)) {
            return AppKind.IOS_APP;
        }
        if ("desktopApp".equals(value)) {
            return AppKind.MAC_APP;
        }
        return AppKind.OTHER;
    }

    /** Known device families in enum order; unknown values are ignored. */
    static List<DeviceFamily> platformsOf(List<String> deviceFamilies) {
        Set<DeviceFamily> platforms = EnumSet.noneOf(DeviceFamily.class);
        for (String family : deviceFamilies) {
            switch (family.toLowerCase(Locale.ROOT)) {
                case "iphone" -> platforms.add(DeviceFamily.IPHONE);
                case "ipad" -> platforms.add(DeviceFamily.IPAD);
                case "ipod" -> platforms.add(DeviceFamily.IPOD);
                case "mac" -> platforms.add(DeviceFamily.MAC);
                case "watch" -> platforms.add(DeviceFamily.WATCH);
                case "tvos" -> platforms.add(DeviceFamily.TV);
                default -> {
                    // extensible on Apple's side; ignored until we map it
                }
            }
        }
        return List.copyOf(platforms);
    }

    /**
     * The icon URL for {@link #ICON_SIZE} px. An array of sized entries picks the largest declared width that fits (the
     * declared size and the URL can disagree); an object substitutes its template. Never returns a URL with braces.
     */
    static String iconUrl(Object artwork) {
        String url = null;
        if (artwork instanceof List<?> entries) {
            long bestWidth = -1;
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> sized
                        && sized.get("width") instanceof Number width
                        && width.longValue() <= ICON_SIZE
                        && width.longValue() > bestWidth
                        && clean(sized.get("url")) != null) {
                    bestWidth = width.longValue();
                    url = clean(sized.get("url"));
                }
            }
        } else if (artwork instanceof Map<?, ?> template) {
            String raw = clean(template.get("url"));
            url = raw == null
                    ? null
                    : raw.replace("{w}", String.valueOf(ICON_SIZE))
                            .replace("{h}", String.valueOf(ICON_SIZE))
                            .replace("{c}", "bb")
                            .replace("{f}", "png");
        }
        return url == null || url.contains("{") || url.contains("}") ? null : url;
    }

    /** {@code releaseDate} is ISO; {@code latestVersionReleaseDate} is localized and never parsed. */
    static LocalDate isoDate(String value) {
        String date = clean(value);
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Price priceOf(Offer offer) {
        if (offer == null || (offer.price() == null && clean(offer.priceFormatted()) == null)) {
            return null;
        }
        // the lookup API has no currency field
        return new Price(offer.price(), null, clean(offer.priceFormatted()));
    }

    private static Rating ratingOf(Item item) {
        if (item.userRating() == null
                || (item.userRating().value() == null && item.userRating().ratingCount() == null)) {
            return null;
        }
        return new Rating(item.userRating().value(), item.userRating().ratingCount());
    }

    private static Links linksOf(Item item) {
        var info = item.softwareInfo();
        return new Links(
                clean(item.url()),
                info == null ? null : clean(info.supportUrl()),
                info == null ? null : clean(info.privacyPolicyUrl()));
    }

    private static Storefront storefrontOf(Meta meta, DetailsQuery query) {
        String served = meta == null || meta.storefront() == null
                ? null
                : clean(meta.storefront().cc());
        String language = meta == null || meta.language() == null
                ? null
                : clean(meta.language().tag());
        return new Storefront(
                served == null ? query.countryCode() : served.toLowerCase(Locale.ROOT),
                language == null ? null : language.toLowerCase(Locale.ROOT),
                query.platform());
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(MzLookupMapper::clean)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String clean(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
