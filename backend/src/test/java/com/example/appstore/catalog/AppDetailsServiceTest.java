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

/**
 * Most tests load on the calling thread, so Caffeine's completion bookkeeping has finished before the fake ticker moves
 * (see {@link AppSearchServiceTest}). Concurrency and failure removal use the real virtual-thread loaders.
 */
class AppDetailsServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<DetailsQuery> queries = new CopyOnWriteArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private final ExecutorService loaders = LoaderExecutors.virtualThreadsWithMdc();
    private volatile LookupResult result = new LookupResult.Found(details("361309726"));
    private volatile RuntimeException failure;
    private volatile CountDownLatch release;

    private final AppDetailsGateway gateway = query -> {
        queries.add(query);
        awaitRelease();
        if (failure != null) {
            throw failure;
        }
        return result;
    };
    private final AppDetailsService service = serviceLoadingOn(Runnable::run);

    @AfterEach
    void closeLoaders() {
        loaders.close();
    }

    @Test
    void returnsTheDetailsForANormalizedQuery() {
        assertThat(service.details("361309726", "DE", "de_DE", Platform.MAC).name())
                .isEqualTo("Pages");
        assertThat(queries).containsExactly(new DetailsQuery("361309726", "de", "de-de", Platform.MAC));
    }

    @Test
    void languageSpellingsShareOneEntryButPlatformsDoNot() {
        service.details("361309726", "de", "de-DE", Platform.IOS);
        service.details("361309726", "de", "de_de", Platform.IOS);
        service.details("361309726", "de", "de-de", Platform.MAC);

        assertThat(queries).hasSize(2);
    }

    @Test
    void notFoundBecomesAppNotFound() {
        result = new LookupResult.NotFound();

        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS))
                .isInstanceOfSatisfying(
                        AppNotFoundException.class, e -> assertThat(e.id()).isEqualTo("1"));
    }

    @Test
    void notFoundIsCachedForSixtySeconds() {
        result = new LookupResult.NotFound();

        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS))
                .isInstanceOf(AppNotFoundException.class);
        nanos.addAndGet(Duration.ofSeconds(59).toNanos());
        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS))
                .isInstanceOf(AppNotFoundException.class);
        assertThat(queries).hasSize(1);

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThatThrownBy(() -> service.details("1", "de", "de", Platform.IOS))
                .isInstanceOf(AppNotFoundException.class);
        assertThat(queries).hasSize(2);
    }

    @Test
    void foundIsCachedForFifteenMinutes() {
        service.details("361309726", "de", "de", Platform.IOS);
        nanos.addAndGet(Duration.ofMinutes(14).toNanos());
        service.details("361309726", "de", "de", Platform.IOS);
        assertThat(queries).hasSize(1);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        service.details("361309726", "de", "de", Platform.IOS);
        assertThat(queries).hasSize(2);
    }

    @Test
    void concurrentLookupsOfAnUnknownIdShareOneUpstreamCall() throws Exception {
        AppDetailsService async = serviceLoadingOn(loaders);
        result = new LookupResult.NotFound();
        release = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> calls = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(callers.submit(() -> async.details("1", "de", "de", Platform.IOS)));
            }
            Thread.sleep(200); // let every caller reach the in-flight entry
            release.countDown();

            for (Future<?> call : calls) {
                assertThatThrownBy(() -> call.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(AppNotFoundException.class);
            }
        }
        assertThat(queries).hasSize(1);
    }

    @Test
    void aFailedLoadIsNeverServedToTheNextCaller() {
        AppDetailsService async = serviceLoadingOn(loaders);
        // each round uses its own id, so the previous round's cached success can't interfere
        for (int i = 0; i < 25; i++) {
            String id = String.valueOf(1_000 + i);
            failure = new UpstreamReadTimeoutException("slow", null);
            assertThatThrownBy(() -> async.details(id, "de", "de", Platform.IOS))
                    .isSameAs(failure);

            failure = null;
            assertThat(async.details(id, "de", "de", Platform.IOS).name()).isEqualTo("Pages");
        }
        assertThat(queries).hasSize(50);
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

    private AppDetailsService serviceLoadingOn(Executor executor) {
        return new AppDetailsService(
                new StorefrontPolicy(registry),
                gateway,
                CacheConfiguration.detailsCache(AppSearchServiceTest.CACHE, executor, nanos::get));
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
                new Storefront("de", "de-de", Platform.IOS));
    }
}
