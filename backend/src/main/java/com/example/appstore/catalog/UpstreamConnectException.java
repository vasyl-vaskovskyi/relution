package com.example.appstore.catalog;

/** Connect timeout, connection refused or DNS failure. The only failure kind that is retried. */
public final class UpstreamConnectException extends UpstreamException {

    public UpstreamConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
