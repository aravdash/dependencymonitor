package com.dependencyhealth.ingestor;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ingestor.github")
public record GithubSbomProperties(URI apiBaseUrl, String apiVersion, String token, Duration requestTimeout,
                                   Duration reportPollInterval, int reportMaxPolls, int maxResponseBytes,
                                   int maxDependencies) {
    public GithubSbomProperties {
        token = token == null ? "" : token.trim();
        if (apiBaseUrl == null || !"https".equalsIgnoreCase(apiBaseUrl.getScheme()) || apiBaseUrl.getHost() == null)
            throw new IllegalArgumentException("GitHub API base URL must be HTTPS");
        if (apiVersion == null || apiVersion.isBlank() || requestTimeout == null || requestTimeout.isNegative()
                || requestTimeout.isZero() || reportPollInterval == null || reportPollInterval.isNegative()
                || reportPollInterval.isZero() || reportMaxPolls < 1 || reportMaxPolls > 300
                || maxResponseBytes < 1024 || maxResponseBytes > 100_000_000
                || maxDependencies < 1 || maxDependencies > 100_000)
            throw new IllegalArgumentException("Invalid GitHub SBOM configuration");
    }
}
