package com.example.appstore.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import com.example.appstore.catalog.storefront.UnsupportedStorefrontException;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppSearchServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<SearchQuery> queries = new ArrayList<>();
    private RuntimeException failure;
    private final AppSearchGateway gateway = query -> {
        queries.add(query);
        if (failure != null) {
            throw failure;
        }
        return List.of(new AppSummary("1", "Pages", null, null, AppKind.IOS_APP, null, null));
    };
    private final AppSearchService service = new AppSearchService(new StorefrontPolicy(registry), gateway);

    @Test
    void searchesWithTheNormalizedQuery() {
        AppSearchResult result = service.search("  pages ", "DE", 5);

        assertThat(queries).containsExactly(new SearchQuery("pages", "de", 5));
        assertThat(result.countryCode()).isEqualTo("de");
        assertThat(result.items()).extracting(AppSummary::name).containsExactly("Pages");
    }

    @Test
    void unsupportedStorefrontNeverReachesTheGateway() {
        assertThatThrownBy(() -> service.search("pages", "cu", 5)).isInstanceOf(UnsupportedStorefrontException.class);
        assertThat(queries).isEmpty();
    }

    @Test
    void appleRejectingAnAllowlistedStorefrontIsReportedAndRethrown() {
        failure = new StorefrontNotServedException("de", null);

        assertThatThrownBy(() -> service.search("pages", "de", 5)).isSameAs(failure);
        assertThat(outdatedSignals()).isEqualTo(1);
    }

    @Test
    void otherUpstreamFailuresPassThroughWithoutAllowlistSignal() {
        failure = new UpstreamServerErrorException(503, "down", null);

        assertThatThrownBy(() -> service.search("pages", "de", 5)).isSameAs(failure);
        assertThat(outdatedSignals()).isZero();
    }

    private double outdatedSignals() {
        return registry.get(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                .tag(MetricNames.TAG_DIRECTION, "outdated")
                .counter()
                .count();
    }
}
