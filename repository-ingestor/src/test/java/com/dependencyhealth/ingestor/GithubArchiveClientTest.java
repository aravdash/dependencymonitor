package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class GithubArchiveClientTest {
    @Test
    void downloadsOnlySupportedManifestsWithoutSendingAuthentication() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    assertThat(request.uri()).isEqualTo(URI.create("https://codeload.github.com/aravdash/RoboFleet/zip/HEAD"));
                    assertThat(request.headers().firstValue("Authorization")).isEmpty();
                    return response(zip(Map.of(
                            "RoboFleet-HEAD/requirements.txt", "fastapi\n",
                            "RoboFleet-HEAD/README.md", "documentation",
                            "RoboFleet-HEAD/node_modules/package.json", "{\"dependencies\":{}}")));
                });

        var client = new GithubArchiveClient(properties(), http);
        assertThat(client.fetch(request())).containsExactly(
                new RepositoryManifest("RoboFleet-HEAD/requirements.txt", "fastapi\n"));
    }

    private byte[] zip(Map<String, String> files) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private HttpResponse<InputStream> response(byte[] body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return 200; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
            @Override public InputStream body() { return new ByteArrayInputStream(body); }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("https://codeload.github.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    private RepositoryScanRequest request() {
        return new RepositoryScanRequest(UUID.randomUUID().toString(), "github", "aravdash", "RoboFleet",
                "https://github.com/aravdash/RoboFleet", Instant.parse("2026-09-20T12:00:00Z"));
    }

    private GithubSbomProperties properties() {
        return new GithubSbomProperties(URI.create("https://api.github.test"), "2026-03-10", "secret",
                Duration.ofSeconds(2), Duration.ofMillis(1), 3, 1_000_000, 100);
    }
}
