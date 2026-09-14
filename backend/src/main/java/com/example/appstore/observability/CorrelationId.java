package com.example.appstore.observability;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id rules, defined once ({@code docs/architecture/error-handling.md#correlation-id}). An incoming value is
 * used only if it is safe to log (no log injection); otherwise a new one is generated.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** With tracing, the trace id is the correlation id and a valid incoming id is kept under this MDC key. */
    public static final String CLIENT_MDC_KEY = "clientCorrelationId";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {}

    /** The incoming id if it is valid, otherwise a new random UUID. */
    public static String resolve(String incoming) {
        return valid(incoming).orElseGet(() -> UUID.randomUUID().toString());
    }

    /** The incoming id if it is safe to log. */
    public static Optional<String> valid(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? Optional.of(incoming) : Optional.empty();
    }
}
