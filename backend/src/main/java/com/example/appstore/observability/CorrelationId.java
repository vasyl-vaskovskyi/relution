package com.example.appstore.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id rules, defined once ({@code docs/architecture/error-handling.md#correlation-id}). An incoming value is
 * used only if it is safe to log (no log injection); otherwise a new one is generated.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {}

    /** The incoming id if it is valid, otherwise a new random UUID. */
    public static String resolve(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
