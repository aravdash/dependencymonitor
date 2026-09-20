package com.dependencyhealth.contract.repository;

import java.util.List;

public record DiscoveredDependency(String packageName, String ecosystem, String version, String packageUrl,
                                   boolean direct, String declaredLicense) {
    public DiscoveredDependency {
        if (packageName == null || packageName.isBlank() || packageName.length() > 512)
            throw new IllegalArgumentException("packageName is required");
        if (!List.of("npm", "pypi", "maven").contains(ecosystem))
            throw new IllegalArgumentException("Unsupported ecosystem");
        if (version == null || version.isBlank() || version.length() > 256)
            throw new IllegalArgumentException("version is required");
        if (packageUrl == null || !packageUrl.startsWith("pkg:") || packageUrl.length() > 2048)
            throw new IllegalArgumentException("packageUrl is invalid");
        declaredLicense = declaredLicense == null || declaredLicense.isBlank() ? "unknown" : declaredLicense;
        if (declaredLicense.length() > 1024) declaredLicense = declaredLicense.substring(0, 1024);
    }
}
