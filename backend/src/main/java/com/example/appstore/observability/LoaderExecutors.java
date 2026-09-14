package com.example.appstore.observability;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executors for cache loaders. {@code spring.threads.virtual.enabled} covers request threads only, so loaders get their
 * own virtual threads. The MDC keys below and the current observation are copied from the submitting thread, so loader
 * log lines keep their ids and, with tracing, the Apple client span stays in the request's trace
 * ({@code docs/architecture/caching-resilience.md#caches}).
 */
public final class LoaderExecutors {

    /** Only these MDC keys reach loader threads. */
    public static final List<String> PROPAGATED_MDC_KEYS =
            List.of(CorrelationId.MDC_KEY, CorrelationId.CLIENT_MDC_KEY, "clientId");

    private LoaderExecutors() {}

    /**
     * A virtual-thread-per-task executor that propagates {@link #PROPAGATED_MDC_KEYS} and the current observation. The
     * caller must close it.
     */
    public static ExecutorService virtualThreadsWithMdc() {
        ContextRegistry registry = new ContextRegistry();
        registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(PROPAGATED_MDC_KEYS.toArray(String[]::new)));
        registry.registerThreadLocalAccessor(ObservationThreadLocalAccessor.getInstance());
        ContextSnapshotFactory snapshots =
                ContextSnapshotFactory.builder().contextRegistry(registry).build();
        return ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(), snapshots);
    }
}
