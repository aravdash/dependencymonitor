package com.dependencyhealth.ingestor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.dependencyhealth.contract.kafka.RepositoryMessagePublisher;
import com.dependencyhealth.contract.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepositoryIngestionListenerTest {
    private final RepositoryMessageCodec codec = new RepositoryMessageCodec(new ObjectMapper());
    private final RepositoryDependencyDiscoverer discoverer = mock(RepositoryDependencyDiscoverer.class);
    private final RepositoryMessagePublisher publisher = mock(RepositoryMessagePublisher.class);
    private final RepositoryIngestionListener listener = new RepositoryIngestionListener(codec, discoverer, publisher);

    @Test void publishesACompleteInventory() {
        var request = request();
        var dependency = new DiscoveredDependency("lodash", "npm", "4.17.21", "pkg:npm/lodash@4.17.21", true, "MIT");
        var inventory = new SpdxInventoryParser.Inventory(List.of(dependency), 0, 1);
        when(discoverer.discover(request)).thenReturn(new RepositoryDependencyDiscoverer.Discovery(
                InventoryStatus.COMPLETE, inventory, "1 dependency discovered"));
        listener.ingest(codec.write(request));
        verify(publisher).publishInventory(argThat(event -> event.status() == InventoryStatus.COMPLETE
                && event.dependencies().equals(List.of(dependency)) && event.requestId().equals(request.requestId())));
    }
    @Test void convertsDiscoveryFailuresIntoAVisibleFailedResult() {
        var request = request();
        when(discoverer.discover(request)).thenThrow(new GithubApiException("Repository source was not found", 404));
        listener.ingest(codec.write(request));
        verify(publisher).publishInventory(argThat(event -> event.status() == InventoryStatus.FAILED
                && event.message().contains("not found") && event.dependencies().isEmpty()));
    }
    @Test void letsKafkaRetryWhenPublishingTheResultFails() {
        var request = request();
        when(discoverer.discover(request)).thenThrow(new GithubApiException("Cannot reach GitHub", 0));
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publishInventory(any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.ingest(codec.write(request)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Kafka unavailable");
    }
    private RepositoryScanRequest request() {
        return new RepositoryScanRequest(UUID.randomUUID().toString(), "github", "Google", "guava",
                "https://github.com/Google/guava", Instant.parse("2026-09-20T12:00:00Z"));
    }
}
