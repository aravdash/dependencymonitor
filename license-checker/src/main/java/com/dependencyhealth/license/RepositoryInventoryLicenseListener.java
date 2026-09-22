package com.dependencyhealth.license;

import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import java.util.LinkedHashMap;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class RepositoryInventoryLicenseListener {
    private final RepositoryMessageCodec codec;
    private final LicenseScanJob scanJob;

    RepositoryInventoryLicenseListener(RepositoryMessageCodec codec, LicenseScanJob scanJob) {
        this.codec = codec;
        this.scanJob = scanJob;
    }

    @KafkaListener(topics = EventTopics.REPOSITORY_INVENTORY,
            groupId = "${repository.consumer-group:license-repository-insights}")
    void receive(String json) {
        var inventory = codec.readInventory(json);
        if (inventory.status() == InventoryStatus.FAILED) return;
        for (var dependency : inventory.dependencies()) {
            boolean unspecified = dependency.version().equals("unspecified");
            String registryVersion = unspecified && !dependency.ecosystem().equals("maven")
                    ? "" : dependency.version();
            var context = new LinkedHashMap<String, Object>();
            context.put("repositoryRequestId", inventory.requestId());
            context.put("repositoryId", inventory.repositoryId());
            context.put("repositoryUrl", inventory.repositoryUrl());
            context.put("repositoryScan", true);
            context.put("discoveredVersion", dependency.version());
            scanJob.scanPackage(new WatchlistProperties.WatchedPackage(dependency.packageName(),
                    dependency.ecosystem(), registryVersion), context);
        }
    }
}
