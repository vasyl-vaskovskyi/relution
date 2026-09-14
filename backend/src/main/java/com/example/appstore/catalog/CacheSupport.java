package com.example.appstore.catalog;

import com.github.benmanes.caffeine.cache.AsyncCache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Single-flight loading through an {@link AsyncCache} that never serves a failed load to a later caller. */
final class CacheSupport {

    private CacheSupport() {}

    /**
     * Returns the cached value or loads it on the cache's executor; concurrent callers for the same key share one load.
     * On failure the loader's own exception is rethrown, so callers see the upstream failure type.
     */
    static <K, V> V getOrLoad(AsyncCache<K, V> cache, K key, Supplier<V> loader) {
        CompletableFuture<V> future =
                cache.get(key, (ignored, executor) -> CompletableFuture.supplyAsync(loader, executor));
        try {
            return future.join();
        } catch (CompletionException e) {
            // Caffeine removes a failed future asynchronously, after join() has already returned here.
            // Remove exactly this future now, so the next caller reaches the upstream instead of this failure.
            cache.asMap().remove(key, future);
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
