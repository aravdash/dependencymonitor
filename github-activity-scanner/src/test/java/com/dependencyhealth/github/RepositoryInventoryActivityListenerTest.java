package com.dependencyhealth.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dependencyhealth.contract.kafka.EventPublisher;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RepositoryInventoryActivityListenerTest {
    @Test
    void scansTheSubmittedRepositoryAndCarriesItsRequestId() {
        var codec = new RepositoryMessageCodec(new ObjectMapper());
        var scanner = mock(GithubScanScheduler.class);
        var listener = new RepositoryInventoryActivityListener(codec, scanner, mock(EventPublisher.class),
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        listener.receive(codec.write(inventory()));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(scanner).scanRepository(org.mockito.ArgumentMatchers.eq("aravdash/robofleet"),
                org.mockito.ArgumentMatchers.eq("github"), org.mockito.ArgumentMatchers.eq("aravdash/robofleet"),
                context.capture());
        assertThat(context.getValue()).containsEntry("repositoryRequestId", inventory().requestId());
    }

    private RepositoryInventoryEvent inventory() {
        return new RepositoryInventoryEvent("123e4567-e89b-12d3-a456-426614174000",
                "github:aravdash/robofleet", "https://github.com/aravdash/RoboFleet", InventoryStatus.PARTIAL,
                List.of(), 0, "Discovered", Instant.parse("2026-09-20T12:00:00Z"));
    }
}
