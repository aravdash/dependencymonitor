package com.dependencyhealth.github;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.Severity;
import com.dependencyhealth.contract.kafka.EventPublisher;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class RepositoryInventoryActivityListener {
    private static final Logger log = LoggerFactory.getLogger(RepositoryInventoryActivityListener.class);
    private final RepositoryMessageCodec codec;
    private final GithubScanScheduler scanner;
    private final EventPublisher publisher;
    private final Clock clock;

    RepositoryInventoryActivityListener(RepositoryMessageCodec codec, GithubScanScheduler scanner,
            EventPublisher publisher, Clock clock) {
        this.codec = codec;
        this.scanner = scanner;
        this.publisher = publisher;
        this.clock = clock;
    }

    @KafkaListener(topics = EventTopics.REPOSITORY_INVENTORY,
            groupId = "${repository.consumer-group:activity-repository-insights}")
    void receive(String json) {
        var inventory = codec.readInventory(json);
        if (inventory.status() == InventoryStatus.FAILED) return;
        String repository = inventory.repositoryId().substring("github:".length());
        var context = new LinkedHashMap<String, Object>();
        context.put("repositoryRequestId", inventory.requestId());
        context.put("repositoryId", inventory.repositoryId());
        context.put("repositoryUrl", inventory.repositoryUrl());
        context.put("repositoryScan", true);
        try {
            scanner.scanRepository(repository, "github", repository, context);
        } catch (RuntimeException ex) {
            log.warn("Repository activity insight failed for {}: {}", inventory.repositoryId(), ex.getMessage());
            context.put("scanUnavailable", true);
            context.put("reason", "GitHub API request failed or was rate limited");
            publisher.publish(new DependencyEvent(UUID.randomUUID().toString(), "github-activity", repository,
                    "github", Severity.WARNING, "Repository activity analysis is temporarily unavailable",
                    context, clock.instant()));
        }
    }
}
