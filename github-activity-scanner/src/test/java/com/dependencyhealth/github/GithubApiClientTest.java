package com.dependencyhealth.github;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubApiClientTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExternalApiClient http = mock(ExternalApiClient.class);

    @Test
    void countsDistinctCommitsWithinExplicitRecentAndBaselineWindows() {
        ArrayNode latest = mapper.createArrayNode().add(commit("recent", NOW));
        ArrayNode history = mapper.createArrayNode()
                .add(commit("recent", NOW))
                .add(commit("boundary", NOW.minus(7, ChronoUnit.DAYS)))
                .add(commit("baseline", NOW.minus(8, ChronoUnit.DAYS)))
                .add(commit("baseline", NOW.minus(8, ChronoUnit.DAYS)))
                .add(commit("too-old", NOW.minus(36, ChronoUnit.DAYS)))
                .add(commit("future", NOW.plusSeconds(1)));
        when(http.getJson(any(URI.class), anyMap())).thenReturn(latest, history);

        ActivitySnapshot snapshot = new GithubApiClient(http, TestSettings.properties()).fetch("owner/repo", NOW);

        assertThat(snapshot.recentCommits()).isEqualTo(2);
        assertThat(snapshot.baselineCommits()).isEqualTo(1);
        assertThat(snapshot.historyComplete()).isTrue();
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(http, times(2)).getJson(uri.capture(), anyMap());
        assertThat(uri.getAllValues().get(1).getQuery())
                .contains("since=2026-08-08T12:00:00Z", "until=2026-09-12T12:00:00Z", "page=1");
    }

    @Test
    void marksHistoryIncompleteWhenPaginationCapIsReached() {
        ArrayNode fullPage = mapper.createArrayNode();
        for (int i = 0; i < 100; i++) {
            fullPage.add(commit("commit-" + i, NOW));
        }
        when(http.getJson(any(URI.class), anyMap()))
                .thenReturn(mapper.createArrayNode().add(commit("head", NOW)), fullPage);

        ActivitySnapshot snapshot = new GithubApiClient(http, TestSettings.properties(1, "")).fetch("owner/repo", NOW);

        assertThat(snapshot.historyComplete()).isFalse();
        verify(http, times(2)).getJson(any(URI.class), anyMap());
    }

    @Test
    void avoidsHistoryRequestsForInactiveRepositoriesAndUsesOptionalToken() {
        Instant oldCommit = NOW.minus(500, ChronoUnit.DAYS);
        when(http.getJson(any(URI.class), anyMap()))
                .thenReturn(mapper.createArrayNode().add(commit("old", oldCommit)));

        ActivitySnapshot snapshot = new GithubApiClient(http, TestSettings.properties(3, "test-token"))
                .fetch("owner/repo", NOW);

        assertThat(snapshot.lastCommit()).isEqualTo(oldCommit);
        verify(http).getJson(URI.create("https://api.github.com/repos/owner/repo/commits?per_page=1"), Map.of(
                "Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28", "Authorization", "Bearer test-token"));
    }

    private ObjectNode commit(String sha, Instant time) {
        ObjectNode node = mapper.createObjectNode().put("sha", sha);
        node.putObject("commit").putObject("committer").put("date", time.toString());
        return node;
    }
}
