package com.dependencyhealth.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

@ConfigurationProperties("watchlist")
public record GithubWatchlist(List<WatchedPackage> packages) {
    public GithubWatchlist {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    public record WatchedPackage(String name, String ecosystem, String repository) {
        public WatchedPackage {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Every watched package requires a name");
            }
            if (ecosystem == null || !Set.of("npm", "pypi", "maven").contains(ecosystem)) {
                throw new IllegalArgumentException("Package ecosystem must be npm, pypi, or maven");
            }
            if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Repository must use owner/repository format for " + name);
            }
        }
    }
}
