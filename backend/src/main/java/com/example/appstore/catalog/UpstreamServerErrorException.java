package com.example.appstore.catalog;

/**
 * Apple answered with a 5xx status, or the connection broke after it was established. {@code status} is {@code 0} when
 * no HTTP status was received. Not retried.
 */
public final class UpstreamServerErrorException extends UpstreamException {

    private final int status;

    public UpstreamServerErrorException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
