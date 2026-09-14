package com.example.appstore.catalog;

/** The connection was established, but Apple didn't answer within the read timeout. Not retried. */
public final class UpstreamReadTimeoutException extends UpstreamException {

    public UpstreamReadTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
