package com.example.appstore.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the correlation id into the MDC for the whole request and echoes it in the {@code X-Correlation-Id} response
 * header ({@code docs/architecture/error-handling.md#correlation-id}).
 *
 * <p>Without tracing, the id is the valid incoming one or a new UUID. With tracing (Level 2, {@code
 * management.tracing.export.enabled=true}), it is the current trace id, and a valid incoming id is kept as {@code
 * clientCorrelationId}. Boot creates spans even while tracing export is off, but those trace ids are never exported or
 * propagated, so they would be useless as correlation ids; that's why the property decides.
 *
 * <p>Runs right after Boot's {@code ServerHttpObservationFilter} ({@code HIGHEST_PRECEDENCE + 1}), which opens the
 * server span, and before Spring Security, so every application log line of the request carries the id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final Supplier<String> currentTraceId;

    /** Without tracing (Level 1). */
    public CorrelationIdFilter() {
        this(() -> null);
    }

    @Autowired
    public CorrelationIdFilter(
            ObjectProvider<Tracer> tracer, @Value("${management.tracing.export.enabled:true}") boolean tracingEnabled) {
        this(tracingEnabled ? () -> traceIdOf(tracer.getIfAvailable()) : () -> null);
    }

    /** @param currentTraceId returns the current trace id, or {@code null} without a trace */
    CorrelationIdFilter(Supplier<String> currentTraceId) {
        this.currentTraceId = currentTraceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationId.HEADER);
        String traceId = currentTraceId.get();
        String correlationId = traceId != null ? traceId : CorrelationId.resolve(incoming);
        Optional<String> clientCorrelationId = traceId != null ? CorrelationId.valid(incoming) : Optional.empty();

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        clientCorrelationId.ifPresent(id -> MDC.put(CorrelationId.CLIENT_MDC_KEY, id));
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
            MDC.remove(CorrelationId.CLIENT_MDC_KEY);
        }
    }

    /** The current span's trace id, or {@code null} without a tracer, a span or a valid id (all zeros is invalid). */
    static String traceIdOf(Tracer tracer) {
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null) {
            return null;
        }
        String traceId = span.context().traceId();
        boolean valid = traceId != null
                && !traceId.isEmpty()
                && CorrelationId.valid(traceId).isPresent()
                && traceId.chars().anyMatch(c -> c != '0');
        return valid ? traceId : null;
    }
}
