package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import java.time.Instant;
import java.util.List;

public record PackageStatus(String packageName, String ecosystem, Instant lastUpdated,
                            List<DependencyEvent> latestFindings) {
    public PackageStatus {
        latestFindings = List.copyOf(latestFindings);
    }
}
