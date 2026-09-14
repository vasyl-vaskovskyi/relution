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

class AppDetailsServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<DetailsQuery> queries = new ArrayList<>();
    private LookupResult result;
    private RuntimeException failure;
    private final AppDetailsGateway gateway = query -> {
        queries.add(query);
        if (failure != null) {
            throw failure;
        }
        return result;
    };
    private final AppDetailsService service = new AppDetailsService(new StorefrontPolicy(registry), gateway);

    @Test
    void returnsTheDetailsForANormalizedQuery() {
        AppDetails pages = details("361309726");
        result = new LookupResult.Found(pages);

        assertThat(service.details("361309726", "DE", "de_DE", Platform.MAC)).isSameAs(pages);
        assertThat(queries).containsExactly(new DetailsQuery("361309726", "de", "de-de", Platform.MAC));
    }

    @Test
    void notFoundBecomesAppNotFound() {
        result = new LookupResult.NotFound();

        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(
                        AppNotFoundException.class, e -> assertThat(e.id()).isEqualTo("1"));
    }

    @Test
    void unsupportedStorefrontNeverReachesTheGateway() {
        assertThatThrownBy(() -> service.details("1", "cu", "de", Platform.IOS))
                .isInstanceOf(UnsupportedStorefrontException.class);
        assertThat(queries).isEmpty();
    }

    @Test
    void appleServingAnotherStorefrontIsReportedAndRethrown() {
        failure = new StorefrontNotServedException("de", "us");

        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS)).isSameAs(failure);
        assertThat(registry.get(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                        .tag(MetricNames.TAG_DIRECTION, "outdated")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    private static AppDetails details(String id) {
        return new AppDetails(
                id,
                "Pages",
                AppKind.IOS_APP,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                new Storefront("de", "de-de", Platform.MAC));
    }
}
