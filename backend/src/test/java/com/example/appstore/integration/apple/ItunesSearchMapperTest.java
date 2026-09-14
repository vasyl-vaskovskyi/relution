package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.AppSummary;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ItunesSearchMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void mapsCapturedIosResults() throws IOException {
        List<AppSummary> items = ItunesSearchMapper.toSummaries(fixture("200-apps-de.json"));

        assertThat(items).hasSize(2);
        AppSummary relution = items.getFirst();
        assertThat(relution.id()).isEqualTo("699597062");
        assertThat(relution.name()).isEqualTo("Relution");
        assertThat(relution.developer()).isEqualTo("MWAY DIGITAL GmbH");
        assertThat(relution.iconUrl())
                .startsWith("https://is1-ssl.mzstatic.com/")
                .endsWith("/512x512bb.jpg");
        assertThat(relution.kind()).isEqualTo(AppKind.IOS_APP);
        assertThat(relution.price().amount()).isEqualByComparingTo("0").hasToString("0.00");
        assertThat(relution.price().currency()).isEqualTo("EUR");
        assertThat(relution.price().formatted()).isEqualTo("Gratis");
        assertThat(relution.rating().average()).isBetween(3.45, 3.46);
        assertThat(relution.rating().count()).isEqualTo(11L);
    }

    @Test
    void mapsMacRowsWithoutIosOnlyFields() throws IOException {
        List<AppSummary> items = ItunesSearchMapper.toSummaries(fixture("200-mixed-ios-mac-de.json"));

        assertThat(items).extracting(AppSummary::kind).containsExactly(AppKind.MAC_APP, AppKind.IOS_APP);
        AppSummary paid = items.get(1);
        assertThat(paid.price().amount()).isEqualTo(new BigDecimal("2.99"));
        // Apple separates amount and currency with a no-break space (U+00A0); passed through unchanged
        assertThat(paid.price().formatted()).isEqualTo("2,99 €");
    }

    @Test
    void noResultsMapToAnEmptyList() throws IOException {
        assertThat(ItunesSearchMapper.toSummaries(fixture("200-no-results-de.json")))
                .isEmpty();
    }

    @Test
    void toleratesMissingBlankAndUnexpectedValues() {
        ItunesSearchResponse response = JSON.readValue("""
                { "resultCount": 5, "results": [
                  { "trackName": "no id" },
                  { "trackId": 1, "trackName": "   " },
                  null,
                  { "kind": "ebook", "trackId": "42", "trackName": " Book ", "artistName": " ",
                    "artworkUrl100": "https://example.test/100x100bb.jpg", "unknownKey": true },
                  { "kind": null, "trackId": 7, "trackName": "Bare", "price": null, "averageUserRating": null }
                ] }
                """, ItunesSearchResponse.class);

        List<AppSummary> items = ItunesSearchMapper.toSummaries(response);

        assertThat(items).hasSize(2);
        AppSummary book = items.getFirst();
        assertThat(book.id()).isEqualTo("42");
        assertThat(book.name()).isEqualTo("Book");
        assertThat(book.developer()).isNull();
        assertThat(book.iconUrl()).isEqualTo("https://example.test/100x100bb.jpg");
        assertThat(book.kind()).isEqualTo(AppKind.OTHER);
        AppSummary bare = items.get(1);
        assertThat(bare.kind()).isEqualTo(AppKind.OTHER);
        assertThat(bare.iconUrl()).isNull();
        assertThat(bare.price()).isNull();
        assertThat(bare.rating()).isNull();
    }

    @Test
    void missingResultsArrayMapsToAnEmptyList() {
        assertThat(ItunesSearchMapper.toSummaries(new ItunesSearchResponse(0, null)))
                .isEmpty();
        assertThat(ItunesSearchMapper.toSummaries(null)).isEmpty();
    }

    static ItunesSearchResponse fixture(String name) throws IOException {
        try (InputStream in =
                ItunesSearchMapperTest.class.getResourceAsStream("/wiremock/__files/apple/search/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return JSON.readValue(in, ItunesSearchResponse.class);
        }
    }
}
