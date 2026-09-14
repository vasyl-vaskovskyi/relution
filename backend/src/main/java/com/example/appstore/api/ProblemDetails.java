package com.example.appstore.api;

import com.example.appstore.observability.CorrelationId;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/**
 * Builds every RFC 9457 problem response of the service, including the ones written by security handlers (the only
 * allowed {@code auth -> api} dependency, ADR-0028). Details are fixed texts: never Apple bodies, exception messages or
 * query strings.
 */
public final class ProblemDetails {

    public static final String CORRELATION_ID = "correlationId";
    public static final String ERRORS = "errors";

    /** One invalid parameter in {@code errors[]}. */
    public record FieldError(String field, String message) {}

    private ProblemDetails() {}

    /**
     * @param path the request path without the query string, used as {@code instance}; may be {@code null}
     */
    public static ProblemDetail of(ProblemType type, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problem.setType(type.type());
        problem.setTitle(type.title());
        setInstance(problem, path);
        addCorrelationId(problem);
        return problem;
    }

    public static ProblemDetail invalidRequest(List<FieldError> errors, String path) {
        ProblemDetail problem = of(ProblemType.INVALID_REQUEST, "One or more parameters are invalid.", path);
        problem.setProperty(ERRORS, List.copyOf(errors));
        return problem;
    }

    /** Adds the current correlation id to a problem built elsewhere (e.g. by Spring for an unknown path). */
    public static void addCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(CORRELATION_ID, correlationId);
        }
    }

    private static void setInstance(ProblemDetail problem, String path) {
        if (path == null) {
            return;
        }
        try {
            problem.setInstance(URI.create(path));
        } catch (IllegalArgumentException e) {
            // a path that isn't a valid URI is simply left out
        }
    }
}
