package com.dependencyhealth.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("scanner.github")
public record GithubScannerProperties(
        String apiBaseUrl,
        String token,
        int inactiveMonths,
        int recentWindowDays,
        int baselineWindowDays,
        int spikeMinimumCommits,
        double spikeMultiplier,
        int maxCommitPages,
        long failureBackoffMs,
        long maxFailureBackoffMs) {

    public GithubScannerProperties {
        if (apiBaseUrl == null || !URI.create(apiBaseUrl).isAbsolute()) {
            throw new IllegalArgumentException("scanner.github.api-base-url must be an absolute URL");
        }
        token = token == null ? "" : token.trim();
        if (inactiveMonths < 1 || recentWindowDays < 1 || baselineWindowDays < 1
                || spikeMinimumCommits < 1 || !Double.isFinite(spikeMultiplier) || spikeMultiplier <= 1
                || maxCommitPages < 1 || maxCommitPages > 100
                || failureBackoffMs < 1 || maxFailureBackoffMs < failureBackoffMs) {
            throw new IllegalArgumentException("Invalid GitHub activity thresholds, pagination limit, or failure backoff");
        }
    }
}
