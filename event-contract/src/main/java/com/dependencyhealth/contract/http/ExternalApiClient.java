package com.dependencyhealth.contract.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded retries for read-only public API queries, including OSV's query POST. */
public class ExternalApiClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalApiClient.class);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final int attempts;

    public ExternalApiClient(HttpClient client, ObjectMapper mapper, Duration timeout, int attempts) {
        if (attempts < 1 || attempts > 10) throw new IllegalArgumentException("HTTP attempts must be 1..10");
        this.client = client; this.mapper = mapper; this.timeout = timeout; this.attempts = attempts;
    }
    public JsonNode getJson(URI uri) { return getJson(uri, Map.of()); }
    public JsonNode getJson(URI uri, Map<String, String> headers) {
        return parse(request(uri, "GET", null, headers), uri);
    }
    public JsonNode postJson(URI uri, Object body) {
        try { return parse(request(uri, "POST", mapper.writeValueAsString(body), Map.of()), uri); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Cannot serialize API query", ex); }
    }
    public String getText(URI uri) { return request(uri, "GET", null, Map.of()); }

    private JsonNode parse(String body, URI uri) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null) throw new IOException("Empty JSON response");
            return node;
        } catch (IOException ex) { throw new ExternalApiException("Invalid JSON from " + uri.getHost(), 0, Duration.ZERO, ex); }
    }

    private String request(URI uri, String method, String body, Map<String, String> headers) {
        ExternalApiException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var builder = HttpRequest.newBuilder(uri).timeout(timeout)
                        .header("User-Agent", "dependency-health-monitor/1.0")
                        .header("Accept", "application/json");
                headers.forEach(builder::setHeader);
                if ("POST".equals(method)) builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                else builder.GET();
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return response.body();
                Duration retryAfter = retryAfter(response.headers());
                lastFailure = new ExternalApiException("HTTP " + status + " from " + uri.getHost(), status, retryAfter, null);
                boolean retryable = status == 429 || status == 408 || status >= 500 ||
                        (status == 403 && response.headers().firstValue("x-ratelimit-remaining").orElse("").equals("0"));
                if (!retryable) throw lastFailure;
                // Leave long server-requested cooldowns to the scheduler, rather than hammering an API.
                if (retryAfter.compareTo(Duration.ofSeconds(10)) > 0) throw lastFailure;
            } catch (IOException ex) {
                lastFailure = new ExternalApiException("Cannot reach " + uri.getHost(), 0, Duration.ZERO, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ExternalApiException("API request interrupted", 0, Duration.ZERO, ex);
            }
            if (attempt < attempts) {
                long delay = Math.max(500L * (1L << (attempt - 1)), lastFailure.retryAfter().toMillis());
                delay = Math.min(delay, 10_000L);
                log.warn("{}; retry {}/{} in {} ms", lastFailure.getMessage(), attempt + 1, attempts, delay);
                try { Thread.sleep(delay); }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ExternalApiException("API retry interrupted", 0, Duration.ZERO, ex);
                }
            }
        }
        throw lastFailure;
    }

    private Duration retryAfter(HttpHeaders headers) {
        Instant now = Instant.now();
        String value = headers.firstValue("retry-after").orElse("");
        try { return Duration.ofSeconds(Math.max(0, Long.parseLong(value))); }
        catch (NumberFormatException ignored) { }
        try { return positive(Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())); }
        catch (RuntimeException ignored) { }
        if (!headers.firstValue("x-ratelimit-remaining").orElse("").equals("0")) return Duration.ZERO;
        try { return positive(Duration.between(now, Instant.ofEpochSecond(Long.parseLong(headers.firstValue("x-ratelimit-reset").orElse(""))))); }
        catch (RuntimeException ignored) { return Duration.ZERO; }
    }
    private Duration positive(Duration duration) { return duration.isNegative() ? Duration.ZERO : duration; }
}
