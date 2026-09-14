package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.AppDetails;
import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.DetailsQuery;
import com.example.appstore.catalog.DeviceFamily;
import com.example.appstore.catalog.LookupResult;
import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.UpstreamContractException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MzLookupMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void mapsAUniversalAppOnTheIosChannel() throws IOException {
        AppDetails pages = found("200-universal-app-enterprisestore-de.json", "361309726", Platform.IOS);

        assertThat(pages.id()).isEqualTo("361309726");
        assertThat(pages.name()).isEqualTo("Pages: Erstelle Dokumente");
        assertThat(pages.subtitle()).isEqualTo("Design, Layout und Gestaltung");
        assertThat(pages.kind()).isEqualTo(AppKind.IOS_APP);
        assertThat(pages.developer()).isEqualTo("Apple");
        assertThat(pages.seller()).isEqualTo("Apple Distribution International");
        assertThat(pages.bundleId()).isEqualTo("com.apple.Pages");
        assertThat(pages.watchBundleId()).isNull();
        assertThat(pages.version()).isEqualTo("15.3");
        assertThat(pages.minimumOsVersion()).isEqualTo("18.0");
        assertThat(pages.firstReleaseDate()).isEqualTo(LocalDate.of(2010, 5, 26));
        assertThat(pages.platforms()).containsExactly(DeviceFamily.IPHONE, DeviceFamily.IPAD, DeviceFamily.MAC);
        assertThat(pages.universal()).isTrue();
        assertThat(pages.price().amount()).isEqualByComparingTo("0");
        assertThat(pages.price().currency()).isNull();
        assertThat(pages.price().formatted()).startsWith("0,00").endsWith("€");
        assertThat(pages.description()).startsWith("Pages ist jetzt Teil von Ap");
        assertThat(pages.whatsNew()).isNotBlank();
        assertThat(pages.iconUrl()).endsWith("/512x512bb.png").doesNotContain("{", "}");
        assertThat(pages.genres()).containsExactly("Produktivität", "Wirtschaft");
        assertThat(pages.rating().average()).isEqualTo(4.5);
        assertThat(pages.rating().count()).isEqualTo(146780L);
        assertThat(pages.links().store())
                .isEqualTo("https://apps.apple.com/de/app/pages-erstelle-dokumente/id361309726");
        assertThat(pages.links().support()).isEqualTo("http://www.apple.com/de/support/ipad/pages/");
        assertThat(pages.links().privacyPolicy()).isEqualTo("https://www.apple.com/legal/privacy/de-ww/");
        assertThat(pages.storefront().countryCode()).isEqualTo("de");
        assertThat(pages.storefront().language()).isEqualTo("de-de");
        assertThat(pages.storefront().platform()).isEqualTo(Platform.IOS);
    }

    @Test
    void theMacChannelReturnsMacMetadataForTheSameApp() throws IOException {
        AppDetails pages = found("200-universal-app-macappstore-de.json", "361309726", Platform.MAC);

        assertThat(pages.kind()).isEqualTo(AppKind.IOS_APP);
        assertThat(pages.version()).isEqualTo("15.3.1");
        assertThat(pages.minimumOsVersion()).isEqualTo("15.6");
        assertThat(pages.storefront().platform()).isEqualTo(Platform.MAC);
    }

    @Test
    void mapsWatchAppsAndTheFirstReleaseDate() throws IOException {
        AppDetails whatsapp = found("200-ios-app-with-watch-de.json", "310633997", Platform.IOS);

        assertThat(whatsapp.watchBundleId()).isEqualTo("net.whatsapp.WhatsApp.watchkitapp");
        assertThat(whatsapp.platforms())
                .containsExactly(
                        DeviceFamily.IPHONE,
                        DeviceFamily.IPAD,
                        DeviceFamily.IPOD,
                        DeviceFamily.MAC,
                        DeviceFamily.WATCH);
        assertThat(whatsapp.firstReleaseDate()).isEqualTo(LocalDate.of(2009, 5, 4));
    }

    @Test
    void mapsAMacOnlyPaidApp() throws IOException {
        AppDetails finalCut = found("200-mac-only-app-de.json", "424389933", Platform.IOS);

        assertThat(finalCut.kind()).isEqualTo(AppKind.MAC_APP);
        assertThat(finalCut.platforms()).containsExactly(DeviceFamily.MAC);
        assertThat(finalCut.universal()).isFalse();
        assertThat(finalCut.price().amount()).isEqualTo(new BigDecimal("349.99"));
        assertThat(finalCut.version()).isEqualTo("12.3");
    }

    @Test
    void mapsOtherKindsWithMissingSoftwareFields() throws IOException {
        AppDetails book = found("200-ebook-de.json", "492186116", Platform.IOS);

        assertThat(book.kind()).isEqualTo(AppKind.OTHER);
        assertThat(book.bundleId()).isNull();
        assertThat(book.minimumOsVersion()).isNull();
        assertThat(book.platforms()).isEmpty();
        assertThat(book.universal()).isFalse();
        assertThat(book.version()).isNull();
        assertThat(book.seller()).isNull();
        assertThat(book.links().support()).isNull();
        assertThat(book.links().store()).startsWith("https://books.apple.com/");
    }

    @Test
    void emptyResultsAreNotFound() throws IOException {
        assertThat(MzLookupMapper.toResult(fixture("200-empty-results-de.json"), query("1", Platform.IOS)))
                .isInstanceOf(LookupResult.NotFound.class);
    }

    @Test
    void anotherIdInTheResultsIsNotFound() throws IOException {
        assertThat(MzLookupMapper.toResult(
                        fixture("200-universal-app-enterprisestore-de.json"), query("310633997", Platform.IOS)))
                .isInstanceOf(LookupResult.NotFound.class);
    }

    @Test
    void reportsTheLanguageAppleServed() throws IOException {
        AppDetails pages = found("200-language-fallback-fr-de.json", "361309726", Platform.IOS);

        assertThat(pages.storefront().language()).isEqualTo("de-de");
    }

    @Test
    void docSampleWithNumericIdAndArtworkArray() throws IOException {
        AppDetails pages = found("doc-sample-artwork-array.json", "361309726", Platform.IOS);

        // the largest declared width that fits is 216, whose URL says 360x216
        assertThat(pages.iconUrl()).endsWith("/360x216bb.png");
        assertThat(pages.version()).isEqualTo("5.0.1");
    }

    @Test
    void docSampleWithArtworkTemplateObject() throws IOException {
        AppDetails b2b = found("doc-sample-artwork-object-watch.json", "451796775", Platform.IOS);

        assertThat(b2b.iconUrl()).endsWith("/512x512bb.png");
        assertThat(b2b.watchBundleId()).isEqualTo("com.example.bbTest.watchkitapp");
        assertThat(b2b.subtitle()).isNull();
    }

    @Test
    void artworkShapes() {
        assertThat(MzLookupMapper.iconUrl(Map.of("url", "https://x.test/a/{w}x{h}{c}.{f}")))
                .isEqualTo("https://x.test/a/512x512bb.png");
        // hand-made: an object with a concrete URL is used as is (inferred shape, never seen)
        assertThat(MzLookupMapper.iconUrl(Map.of("url", "https://x.test/a/100x100bb.jpg")))
                .isEqualTo("https://x.test/a/100x100bb.jpg");
        assertThat(MzLookupMapper.iconUrl(Map.of("url", "https://x.test/{unknown}.png")))
                .isNull();
        assertThat(MzLookupMapper.iconUrl(List.of(Map.of("width", 1024, "url", "https://x.test/1024.png"))))
                .isNull();
        assertThat(MzLookupMapper.iconUrl("not an artwork")).isNull();
        assertThat(MzLookupMapper.iconUrl(null)).isNull();
    }

    @Test
    void toleratesMissingAndLocalizedValues() {
        MzLookupResponse response = JSON.readValue("""
                { "results": { "7": { "name": " Bare ", "kind": "visionApp", "releaseDate": "9.09.2026",
                  "deviceFamilies": ["vision", " ", "iphone"], "offers": [], "genreNames": null,
                  "userRating": { "value": null, "ratingCount": null }, "softwareInfo": null } } }
                """, MzLookupResponse.class);

        AppDetails bare = ((LookupResult.Found) MzLookupMapper.toResult(response, query("7", Platform.IOS))).details();

        assertThat(bare.name()).isEqualTo("Bare");
        assertThat(bare.kind()).isEqualTo(AppKind.OTHER);
        assertThat(bare.firstReleaseDate()).isNull();
        assertThat(bare.platforms()).containsExactly(DeviceFamily.IPHONE);
        assertThat(bare.version()).isNull();
        assertThat(bare.price()).isNull();
        assertThat(bare.rating()).isNull();
        assertThat(bare.genres()).isEmpty();
        assertThat(bare.storefront().countryCode()).isEqualTo("de");
        assertThat(bare.storefront().language()).isNull();
    }

    @Test
    void anItemWithoutANameBreaksTheContract() {
        MzLookupResponse response =
                JSON.readValue("{ \"results\": { \"7\": { \"kind\": \"iosSoftware\" } } }", MzLookupResponse.class);

        assertThatThrownBy(() -> MzLookupMapper.toResult(response, query("7", Platform.IOS)))
                .isInstanceOf(UpstreamContractException.class);
    }

    private static AppDetails found(String fixture, String id, Platform platform) throws IOException {
        LookupResult result = MzLookupMapper.toResult(fixture(fixture), query(id, platform));
        assertThat(result).isInstanceOf(LookupResult.Found.class);
        return ((LookupResult.Found) result).details();
    }

    private static DetailsQuery query(String id, Platform platform) {
        return new DetailsQuery(id, "de", "de", platform);
    }

    static MzLookupResponse fixture(String name) throws IOException {
        try (InputStream in = MzLookupMapperTest.class.getResourceAsStream("/wiremock/__files/apple/lookup/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return JSON.readValue(in, MzLookupResponse.class);
        }
    }
}
