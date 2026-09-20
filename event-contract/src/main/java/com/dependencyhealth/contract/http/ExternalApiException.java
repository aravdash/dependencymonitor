package com.dependencyhealth.contract.http;

import java.time.Duration;

public final class ExternalApiException extends RuntimeException {
    private final int statusCode;
    private final Duration retryAfter;
    public ExternalApiException(String message, int statusCode, Duration retryAfter, Throwable cause) {
        super(message, cause); this.statusCode = statusCode; this.retryAfter = retryAfter;
    }
    public int statusCode() { return statusCode; }
    public Duration retryAfter() { return retryAfter; }
}
