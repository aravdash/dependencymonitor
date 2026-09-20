package com.dependencyhealth.contract.repository;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record RepositoryScanRequest(String requestId, String provider, String owner, String repository,
                                    String repositoryUrl, Instant requestedAt) {
    public RepositoryScanRequest {
        if (requestId == null || !UUID.fromString(requestId).toString().equalsIgnoreCase(requestId))
            throw new IllegalArgumentException("requestId must be a UUID");
        if (!"github".equals(provider)) throw new IllegalArgumentException("Only github is supported");
        validateSegment(owner, "owner");
        validateSegment(repository, "repository");
        if (repositoryUrl == null || !repositoryUrl.equals("https://github.com/" + owner + "/" + repository))
            throw new IllegalArgumentException("repositoryUrl is not canonical");
        if (requestedAt == null) throw new IllegalArgumentException("requestedAt is required");
    }

    public String repositoryId() {
        return provider + ":" + owner.toLowerCase(Locale.ROOT) + "/" + repository.toLowerCase(Locale.ROOT);
    }

    private static void validateSegment(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,100}") || value.startsWith(".") || value.endsWith("."))
            throw new IllegalArgumentException(field + " is invalid");
    }
}
