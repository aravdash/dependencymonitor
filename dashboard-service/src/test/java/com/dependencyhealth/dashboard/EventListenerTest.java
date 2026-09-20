package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.InvalidEventException;
import com.dependencyhealth.contract.Severity;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EventListenerTest {
    private final DependencyEventCodec codec = new DependencyEventCodec();
    private final EventRepository repository = mock(EventRepository.class);
    private final EventListener listener = new EventListener(codec, repository);

    @Test
    void persistsOriginalEventWithoutReplacingItsTimestampOrDetail() {
        DependencyEvent event = event();
        when(repository.save(event)).thenReturn(true);
        listener.onEvent(codec.write(event));
        verify(repository).save(event);
    }

    @Test
    void databaseFailuresPropagateToKafkaSoTheEventCanBeRetried() {
        DependencyEvent event = event();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.save(event)).thenThrow(failure);
        assertThatThrownBy(() -> listener.onEvent(codec.write(event))).isSameAs(failure);
    }

    @Test
    void duplicateDatabaseInsertIsSuccessfulConsumption() {
        DependencyEvent event = event();
        when(repository.save(event)).thenReturn(false);
        listener.onEvent(codec.write(event));
        verify(repository).save(event);
    }

    @Test
    void malformedEventsNeverReachPersistence() {
        assertThatThrownBy(() -> listener.onEvent("{\"severity\":\"unexpected\"}"))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(repository);
    }

    private static DependencyEvent event() {
        return new DependencyEvent("123e4567-e89b-12d3-a456-426614174000", "osv-cve", "lodash", "npm",
                Severity.CRITICAL, "Known vulnerability", Map.of("vulnerabilityId", "GHSA-example"),
                Instant.parse("2025-01-01T10:00:00Z"));
    }
}
