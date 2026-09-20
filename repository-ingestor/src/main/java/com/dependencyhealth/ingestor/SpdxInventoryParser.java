package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SpdxInventoryParser {
    private final GithubSbomProperties properties;
    public SpdxInventoryParser(GithubSbomProperties properties) { this.properties = properties; }

    public Inventory parse(JsonNode response) {
        JsonNode document = response.has("sbom") ? response.path("sbom") : response;
        if (!document.isObject() || !document.path("packages").isArray() || !document.path("relationships").isArray())
            throw new IllegalArgumentException("GitHub returned an invalid SPDX document");
        Set<String> roots = repositoryRoots(document.path("relationships"));
        Set<String> directIds = directDependencyIds(document.path("relationships"), roots);
        Map<String, DiscoveredDependency> dependencies = new LinkedHashMap<>();
        int unsupported = 0;
        int packageCount = 0;
        for (JsonNode pkg : document.path("packages")) {
            if (roots.contains(pkg.path("SPDXID").asText())) continue;
            if (++packageCount > properties.maxDependencies())
                throw new IllegalArgumentException("Repository exceeds the configured dependency limit");
            ParsedPurl purl = packagePurl(pkg);
            if (purl == null) { unsupported++; continue; }
            String license = license(pkg);
            boolean direct = directIds.contains(pkg.path("SPDXID").asText());
            var dependency = new DiscoveredDependency(purl.name(), purl.ecosystem(), purl.version(), purl.raw(), direct, license);
            String key = dependency.ecosystem() + "\u0000" + dependency.packageName() + "\u0000" + dependency.version();
            dependencies.merge(key, dependency, (a, b) -> a.direct() ? a : b);
        }
        return new Inventory(List.copyOf(dependencies.values()), unsupported, packageCount);
    }

    private Set<String> repositoryRoots(JsonNode relationships) {
        Set<String> roots = new HashSet<>();
        for (JsonNode relationship : relationships) {
            if ("DESCRIBES".equals(relationship.path("relationshipType").asText()))
                roots.add(relationship.path("relatedSpdxElement").asText());
        }
        return roots;
    }
    private Set<String> directDependencyIds(JsonNode relationships, Set<String> roots) {
        Set<String> direct = new HashSet<>();
        for (JsonNode relationship : relationships) {
            String type = relationship.path("relationshipType").asText();
            String element = relationship.path("spdxElementId").asText();
            String related = relationship.path("relatedSpdxElement").asText();
            if ("DEPENDS_ON".equals(type) && roots.contains(element)) direct.add(related);
            if ("DEPENDENCY_OF".equals(type) && roots.contains(related)) direct.add(element);
        }
        return direct;
    }
    private ParsedPurl packagePurl(JsonNode pkg) {
        for (JsonNode ref : pkg.path("externalRefs")) {
            if (!"purl".equalsIgnoreCase(ref.path("referenceType").asText())) continue;
            String raw = ref.path("referenceLocator").asText("");
            ParsedPurl parsed = parsePurl(raw);
            if (parsed != null) return parsed;
        }
        return null;
    }
    static ParsedPurl parsePurl(String raw) {
        if (!raw.startsWith("pkg:")) return null;
        String clean = raw.substring(4).split("[?#]", 2)[0];
        int slash = clean.indexOf('/');
        int at = clean.lastIndexOf('@');
        if (slash < 1 || at <= slash + 1 || at == clean.length() - 1) return null;
        String type = clean.substring(0, slash).toLowerCase();
        String path = decode(clean.substring(slash + 1, at));
        String version = decode(clean.substring(at + 1));
        String ecosystem;
        String name;
        switch (type) {
            case "npm" -> { ecosystem = "npm"; name = path; }
            case "pypi" -> { ecosystem = "pypi"; name = path; }
            case "maven" -> {
                ecosystem = "maven";
                int group = path.lastIndexOf('/');
                if (group < 1 || group == path.length() - 1) return null;
                name = path.substring(0, group).replace('/', '.') + ":" + path.substring(group + 1);
            }
            default -> { return null; }
        }
        if (name.isBlank() || version.isBlank()) return null;
        return new ParsedPurl(ecosystem, name, version, raw);
    }
    private static String decode(String value) {
        try { return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return ""; }
    }
    private String license(JsonNode pkg) {
        String concluded = pkg.path("licenseConcluded").asText("");
        if (!concluded.isBlank() && !"NOASSERTION".equals(concluded)) return concluded;
        String declared = pkg.path("licenseDeclared").asText("");
        return declared.isBlank() || "NOASSERTION".equals(declared) ? "unknown" : declared;
    }
    public record Inventory(List<DiscoveredDependency> dependencies, int unsupportedCount, int packageCount) { }
    record ParsedPurl(String ecosystem, String name, String version, String raw) { }
}
