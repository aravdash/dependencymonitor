package com.dependencyhealth.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RepositoryInventoryLicenseListenerTest {
    @Test
    void convertsAnUnpinnedPypiVersionToLatestRegistryLookup() {
        var codec = new RepositoryMessageCodec(new ObjectMapper());
        var job = mock(LicenseScanJob.class);
        var listener = new RepositoryInventoryLicenseListener(codec, job);
        listener.receive(codec.write(inventory()));
        ArgumentCaptor<WatchlistProperties.WatchedPackage> pkg = ArgumentCaptor.forClass(
                WatchlistProperties.WatchedPackage.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(job).scanPackage(pkg.capture(), context.capture());
        assertThat(pkg.getValue().version()).isBlank();
        assertThat(context.getValue()).containsEntry("discoveredVersion", "unspecified")
                .containsEntry("repositoryScan", true);
    }

    private RepositoryInventoryEvent inventory() {
        return new RepositoryInventoryEvent("123e4567-e89b-12d3-a456-426614174000",
                "github:aravdash/robofleet", "https://github.com/aravdash/RoboFleet", InventoryStatus.PARTIAL,
                List.of(new DiscoveredDependency("fastapi", "pypi", "unspecified", "pkg:pypi/fastapi", true, "unknown")),
                0, "Discovered", Instant.parse("2026-09-20T12:00:00Z"));
    }
}
