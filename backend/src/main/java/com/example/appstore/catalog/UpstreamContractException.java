package com.example.appstore.catalog;

/**
 * Apple's answer doesn't match what the integration expects: an unexpected 4xx status or a malformed payload. Usually our
 * integration bug or Legacy API drift. {@code status} is the HTTP status, or {@code 0} for a malformed 2xx payload.
 */
public final class UpstreamContractException extends UpstreamException {

    private final int status;

    public UpstreamContractException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
