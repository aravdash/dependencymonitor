package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.kafka.RepositoryMessagePublisher;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RepositoryIngestionListener {
    private static final Logger log = LoggerFactory.getLogger(RepositoryIngestionListener.class);
    private final RepositoryMessageCodec codec;
    private final RepositoryDependencyDiscoverer discoverer;
    private final RepositoryMessagePublisher publisher;
    public RepositoryIngestionListener(RepositoryMessageCodec codec, RepositoryDependencyDiscoverer discoverer,
            RepositoryMessagePublisher publisher) {
        this.codec = codec; this.discoverer = discoverer; this.publisher = publisher;
    }

    @KafkaListener(topics = EventTopics.REPOSITORY_SCAN_REQUESTS)
    public void ingest(String json) {
        RepositoryScanRequest request = codec.readRequest(json);
        RepositoryInventoryEvent result;
        try {
            var discovery = discoverer.discover(request);
            var inventory = discovery.inventory();
            result = new RepositoryInventoryEvent(request.requestId(), request.repositoryId(), request.repositoryUrl(),
                    discovery.status(), inventory.dependencies(), inventory.unsupportedCount(), discovery.message(), Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Repository discovery failed for {}: {}", request.repositoryId(), ex.getMessage());
            result = new RepositoryInventoryEvent(request.requestId(), request.repositoryId(), request.repositoryUrl(),
                    InventoryStatus.FAILED, List.of(), 0, safeMessage(ex), Instant.now());
        }
        // A publication failure escapes so Kafka retries the source request.
        publisher.publishInventory(result);
    }
    private String safeMessage(RuntimeException ex) {
        if (ex instanceof GithubApiException) return ex.getMessage();
        if (ex instanceof IllegalArgumentException) return ex.getMessage();
        return "Repository dependency discovery failed";
    }
}
