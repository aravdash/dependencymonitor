package com.dependencyhealth.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Version 1 wire contract. Source-specific details may evolve additively. */
public record DependencyEvent(String eventId, String source, String packageName, String ecosystem,
                              Severity severity, String summary, Map<String, Object> detail, Instant timestamp) {
    private static final Set<String> SOURCES = Set.of("github-activity", "osv-cve", "license-check");

    public DependencyEvent {
        if (eventId == null || !UUID.fromString(eventId).toString().equalsIgnoreCase(eventId))
            throw new IllegalArgumentException("eventId must be a canonical UUID");
        if (!SOURCES.contains(source == null ? "" : source)) throw new IllegalArgumentException("Invalid source");
        if (packageName == null || packageName.isBlank()) throw new IllegalArgumentException("packageName is required");
        if (ecosystem == null || ecosystem.isBlank()) throw new IllegalArgumentException("ecosystem is required");
        if (severity == null || summary == null || summary.isBlank() || detail == null || timestamp == null)
            throw new IllegalArgumentException("severity, summary, detail and timestamp are required");
        detail = Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    /** Use a compound key so equal names in different registries do not share identity. */
    public String partitionKey() { return ecosystem + ":" + packageName; }
}
