package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GithubSbomClient {
    private static final Logger log = LoggerFactory.getLogger(GithubSbomClient.class);
    private final GithubSbomProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public GithubSbomClient(GithubSbomProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
    }
    GithubSbomClient(GithubSbomProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties; this.mapper = mapper; this.http = http;
    }
    @PostConstruct void describeAuthentication() {
        if (properties.token().isBlank())
            log.warn("GITHUB_TOKEN is unset; repository discovery is limited to public repositories and anonymous rate limits");
    }

    public JsonNode fetch(RepositoryScanRequest request) {
        URI generate = api("/repos/" + request.owner() + "/" + request.repository()
                + "/dependency-graph/sbom/generate-report");
        Response generated = send(generate, githubHeaders(), 3);
        if (generated.status() != 201) throw statusFailure(generated.status());
        URI reportUri = requiredReportUri(generated.body(), request);
        for (int poll = 1; poll <= properties.reportMaxPolls(); poll++) {
            Response report = send(reportUri, githubHeaders(), 3);
            if (report.status() == 201 || report.status() == 202) {
                pause(properties.reportPollInterval());
                continue;
            }
            if (report.status() == 200) return parse(report.body());
            if (report.status() == 302 || report.status() == 303 || report.status() == 307) {
                URI download = safeDownloadUri(report.location());
                Response document = send(download, Map.of("Accept", "application/json"), 3);
                if (document.status() != 200) throw new GithubApiException("GitHub's SBOM download failed", document.status());
                return parse(document.body());
            }
            throw statusFailure(report.status());
        }
        throw new GithubApiException("GitHub did not finish generating the dependency report in time", 202);
    }

    private Response send(URI uri, Map<String, String> headers, int attempts) {
        GithubApiException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var builder = HttpRequest.newBuilder(uri).timeout(properties.requestTimeout()).GET()
                        .header("User-Agent", "dependency-health-monitor/1.0");
                headers.forEach(builder::header);
                var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                String body = boundedBody(response.body());
                int status = response.statusCode();
                if (status < 400 || (status >= 400 && status < 500 && status != 408 && status != 429))
                    return new Response(status, body, response.headers().firstValue("location").orElse(""));
                last = new GithubApiException("GitHub API temporarily unavailable", status);
            } catch (IOException ex) {
                last = new GithubApiException("Cannot reach GitHub", 0);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new GithubApiException("GitHub request interrupted", 0);
            }
            if (attempt < attempts) pause(Duration.ofMillis(500L << (attempt - 1)));
        }
        throw last;
    }

    private String boundedBody(InputStream stream) throws IOException {
        try (stream) {
            byte[] bytes = stream.readNBytes(properties.maxResponseBytes() + 1);
            if (bytes.length > properties.maxResponseBytes()) throw new IOException("GitHub response exceeds configured limit");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private URI requiredReportUri(String body, RepositoryScanRequest request) {
        String value = parse(body).path("sbom_url").asText("");
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException ex) { throw new GithubApiException("GitHub omitted the SBOM report URL", 0); }
        String expectedPath = "/repos/" + request.owner() + "/" + request.repository() + "/dependency-graph/sbom/fetch-report/";
        if (!sameAuthority(properties.apiBaseUrl(), uri) || !uri.getPath().startsWith(expectedPath))
            throw new GithubApiException("GitHub returned an unexpected SBOM report URL", 0);
        return uri;
    }
    private URI safeDownloadUri(String location) {
        URI uri;
        try { uri = URI.create(location); } catch (RuntimeException ex) { throw new GithubApiException("GitHub omitted the SBOM download URL", 0); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new GithubApiException("GitHub returned an unsafe SBOM download URL", 0);
        return uri;
    }
    private boolean sameAuthority(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }
    private int effectivePort(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : 443; }
    private URI api(String path) { return URI.create(properties.apiBaseUrl().toString().replaceAll("/+$", "") + path); }
    private Map<String, String> githubHeaders() {
        var headers = new java.util.LinkedHashMap<String, String>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", properties.apiVersion());
        if (!properties.token().isBlank()) headers.put("Authorization", "Bearer " + properties.token());
        return headers;
    }
    private JsonNode parse(String body) {
        try { return mapper.readTree(body); }
        catch (IOException ex) { throw new GithubApiException("GitHub returned invalid JSON", 0); }
    }
    private GithubApiException statusFailure(int status) {
        return switch (status) {
            case 401, 403 -> new GithubApiException("Repository access was denied; private repositories require a GitHub token", status);
            case 404 -> new GithubApiException("Repository or dependency graph was not found", status);
            default -> new GithubApiException("GitHub dependency discovery failed", status);
        };
    }
    private void pause(Duration duration) {
        try { Thread.sleep(duration.toMillis()); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new GithubApiException("GitHub polling interrupted", 0); }
    }
    private record Response(int status, String body, String location) { }
}
