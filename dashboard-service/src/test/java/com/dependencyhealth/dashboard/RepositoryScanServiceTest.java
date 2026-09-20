package com.dependencyhealth.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dependencyhealth.contract.kafka.RepositoryMessagePublisher;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RepositoryScanServiceTest {
    private final RepositoryScanRepository repository = mock(RepositoryScanRepository.class);
    private final RepositoryMessagePublisher publisher = mock(RepositoryMessagePublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
    private final RepositoryScanService service = new RepositoryScanService(repository, publisher, clock);

    @Test
    void canonicalizesAndQueuesARepositoryScan() {
        when(repository.create(any())).thenAnswer(invocation -> {
            RepositoryScanRequest request = invocation.getArgument(0);
            return view(request, "queued");
        });
        RepositoryScanView result = service.submit("Google/guava.git");
        ArgumentCaptor<RepositoryScanRequest> request = ArgumentCaptor.forClass(RepositoryScanRequest.class);
        verify(publisher).publishRequest(request.capture());
        assertThat(request.getValue().repositoryUrl()).isEqualTo("https://github.com/Google/guava");
        assertThat(request.getValue().requestedAt()).isEqualTo(clock.instant());
        assertThat(result.repositoryId()).isEqualTo("github:google/guava");
    }

    @Test
    void marksTheVisibleScanFailedIfKafkaPublicationFails() {
        when(repository.create(any())).thenAnswer(invocation -> view(invocation.getArgument(0), "queued"));
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publishRequest(any());
        assertThatThrownBy(() -> service.submit("https://github.com/acme/app"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Kafka");
        ArgumentCaptor<RepositoryScanRequest> request = ArgumentCaptor.forClass(RepositoryScanRequest.class);
        verify(repository).create(request.capture());
        verify(repository).markPublicationFailed(request.getValue().requestId());
    }

    @Test
    void invalidUrlsNeverReachPersistenceOrKafka() {
        assertThatThrownBy(() -> service.submit("https://example.com/acme/app"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).create(any());
        verify(publisher, never()).publishRequest(any());
    }

    private RepositoryScanView view(RepositoryScanRequest request, String status) {
        return new RepositoryScanView(request.requestId(), request.repositoryId(), request.repositoryUrl(), status,
                0, 0, "Waiting for repository discovery", request.requestedAt(), null);
    }
}
