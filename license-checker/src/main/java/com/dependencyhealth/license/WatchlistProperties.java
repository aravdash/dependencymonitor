package com.dependencyhealth.license;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("watchlist")
public record WatchlistProperties(List<WatchedPackage> packages) {
    public WatchlistProperties {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    public record WatchedPackage(String name, String ecosystem, String version) {
        public WatchedPackage {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Package name is required");
            ecosystem = ecosystem == null ? "" : ecosystem.toLowerCase(Locale.ROOT);
            if (!List.of("npm", "pypi", "maven").contains(ecosystem)) {
                throw new IllegalArgumentException("Unsupported license ecosystem: " + ecosystem);
            }
            version = version == null ? "" : version;
            if (ecosystem.equals("maven") && version.isBlank()) {
                throw new IllegalArgumentException("Maven packages require an explicit version");
            }
        }
    }
}
