package com.dependencyhealth.license;

import java.util.List;

public record LicenseMetadata(List<String> licenses, String version, String metadataUrl, String provenance) {
    public LicenseMetadata {
        licenses = List.copyOf(licenses);
    }
}
