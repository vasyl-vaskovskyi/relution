package com.example.appstore.catalog;

/**
 * An Apple upstream call failed. The subtypes are the complete set of failure kinds; the API layer maps them with an
 * exhaustive {@code switch} ({@code docs/architecture/error-handling.md}).
 *
 * <p>Messages never contain Apple response bodies, search terms or credentials.
 */
public abstract sealed class UpstreamException extends RuntimeException
        permits UpstreamConnectException,
                UpstreamReadTimeoutException,
                UpstreamServerErrorException,
                UpstreamRateLimitedException,
                UpstreamCircuitOpenException,
                UpstreamContractException,
                StorefrontNotServedException {

    protected UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
