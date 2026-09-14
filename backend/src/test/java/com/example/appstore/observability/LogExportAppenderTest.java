package com.example.appstore.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The root logger is shared by every context in the test JVM (a cached {@code @SpringBootTest} context may have its
 * own appender attached), so the assertions look at this context's appender instance, not at appender names.
 */
class LogExportAppenderTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OpenTelemetry.class, OpenTelemetry::noop)
            .withUserConfiguration(LogExportAppender.class);

    @Test
    void theAppenderIsAbsentUnlessExportIsEnabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LogExportAppender.class));
        runner.withPropertyValues("management.logging.export.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LogExportAppender.class));
    }

    @Test
    void exportEnabledAttachesTheAppenderToTheRootLoggerUntilTheContextCloses() {
        AtomicReference<OpenTelemetryAppender> attached = new AtomicReference<>();

        runner.withPropertyValues("management.logging.export.enabled=true").run(context -> {
            OpenTelemetryAppender appender =
                    context.getBean(LogExportAppender.class).appender();
            attached.set(appender);
            assertThat(appender).isNotNull();
            assertThat(appender.isStarted()).isTrue();
            assertThat(root().isAttached(appender)).isTrue();
        });

        assertThat(root().isAttached(attached.get())).isFalse();
        assertThat(attached.get().isStarted()).isFalse();
    }

    private static Logger root() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
