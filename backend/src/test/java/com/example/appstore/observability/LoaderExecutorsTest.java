package com.example.appstore.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LoaderExecutorsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void loaderRunsOnAVirtualThreadWithTheSelectedMdcKeys() throws Exception {
        MDC.put("correlationId", "corr-123");
        MDC.put("clientCorrelationId", "client-corr");
        MDC.put("clientId", "client-a");
        MDC.put("unrelated", "x");

        try (ExecutorService executor = LoaderExecutors.virtualThreadsWithMdc()) {
            String seen = executor.submit(() -> Thread.currentThread().isVirtual() + "|" + MDC.get("correlationId")
                            + "|" + MDC.get("clientCorrelationId") + "|" + MDC.get("clientId") + "|"
                            + MDC.get("unrelated"))
                    .get();

            assertThat(seen).isEqualTo("true|corr-123|client-corr|client-a|null");
        }
    }

    @Test
    void loaderThreadsDoNotKeepAnEarlierMdc() throws Exception {
        try (ExecutorService executor = LoaderExecutors.virtualThreadsWithMdc()) {
            MDC.put("correlationId", "first");
            executor.submit(() -> {}).get();
            MDC.clear();

            assertThat(executor.submit(() -> MDC.get("correlationId")).get()).isNull();
        }
    }

    @Test
    void theCurrentObservationReachesLoaderThreads() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        Observation request = Observation.start("request", registry);

        try (ExecutorService executor = LoaderExecutors.virtualThreadsWithMdc();
                Observation.Scope scope = request.openScope()) {
            assertThat(executor.submit(registry::getCurrentObservation).get())
                    .as("the Apple client observation becomes a child of the request")
                    .isSameAs(request);
        } finally {
            request.stop();
        }
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
