package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GithubSbomClientTest {
    private final GithubSbomProperties properties = new GithubSbomProperties(
            URI.create("https://api.github.test"), "2026-03-10", "secret-token",
            Duration.ofSeconds(2), Duration.ofMillis(1), 3, 1_000_000, 100);

    @Test
    void followsAsyncReportWithoutLeakingTheGithubTokenToTheDownloadHost() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any()))
                .thenReturn(response(201, "{\"sbom_url\":\"https://api.github.test/repos/acme/app/dependency-graph/sbom/fetch-report/report-id\"}", Map.of()))
                .thenReturn(response(202, "", Map.of()))
                .thenReturn(response(302, "", Map.of("location", List.of("https://downloads.github.test/report.json"))))
                .thenReturn(response(200, "{\"sbom\":{\"packages\":[],\"relationships\":[]}}", Map.of()));

        var client = new GithubSbomClient(properties, new ObjectMapper(), http);
        assertThat(client.fetch(request()).path("sbom").path("packages").isArray()).isTrue();

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.times(4)).send(requests.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any());
        assertThat(requests.getAllValues().subList(0, 3))
                .allSatisfy(request -> assertThat(request.headers().firstValue("Authorization"))
                        .contains("Bearer secret-token"));
        HttpRequest download = requests.getAllValues().get(3);
        assertThat(download.uri().getHost()).isEqualTo("downloads.github.test");
        assertThat(download.headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void rejectsReportUrlsOutsideTheConfiguredGithubApi() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any()))
                .thenReturn(response(201, "{\"sbom_url\":\"https://attacker.example/report\"}", Map.of()));
        var client = new GithubSbomClient(properties, new ObjectMapper(), http);
        assertThatThrownBy(() -> client.fetch(request()))
                .isInstanceOf(GithubApiException.class).hasMessageContaining("unexpected");
    }

    private RepositoryScanRequest request() {
        return new RepositoryScanRequest(UUID.randomUUID().toString(), "github", "acme", "app",
                "https://github.com/acme/app", Instant.parse("2026-09-20T12:00:00Z"));
    }

    private HttpResponse<java.io.InputStream> response(int status, String body, Map<String, List<String>> headers) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public java.util.Optional<HttpResponse<java.io.InputStream>> previousResponse() { return java.util.Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(headers, (name, value) -> true); }
            @Override public java.io.InputStream body() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }
            @Override public java.util.Optional<SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override public URI uri() { return URI.create("https://api.github.test"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
