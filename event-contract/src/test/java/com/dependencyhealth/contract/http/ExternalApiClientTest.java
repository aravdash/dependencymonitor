package com.dependencyhealth.contract.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalApiClientTest {
    @Test void repositoryPermissionFailureDoesNotTriggerGlobalRateLimitCooldown() throws Exception {
        var client = mock(HttpClient.class);
        var response = response(403, Map.of("x-ratelimit-remaining", List.of("49"),
                "x-ratelimit-reset", List.of(Long.toString(Instant.now().plusSeconds(3600).getEpochSecond()))));
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var api = new ExternalApiClient(client, new ObjectMapper(), Duration.ofSeconds(2), 3);
        assertThatThrownBy(() -> api.getJson(URI.create("https://api.github.com/repos/example/private")))
                .isInstanceOfSatisfying(ExternalApiException.class, ex -> {
                    assertThat(ex.statusCode()).isEqualTo(403);
                    assertThat(ex.retryAfter()).isEqualTo(Duration.ZERO);
                });
        verify(client, times(1)).send(any(HttpRequest.class), any());
    }
    @Test void longRetryAfterIsReturnedToSchedulerWithoutSendingRequestsTooEarly() throws Exception {
        var client = mock(HttpClient.class);
        var tooManyRequests = response(429, Map.of("retry-after", List.of("120")));
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(tooManyRequests);
        var api = new ExternalApiClient(client, new ObjectMapper(), Duration.ofSeconds(2), 3);
        assertThatThrownBy(() -> api.getJson(URI.create("https://example.org/api")))
                .isInstanceOfSatisfying(ExternalApiException.class, ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(120)));
        verify(client, times(1)).send(any(HttpRequest.class), any());
    }
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (key, value) -> true));
        return response;
    }
}
