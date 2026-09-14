package com.example.appstore.api;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The only place problem-type URNs, titles and statuses are defined ({@code docs/api/README.md#errors}). New values are
 * additive; clients must tolerate unknown types.
 */
public enum ProblemType {
    INVALID_REQUEST("invalid-request", "Invalid request", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_STOREFRONT("unsupported-storefront", "Unsupported storefront", HttpStatus.BAD_REQUEST),
    APP_NOT_FOUND("app-not-found", "App not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED("unauthorized", "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("forbidden", "Forbidden", HttpStatus.FORBIDDEN),
    UPSTREAM_UNAVAILABLE("upstream-unavailable", "Upstream unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    UPSTREAM_TIMEOUT("upstream-timeout", "Upstream timeout", HttpStatus.GATEWAY_TIMEOUT),
    UPSTREAM_ERROR("upstream-error", "Upstream error", HttpStatus.BAD_GATEWAY),
    INTERNAL("internal", "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String URN_PREFIX = "urn:appstore:problem:";

    private final URI type;
    private final String title;
    private final HttpStatus status;

    ProblemType(String slug, String title, HttpStatus status) {
        this.type = URI.create(URN_PREFIX + slug);
        this.title = title;
        this.status = status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }
}
