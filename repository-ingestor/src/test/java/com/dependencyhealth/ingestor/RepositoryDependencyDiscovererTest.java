package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepositoryDependencyDiscovererTest {
    private final GithubSbomClient sbomClient = mock(GithubSbomClient.class);
    private final SpdxInventoryParser spdxParser = mock(SpdxInventoryParser.class);
    private final GithubArchiveClient archiveClient = mock(GithubArchiveClient.class);
    private final ManifestInventoryParser manifestParser = mock(ManifestInventoryParser.class);
    private final RepositoryDependencyDiscoverer discoverer = new RepositoryDependencyDiscoverer(
            sbomClient, spdxParser, archiveClient, manifestParser);

    @Test
    void fallsBackToSourceManifestsWhenGithubHasNoDependencyGraph() {
        RepositoryScanRequest request = request();
        var manifests = List.of(new RepositoryManifest("repo/requirements.txt", "fastapi\n"));
        var dependency = new DiscoveredDependency("fastapi", "pypi", "unspecified", "pkg:pypi/fastapi", true, "unknown");
        var inventory = new SpdxInventoryParser.Inventory(List.of(dependency), 0, 1);
        when(sbomClient.fetch(request)).thenThrow(new GithubApiException("Dependency graph not found", 404));
        when(archiveClient.fetch(request)).thenReturn(manifests);
        when(manifestParser.parse(manifests)).thenReturn(inventory);

        var result = discoverer.discover(request);
        assertThat(result.status()).isEqualTo(InventoryStatus.PARTIAL);
        assertThat(result.inventory().dependencies()).containsExactly(dependency);
        assertThat(result.message()).contains("source manifests", "versions may be unspecified");
    }

    private RepositoryScanRequest request() {
        return new RepositoryScanRequest(UUID.randomUUID().toString(), "github", "aravdash", "RoboFleet",
                "https://github.com/aravdash/RoboFleet", Instant.parse("2026-09-20T12:00:00Z"));
    }
}
