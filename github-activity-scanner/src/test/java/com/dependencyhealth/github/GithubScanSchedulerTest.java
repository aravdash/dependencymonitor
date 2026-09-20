package com.dependencyhealth.github;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.http.ExternalApiException;
import com.dependencyhealth.contract.kafka.EventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubScanSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private final GithubApiClient client = mock(GithubApiClient.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final GithubScannerProperties properties = TestSettings.properties();
    private final GithubWatchlist watchlist = new GithubWatchlist(List.of(
            new GithubWatchlist.WatchedPackage("first", "npm", "owner/first"),
            new GithubWatchlist.WatchedPackage("second", "pypi", "owner/second")));

    @Test
    void failingRepositoryDoesNotStopOtherPackagesAndGetsAPollCooldown() {
        when(client.fetch(eq("owner/first"), any())).thenThrow(new IllegalStateException("temporary API outage"));
        when(client.fetch(eq("owner/second"), any())).thenReturn(new ActivitySnapshot(NOW, 3, 20, true));
        GithubScanScheduler scheduler = scheduler(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatCode(scheduler::scan).doesNotThrowAnyException();
        scheduler.scan();

        verify(client, times(1)).fetch(eq("owner/first"), any());
        ArgumentCaptor<DependencyEvent> events = ArgumentCaptor.forClass(DependencyEvent.class);
        verify(publisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.packageName()).isEqualTo("second");
            assertThat(event.source()).isEqualTo("github-activity");
            assertThat(event.timestamp()).isEqualTo(NOW);
        });
    }

    @Test
    void rateLimitPausesAllRepositoriesUntilServerReset() {
        ExternalApiException rateLimit = mock(ExternalApiException.class);
        when(rateLimit.statusCode()).thenReturn(403);
        when(rateLimit.retryAfter()).thenReturn(Duration.ofHours(1));
        when(client.fetch(eq("owner/first"), any())).thenThrow(rateLimit);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        GithubScanScheduler scheduler = scheduler(clock);

        scheduler.scan();
        when(clock.instant()).thenReturn(NOW.plusSeconds(120));
        scheduler.scan();

        verify(client, times(1)).fetch(eq("owner/first"), any());
        verify(client, never()).fetch(eq("owner/second"), any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void kafkaPublishFailureDoesNotEscapeTheScheduledTask() {
        when(client.fetch(any(), any())).thenReturn(new ActivitySnapshot(NOW, 3, 20, true));
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(any());

        assertThatCode(scheduler(Clock.fixed(NOW, ZoneOffset.UTC))::scan).doesNotThrowAnyException();

        verify(client).fetch(eq("owner/second"), any());
    }

    private GithubScanScheduler scheduler(Clock clock) {
        return new GithubScanScheduler(client, new ActivityClassifier(properties), publisher, watchlist, properties, clock);
    }
}
