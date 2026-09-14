package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.catalog.AppDetails;
import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.DetailsQuery;
import com.example.appstore.catalog.LookupResult;
import com.example.appstore.catalog.Platform;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nightly drift check against the real Apple APIs (ADR-0037, {@code docs/development/testing.md}). Exactly three calls:
 * one Search and two lookups of pinned apps. Uses the production clients, mappers, drift detector and the URLs and
 * timeouts from {@code application.yml}, without starting the application. Asserts the required-key list of
 * {@link AppleMissingFieldDetector} plus known stable values. Nothing here logs a response body.
 */
@Tag("live")
class AppleDriftLiveTest {

    private static final String PAGES_ID = "361309726";
    private static final String FINAL_CUT_PRO_ID = "424389933";

    private static final AppleProperties PROPERTIES = productionProperties();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppleMissingFieldDetector detector = new AppleMissingFieldDetector(registry);

    @Test
    void searchRowsCarryTheRequiredKeys() {
        ItunesSearchClient client =
                new ItunesSearchClient(restClient(PROPERTIES.search().url().toString()), JSON);

        ItunesSearchResponse response = client.search("pages", "de", 10);
        detector.inspect(response);

        assertNoMissingFields();
        assertThat(response.results()).isNotEmpty();
        assertThat(response.results())
                .extracting(ItunesSearchResponse.Row::kind)
                .allMatch(kind -> kind.equals("software") || kind.equals("mac-software"));
        assertThat(response.results())
                .extracting(ItunesSearchResponse.Row::trackId)
                .contains(PAGES_ID);
    }

    @Test
    void iosLookupOfPagesCarriesTheRequiredKeysAndKnownValues() {
        MzLookupResponse response = lookupClient().lookup(PAGES_ID, "de", "de", Platform.IOS);
        detector.inspect(response);

        assertNoMissingFields();
        MzLookupResponse.Item item = response.results().get(PAGES_ID);
        assertThat(item.kind()).isEqualTo("iosSoftware");
        assertThat(item.bundleId()).isEqualTo("com.apple.Pages");
        assertThat(item.offers()).isNotEmpty();
        assertThat(item.offers().getFirst().version().display()).isNotBlank();

        AppDetails pages = found(response, PAGES_ID, Platform.IOS);
        assertThat(pages.kind()).isEqualTo(AppKind.IOS_APP);
        assertThat(pages.universal()).isTrue();
        assertThat(pages.iconUrl()).isNotNull();
        assertThat(pages.storefront().countryCode()).isEqualTo("de");
    }

    @Test
    void macLookupOfFinalCutProCarriesTheRequiredKeysAndKnownValues() {
        MzLookupResponse response = lookupClient().lookup(FINAL_CUT_PRO_ID, "de", "de", Platform.MAC);
        detector.inspect(response);

        assertNoMissingFields();
        MzLookupResponse.Item item = response.results().get(FINAL_CUT_PRO_ID);
        assertThat(item.kind()).isEqualTo("desktopApp");
        assertThat(item.bundleId()).isEqualTo("com.apple.FinalCut");
        assertThat(item.deviceFamilies()).containsExactly("mac");

        AppDetails finalCut = found(response, FINAL_CUT_PRO_ID, Platform.MAC);
        assertThat(finalCut.kind()).isEqualTo(AppKind.MAC_APP);
        assertThat(finalCut.version()).isNotBlank();
        assertThat(finalCut.iconUrl()).isNotNull();
    }

    private void assertNoMissingFields() {
        assertThat(registry.find(MetricNames.APPLE_MAPPING_MISSING_FIELD).counters())
                .as(
                        "required keys missing (search %s, lookup %s)",
                        AppleMissingFieldDetector.SEARCH_ROW_FIELDS, AppleMissingFieldDetector.LOOKUP_APP_FIELDS)
                .isEmpty();
    }

    private static AppDetails found(MzLookupResponse response, String id, Platform platform) {
        LookupResult result = MzLookupMapper.toResult(response, new DetailsQuery(id, "de", "de", platform));
        assertThat(result).isInstanceOf(LookupResult.Found.class);
        return ((LookupResult.Found) result).details();
    }

    private static MzLookupClient lookupClient() {
        return new MzLookupClient(restClient(PROPERTIES.lookup().url().toString()), JSON);
    }

    private static RestClient restClient(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(AppleClientConfiguration.requestFactory(PROPERTIES))
                .build();
    }

    /** Binds {@code appstore.apple.*} from the production {@code application.yml}, so URLs and timeouts can't drift. */
    private static AppleProperties productionProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("appstore.apple", AppleProperties.class)
                .get();
    }
}
