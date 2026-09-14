package com.example.appstore.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Level 2 log export (ADR-0051): attaches the OpenTelemetry Logback appender to the root logger, so Boot's OTLP log
 * exporter receives the same events as the console. Only present when {@code management.logging.export.enabled=true}
 * ({@code compose.observability.yml}); otherwise Logback keeps Boot's own configuration untouched.
 *
 * <p>The appender is added programmatically instead of in a {@code logback-spring.xml}, so the console appender (plain
 * text or ECS JSON) stays Boot's default. No MDC attribute, argument, marker or key-value pair is captured: a record
 * carries the formatted message, level, logger name, exception and the trace context, nothing else.
 */
@Component
@ConditionalOnBooleanProperty("management.logging.export.enabled")
public class LogExportAppender implements InitializingBean, DisposableBean {

    static final String APPENDER_NAME = "OTLP";

    private final OpenTelemetry openTelemetry;
    private OpenTelemetryAppender appender;
    private Logger root;

    public LogExportAppender(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        OpenTelemetryAppender otlp = new OpenTelemetryAppender();
        otlp.setContext(context);
        otlp.setName(APPENDER_NAME);
        otlp.setOpenTelemetry(openTelemetry);
        otlp.start();
        root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(otlp);
        appender = otlp;
    }

    /** The attached appender, or {@code null} when Logback isn't the logging backend. */
    OpenTelemetryAppender appender() {
        return appender;
    }

    @Override
    public void destroy() {
        if (appender != null) {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
