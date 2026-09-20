package com.dependencyhealth.contract.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryInventoryEvent(String requestId, String repositoryId, String repositoryUrl,
                                       InventoryStatus status, List<DiscoveredDependency> dependencies,
                                       int unsupportedCount, String message, Instant timestamp) {
    public RepositoryInventoryEvent {
        if (requestId == null || !UUID.fromString(requestId).toString().equalsIgnoreCase(requestId))
            throw new IllegalArgumentException("requestId must be a UUID");
        if (repositoryId == null || !repositoryId.matches("github:[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}"))
            throw new IllegalArgumentException("repositoryId is invalid");
        if (repositoryUrl == null || !repositoryUrl.startsWith("https://github.com/"))
            throw new IllegalArgumentException("repositoryUrl is invalid");
        if (status == null || unsupportedCount < 0 || message == null || timestamp == null)
            throw new IllegalArgumentException("status, counts, message and timestamp are required");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        if (status == InventoryStatus.FAILED && !dependencies.isEmpty())
            throw new IllegalArgumentException("A failed inventory cannot contain dependencies");
    }
}
