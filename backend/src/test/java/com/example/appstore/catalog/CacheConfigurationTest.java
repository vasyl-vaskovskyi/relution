package com.example.appstore.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.AsyncCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;

class CacheConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfiguration.class)
            .withBean(CacheProperties.class, () -> AppSearchServiceTest.CACHE)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void bothCachesAreBeansWithStatisticsBoundToMicrometer() {
        runner.run(context -> {
            assertThat(context.getBeanProvider(ResolvableType.forClassWithGenerics(
                                    AsyncCache.class, SearchCacheKey.class, AppSearchResult.class))
                            .getIfAvailable())
                    .isNotNull();
            AsyncCache<?, ?> details = (AsyncCache<?, ?>) context.getBeanProvider(ResolvableType.forClassWithGenerics(
                            AsyncCache.class, DetailsQuery.class, LookupResult.class))
                    .getObject();
            assertThat(details.synchronous().policy().isRecordingStats()).isTrue();

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            List<String> caches = registry.find("cache.gets").meters().stream()
                    .map(meter -> meter.getId().getTag("cache"))
                    .distinct()
                    .toList();
            assertThat(caches).containsExactlyInAnyOrder("app-search", "app-details");
        });
    }
}
