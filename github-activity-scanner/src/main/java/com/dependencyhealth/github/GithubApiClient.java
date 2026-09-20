package com.dependencyhealth.github;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class GithubApiClient {
    private static final int PAGE_SIZE = 100;
    private final ExternalApiClient http;
    private final GithubScannerProperties properties;

    public GithubApiClient(ExternalApiClient http, GithubScannerProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public ActivitySnapshot fetch(String repository, Instant now) {
        String endpoint = properties.apiBaseUrl().replaceAll("/+$", "") + "/repos/" + repository + "/commits";
        JsonNode latest = getArray(URI.create(endpoint + "?per_page=1"));
        if (latest.isEmpty()) {
            return new ActivitySnapshot(null, 0, 0, true);
        }
        Instant lastCommit = commitTime(latest.get(0));
        Instant inactiveCutoff = now.atZone(ZoneOffset.UTC).minusMonths(properties.inactiveMonths()).toInstant();
        if (lastCommit.isBefore(inactiveCutoff)) {
            return new ActivitySnapshot(lastCommit, 0, 0, true);
        }
        Instant recentStart = now.minus(properties.recentWindowDays(), ChronoUnit.DAYS);
        Instant baselineStart = recentStart.minus(properties.baselineWindowDays(), ChronoUnit.DAYS);
        String query = "?since=" + encode(baselineStart) + "&until=" + encode(now) + "&per_page=" + PAGE_SIZE;
        Set<String> observedShas = new HashSet<>();
        int recent = 0;
        int baseline = 0;
        boolean complete = false;
        for (int page = 1; page <= properties.maxCommitPages(); page++) {
            JsonNode commits = getArray(URI.create(endpoint + query + "&page=" + page));
            for (JsonNode commit : commits) {
                String sha = commit.path("sha").asText();
                if (sha.isBlank()) {
                    throw new IllegalStateException("GitHub commit response omitted sha");
                }
                if (!observedShas.add(sha)) {
                    continue;
                }
                Instant time = commitTime(commit);
                if (time.isAfter(now) || time.isBefore(baselineStart)) {
                    continue;
                }
                if (!time.isBefore(recentStart)) {
                    recent++;
                } else {
                    baseline++;
                }
            }
            if (commits.size() < PAGE_SIZE) {
                complete = true;
                break;
            }
        }
        return new ActivitySnapshot(lastCommit, recent, baseline, complete);
    }

    private JsonNode getArray(URI uri) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        if (!properties.token().isBlank()) {
            headers.put("Authorization", "Bearer " + properties.token());
        }
        JsonNode response = http.getJson(uri, headers);
        if (!response.isArray()) {
            throw new IllegalStateException("GitHub returned an unexpected commits response");
        }
        return response;
    }

    private Instant commitTime(JsonNode commit) {
        String date = commit.path("commit").path("committer").path("date").asText();
        if (date.isBlank()) {
            date = commit.path("commit").path("author").path("date").asText();
        }
        if (date.isBlank()) {
            throw new IllegalStateException("GitHub commit response omitted its timestamp");
        }
        return Instant.parse(date);
    }

    private String encode(Instant instant) {
        return URLEncoder.encode(instant.toString(), StandardCharsets.UTF_8);
    }
}
