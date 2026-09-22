package com.dependencyhealth.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.Severity;
import com.dependencyhealth.contract.kafka.EventPublisher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LicenseScanJobTest {
    @Test
    void unpinnedRepositoryDependencyChecksLatestMetadataAndReportsTheAssumption() {
        var client = mock(RegistryLicenseClient.class);
        var publisher = mock(EventPublisher.class);
        var pkg = new WatchlistProperties.WatchedPackage("fastapi", "pypi", "");
        when(client.fetch(pkg)).thenReturn(new LicenseMetadata(List.of("MIT"), "0.116.0",
                "https://pypi.org/pypi/fastapi/json", "PyPI metadata"));
        var registry = URI.create("https://registry.example");
        var policy = new LicensePolicy(new LicenseProperties(List.of("GPL-*"), registry, registry, registry, 5));
        var job = new LicenseScanJob(new WatchlistProperties(List.of()), client, policy, publisher);
        job.scanPackage(pkg, Map.of("repositoryRequestId", "request-1", "repositoryScan", true,
                "discoveredVersion", "unspecified"));
        var event = ArgumentCaptor.forClass(DependencyEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().severity()).isEqualTo(Severity.WARNING);
        assertThat(event.getValue().summary()).contains("latest release", "unpinned");
        assertThat(event.getValue().detail()).containsEntry("version", "0.116.0")
                .containsKey("versionAssumption");
    }
}
