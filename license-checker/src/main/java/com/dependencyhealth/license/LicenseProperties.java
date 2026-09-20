package com.dependencyhealth.license;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scanner.license")
public record LicenseProperties(List<String> disallowed, URI npmRegistry, URI pypiRegistry, URI mavenRepository, int maxParentDepth) {
    public LicenseProperties {
        disallowed = disallowed == null ? List.of() : List.copyOf(disallowed);
        if (npmRegistry == null || pypiRegistry == null || mavenRepository == null || maxParentDepth < 1) {
            throw new IllegalArgumentException("License registries and positive max-parent-depth are required");
        }
    }
}
