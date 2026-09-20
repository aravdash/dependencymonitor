package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class RepositoryDependencyDiscoverer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryDependencyDiscoverer.class);
    private final GithubSbomClient sbomClient;
    private final SpdxInventoryParser spdxParser;
    private final GithubArchiveClient archiveClient;
    private final ManifestInventoryParser manifestParser;

    RepositoryDependencyDiscoverer(GithubSbomClient sbomClient, SpdxInventoryParser spdxParser,
            GithubArchiveClient archiveClient, ManifestInventoryParser manifestParser) {
        this.sbomClient = sbomClient;
        this.spdxParser = spdxParser;
        this.archiveClient = archiveClient;
        this.manifestParser = manifestParser;
    }

    Discovery discover(RepositoryScanRequest request) {
        try {
            var inventory = spdxParser.parse(sbomClient.fetch(request));
            InventoryStatus status = inventory.unsupportedCount() == 0
                    ? InventoryStatus.COMPLETE : InventoryStatus.PARTIAL;
            String message = inventory.dependencies().size() + " supported dependencies discovered from GitHub's dependency graph";
            if (inventory.unsupportedCount() > 0)
                message += "; " + inventory.unsupportedCount() + " unsupported entries skipped";
            return new Discovery(status, inventory, message);
        } catch (RuntimeException sbomFailure) {
            log.info("GitHub dependency graph unavailable for {}; using source manifests: {}",
                    request.repositoryId(), sbomFailure.getMessage());
            try {
                var manifests = archiveClient.fetch(request);
                var inventory = manifestParser.parse(manifests);
                String message = inventory.dependencies().size() + " dependencies discovered from "
                        + manifests.size() + " source manifests; versions may be unspecified";
                return new Discovery(InventoryStatus.PARTIAL, inventory, message);
            } catch (RuntimeException archiveFailure) {
                log.warn("Source manifest fallback failed for {}: {}", request.repositoryId(), archiveFailure.getMessage());
                throw new GithubApiException(safeFailure(sbomFailure, archiveFailure), 0);
            }
        }
    }

    private String safeFailure(RuntimeException sbomFailure, RuntimeException archiveFailure) {
        if (archiveFailure instanceof GithubApiException || archiveFailure instanceof IllegalArgumentException)
            return archiveFailure.getMessage();
        if (sbomFailure instanceof GithubApiException || sbomFailure instanceof IllegalArgumentException)
            return sbomFailure.getMessage();
        return "Repository dependency discovery failed";
    }

    record Discovery(InventoryStatus status, SpdxInventoryParser.Inventory inventory, String message) { }
}
