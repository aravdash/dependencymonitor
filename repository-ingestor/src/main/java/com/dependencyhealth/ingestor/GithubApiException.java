package com.dependencyhealth.ingestor;

final class GithubApiException extends RuntimeException {
    private final int status;
    GithubApiException(String message, int status) { super(message); this.status = status; }
    int status() { return status; }
}
