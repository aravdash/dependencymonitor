package com.dependencyhealth.contract.kafka;

import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

public final class RepositoryMessagePublisher {
    private final KafkaTemplate<String, String> template;
    private final RepositoryMessageCodec codec;
    private final Duration timeout;
    public RepositoryMessagePublisher(KafkaTemplate<String, String> template, RepositoryMessageCodec codec, Duration timeout) {
        this.template = template; this.codec = codec; this.timeout = timeout;
    }
    public void publishRequest(RepositoryScanRequest request) {
        publish(EventTopics.REPOSITORY_SCAN_REQUESTS, request.repositoryId(), codec.write(request));
    }
    public void publishInventory(RepositoryInventoryEvent inventory) {
        publish(EventTopics.REPOSITORY_INVENTORY, inventory.repositoryId(), codec.write(inventory));
    }
    private void publish(String topic, String key, String payload) {
        try { template.send(topic, key, payload).get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Kafka did not acknowledge repository message", ex);
        }
    }
}
