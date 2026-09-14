package com.example.appstore.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.storefront.StorefrontPolicy;
import com.example.appstore.catalog.storefront.UnsupportedStorefrontException;
import com.example.appstore.observability.LoaderExecutors;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Most tests load on the calling thread, so Caffeine's completion bookkeeping (write time, removal of failed futures)
 * has finished before the fake ticker moves. Concurrency, MDC and failure removal use the real virtual-thread loaders.
 */
class AppSearchServiceTest {

    static final CacheProperties CACHE = new CacheProperties(
            new CacheProperties.Entry(Duration.ofMinutes(10), 1_000),
            new CacheProperties.Entry(Duration.ofMinutes(15), 5_000),
            new CacheProperties.NotFound(Duration.ofSeconds(60)));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<SearchQuery> queries = new CopyOnWriteArrayList<>();
    private final List<String> loaderCorrelationIds = new CopyOnWriteArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private final ExecutorService loaders = LoaderExecutors.virtualThreadsWithMdc();
    private volatile RuntimeException failure;
    private volatile CountDownLatch release;

    private final AppSearchGateway gateway = query -> {
        queries.add(query);
        loaderCorrelationIds.add(String.valueOf(MDC.get("correlationId")));
        awaitRelease();
        if (failure != null) {
            throw failure;
        }
        return List.of(new AppSummary("1", "Pages", null, null, AppKind.IOS_APP, null, null));
    };
    private final AppSearchService service = serviceLoadingOn(Runnable::run);

    @AfterEach
    void closeLoaders() {
        MDC.clear();
        loaders.close();
    }

    @Test
    void searchesWithTheNormalizedQuery() {
        AppSearchResult result = service.search("  pages ", "DE", 5);

        assertThat(queries).containsExactly(new SearchQuery("pages", "de", 5));
        assertThat(result.countryCode()).isEqualTo("de");
        assertThat(result.items()).extracting(AppSummary::name).containsExactly("Pages");
    }

    @Test
    void termsThatDifferOnlyInCaseShareOneEntryAndAppleGetsTheFirstSpelling() {
        service.search("WhatsApp", "de", 5);
        service.search("whatsapp", "DE", 5);
        service.search("  WHATSAPP ", "de", 5);

        assertThat(queries).containsExactly(new SearchQuery("WhatsApp", "de", 5));
    }

    @Test
    void limitAndStorefrontArePartOfTheKey() {
        service.search("pages", "de", 5);
        service.search("pages", "de", 6);
        service.search("pages", "at", 5);

        assertThat(queries).hasSize(3);
    }

    @Test
    void entriesExpireAfterTheSearchTtl() {
        service.search("pages", "de", 5);
        nanos.addAndGet(Duration.ofMinutes(9).toNanos());
        service.search("pages", "de", 5);
        assertThat(queries).hasSize(1);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        service.search("pages", "de", 5);
        assertThat(queries).hasSize(2);
    }

    @Test
    void concurrentIdenticalSearchesShareOneUpstreamCall() throws Exception {
        AppSearchService async = serviceLoadingOn(loaders);
        release = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AppSearchResult>> results = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 8; i++) {
                String term = i % 2 == 0 ? "pages" : "PAGES";
                results.add(callers.submit(() -> async.search(term, "de", 5)));
            }
            Thread.sleep(200); // let every caller reach the in-flight entry
            release.countDown();

            for (Future<AppSearchResult> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS).items()).hasSize(1);
            }
        }
        assertThat(queries).hasSize(1);
    }

    @Test
    void theLoaderSeesTheCallersCorrelationId() {
        MDC.put("correlationId", "corr-42");

        serviceLoadingOn(loaders).search("pages", "de", 5);

        assertThat(loaderCorrelationIds).containsExactly("corr-42");
    }

    @Test
    void aFailedLoadIsNeverServedToTheNextCaller() {
        AppSearchService async = serviceLoadingOn(loaders);
        // repeated, because Caffeine's own removal of a failed future races with the caller;
        // each round uses its own key, so the previous round's cached success can't interfere
        for (int i = 0; i < 25; i++) {
            String term = "pages " + i;
            failure = new UpstreamServerErrorException(503, "down", null);
            assertThatThrownBy(() -> async.search(term, "de", 5)).isSameAs(failure);

            failure = null;
            assertThat(async.search(term, "de", 5).items()).hasSize(1);
        }
        assertThat(queries).hasSize(50);
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

    private AppSearchService serviceLoadingOn(Executor executor) {
        return new AppSearchService(
                new StorefrontPolicy(registry), gateway, CacheConfiguration.searchCache(CACHE, executor, nanos::get));
    }

    private void awaitRelease() {
        CountDownLatch latch = release;
        if (latch == null) {
            return;
        }
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double outdatedSignals() {
        return registry.get(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                .tag(MetricNames.TAG_DIRECTION, "outdated")
                .counter()
                .count();
    }
}
