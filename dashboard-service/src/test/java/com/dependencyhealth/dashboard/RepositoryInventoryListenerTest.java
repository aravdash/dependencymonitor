package com.dependencyhealth.dashboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.dependencyhealth.contract.repository.InventoryStatus;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryInventoryListenerTest {
    @Test
    void decodesAndPersistsAnInventoryMessage() {
        var codec = new RepositoryMessageCodec(new ObjectMapper());
        var repository = mock(RepositoryScanRepository.class);
        var listener = new RepositoryInventoryListener(codec, repository);
        var event = new RepositoryInventoryEvent("123e4567-e89b-12d3-a456-426614174000",
                "github:acme/app", "https://github.com/acme/app", InventoryStatus.COMPLETE,
                List.of(new DiscoveredDependency("requests", "pypi", "2.32.3",
                        "pkg:pypi/requests@2.32.3", true, "Apache-2.0")),
                0, "Discovered 1 supported dependency", Instant.parse("2026-09-20T12:00:05Z"));
        listener.receive(codec.write(event));
        verify(repository).apply(event);
    }
}
