package com.dependencyhealth.dashboard;

public record RepositoryDependencyView(String packageName, String ecosystem, String version, String packageUrl,
                                       boolean direct, String declaredLicense) { }
