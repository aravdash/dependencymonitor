package com.dependencyhealth.license;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegistryLicenseClient {
    private final ExternalApiClient http;
    private final LicenseProperties properties;
    private final PomMetadataParser pomParser;

    public RegistryLicenseClient(ExternalApiClient http, LicenseProperties properties, PomMetadataParser pomParser) {
        this.http = http;
        this.properties = properties;
        this.pomParser = pomParser;
    }

    public LicenseMetadata fetch(WatchlistProperties.WatchedPackage pkg) {
        return switch (pkg.ecosystem()) {
            case "npm" -> npm(pkg);
            case "pypi" -> pypi(pkg);
            case "maven" -> maven(pkg);
            default -> throw new IllegalArgumentException("Unsupported ecosystem " + pkg.ecosystem());
        };
    }

    private LicenseMetadata npm(WatchlistProperties.WatchedPackage pkg) {
        URI uri = append(properties.npmRegistry(), encode(pkg.name()) + "/" + encode(pkg.version().isBlank() ? "latest" : pkg.version()));
        JsonNode response = http.getJson(uri);
        if (!response.isObject() || !response.hasNonNull("name") || !response.hasNonNull("version")) {
            throw new IllegalStateException("Unexpected npm package metadata");
        }
        List<String> licenses = new ArrayList<>();
        addNpmLicense(licenses, response.path("license"));
        if (licenses.isEmpty()) for (JsonNode old : response.path("licenses")) addNpmLicense(licenses, old);
        return new LicenseMetadata(licenses, response.path("version").asText(), uri.toString(), "npm registry license metadata");
    }

    private void addNpmLicense(List<String> licenses, JsonNode node) {
        String value = node.isTextual() ? node.asText() : node.path("type").asText("");
        if (!value.isBlank()) licenses.add(value);
    }

    private LicenseMetadata pypi(WatchlistProperties.WatchedPackage pkg) {
        URI uri = append(properties.pypiRegistry(), encode(pkg.name()) + (pkg.version().isBlank() ? "" : "/" + encode(pkg.version())) + "/json");
        JsonNode info = http.getJson(uri).path("info");
        if (!info.isObject() || !info.hasNonNull("name") || !info.hasNonNull("version")) {
            throw new IllegalStateException("Unexpected PyPI package metadata");
        }
        List<String> licenses = new ArrayList<>();
        String expression = info.path("license_expression").asText("");
        if (!expression.isBlank()) licenses.add(expression);
        else {
            for (JsonNode classifier : info.path("classifiers")) {
                String value = classifier.asText();
                if (value.startsWith("License :: ") && !value.equals("License :: OSI Approved")) {
                    licenses.add(value.substring(value.lastIndexOf(" :: ") + 4));
                }
            }
            if (licenses.isEmpty()) {
                String legacy = info.path("license").asText("");
                if (!legacy.isBlank()) licenses.add(legacy);
            }
        }
        return new LicenseMetadata(licenses, info.path("version").asText(), uri.toString(), "PyPI license_expression, classifiers, or legacy license");
    }

    private LicenseMetadata maven(WatchlistProperties.WatchedPackage pkg) {
        String[] parts = pkg.name().split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Maven package must use groupId:artifactId");
        var coordinates = new PomMetadataParser.Coordinates(parts[0], parts[1], pkg.version());
        var visited = new HashSet<PomMetadataParser.Coordinates>();
        for (int depth = 0; depth < properties.maxParentDepth(); depth++) {
            if (!visited.add(coordinates)) throw new IllegalStateException("Maven parent cycle detected");
            URI uri = append(properties.mavenRepository(), coordinates.repositoryPath());
            var metadata = pomParser.parse(http.getText(uri));
            if (!metadata.licenses().isEmpty() || metadata.parent() == null) {
                return new LicenseMetadata(metadata.licenses(), pkg.version(), uri.toString(),
                        depth == 0 ? "Maven POM" : "Inherited Maven POM licenses (parent depth " + depth + ")");
            }
            coordinates = metadata.parent();
        }
        throw new IllegalStateException("Maven max-parent-depth exceeded; license scan is incomplete");
    }

    private URI append(URI base, String path) {
        return URI.create(base.toString().replaceAll("/+$", "") + "/" + path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
