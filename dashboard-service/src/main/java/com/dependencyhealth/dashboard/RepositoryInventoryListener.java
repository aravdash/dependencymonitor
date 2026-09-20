package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RepositoryInventoryListener {
    private final RepositoryMessageCodec codec;
    private final RepositoryScanRepository repository;
    public RepositoryInventoryListener(RepositoryMessageCodec codec, RepositoryScanRepository repository) {
        this.codec = codec; this.repository = repository;
    }
    @KafkaListener(topics = EventTopics.REPOSITORY_INVENTORY)
    public void receive(String json) { repository.apply(codec.readInventory(json)); }
}
