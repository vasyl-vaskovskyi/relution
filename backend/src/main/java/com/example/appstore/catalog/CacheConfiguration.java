package com.example.appstore.catalog;

import com.example.appstore.observability.LoaderExecutors;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Native Caffeine {@code AsyncCache}s (ADR-0030): single-flight per key, failed loads never cached, loaders on virtual
 * threads with the MDC. The loader executor is not a bean, so it can't replace Boot's application task executor.
 */
@Configuration(proxyBeanMethods = false)
class CacheConfiguration implements DisposableBean {

    static final String SEARCH_CACHE = "app-search";
    static final String DETAILS_CACHE = "app-details";

    private final ExecutorService loaderExecutor = LoaderExecutors.virtualThreadsWithMdc();

    @Bean
    AsyncCache<SearchCacheKey, AppSearchResult> appSearchCache(CacheProperties properties, MeterRegistry registry) {
        return CaffeineCacheMetrics.monitor(
                registry, searchCache(properties, loaderExecutor, Ticker.systemTicker()), SEARCH_CACHE);
    }

    @Bean
    AsyncCache<DetailsQuery, LookupResult> appDetailsCache(CacheProperties properties, MeterRegistry registry) {
        return CaffeineCacheMetrics.monitor(
                registry, detailsCache(properties, loaderExecutor, Ticker.systemTicker()), DETAILS_CACHE);
    }

    static AsyncCache<SearchCacheKey, AppSearchResult> searchCache(
            CacheProperties properties, Executor executor, Ticker ticker) {
        return Caffeine.newBuilder()
                .maximumSize(properties.search().size())
                .expireAfterWrite(properties.search().ttl())
                .executor(executor)
                .ticker(ticker)
                .recordStats()
                .buildAsync();
    }

    /** {@code Found} lives for {@code appstore.cache.details.ttl}, {@code NotFound} for the shorter not-found TTL. */
    static AsyncCache<DetailsQuery, LookupResult> detailsCache(
            CacheProperties properties, Executor executor, Ticker ticker) {
        Expiry<DetailsQuery, LookupResult> expiry =
                Expiry.creating((DetailsQuery query, LookupResult result) -> result instanceof LookupResult.Found
                        ? properties.details().ttl()
                        : properties.notFound().ttl());
        return Caffeine.newBuilder()
                .maximumSize(properties.details().size())
                .expireAfter(expiry)
                .executor(executor)
                .ticker(ticker)
                .recordStats()
                .buildAsync();
    }

    @Override
    public void destroy() {
        loaderExecutor.close();
    }
}
