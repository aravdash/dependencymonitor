package com.dependencyhealth.dashboard;

import java.time.Instant;

public record RepositoryScanView(String requestId, String repositoryId, String repositoryUrl, String status,
                                 int dependencyCount, int unsupportedCount, String message,
                                 Instant requestedAt, Instant completedAt) { }
