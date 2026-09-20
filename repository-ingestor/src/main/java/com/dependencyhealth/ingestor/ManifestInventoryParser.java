package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Component
class ManifestInventoryParser {
    private final GithubSbomProperties properties;
    private final ObjectMapper mapper;

    ManifestInventoryParser(GithubSbomProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    SpdxInventoryParser.Inventory parse(List<RepositoryManifest> manifests) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Map<String, Candidate> npmDeclarations = new LinkedHashMap<>();
        Set<String> directNames = new HashSet<>();
        Set<String> lockedNpmNames = new HashSet<>();
        for (RepositoryManifest manifest : manifests) {
            String name = fileName(manifest.path()).toLowerCase(Locale.ROOT);
            if (name.equals("package-lock.json")) {
                parsePackageLock(manifest, candidates, directNames, lockedNpmNames);
            } else if (name.equals("package.json")) {
                parsePackageJson(manifest, npmDeclarations, directNames);
            } else if (name.equals("pom.xml")) {
                parsePom(manifest, candidates, directNames);
            } else if (name.matches("requirements[^/]*\\.txt")) {
                parseRequirements(manifest, candidates, directNames);
            }
        }
        npmDeclarations.forEach((key, value) -> {
            if (!lockedNpmNames.contains(value.name())) candidates.putIfAbsent(key, value);
        });
        List<DiscoveredDependency> dependencies = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            if (dependencies.size() >= properties.maxDependencies())
                throw new IllegalArgumentException("Repository exceeds the configured dependency limit");
            boolean direct = directNames.contains(candidate.ecosystem() + ":" + candidate.name());
            dependencies.add(new DiscoveredDependency(candidate.name(), candidate.ecosystem(), candidate.version(),
                    candidate.purl(), direct, "unknown"));
        }
        return new SpdxInventoryParser.Inventory(List.copyOf(dependencies), 0, dependencies.size());
    }

    private void parseRequirements(RepositoryManifest manifest, Map<String, Candidate> output, Set<String> direct) {
        for (String rawLine : manifest.content().lines().toList()) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isBlank() || line.startsWith("-") || line.contains("://")) continue;
            line = line.split(";", 2)[0].trim();
            var match = java.util.regex.Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)(?:\\[[^]]+])?\\s*(.*)$")
                    .matcher(line);
            if (!match.matches()) continue;
            String name = normalizePypi(match.group(1));
            String constraint = match.group(2).trim();
            String version = constraint.isBlank() ? "unspecified" : constraint.replaceAll("\\s+", "");
            String exact = version.startsWith("==") && !version.contains(",") ? version.substring(2) : null;
            Candidate dependency = candidate("pypi", name, version,
                    "pkg:pypi/" + name + (exact == null || exact.isBlank() ? "" : "@" + exact));
            output.putIfAbsent(key(dependency), dependency);
            direct.add("pypi:" + name);
        }
    }

    private void parsePackageJson(RepositoryManifest manifest, Map<String, Candidate> output, Set<String> direct) {
        JsonNode root = json(manifest);
        for (String section : List.of("dependencies", "optionalDependencies", "devDependencies")) {
            JsonNode dependencies = root.path(section);
            if (!dependencies.isObject()) continue;
            dependencies.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                String version = entry.getValue().asText("unspecified").trim();
                if (version.isBlank()) version = "unspecified";
                Candidate candidate = candidate("npm", name, version, "pkg:npm/" + npmPurlName(name));
                output.putIfAbsent(key(candidate), candidate);
                direct.add("npm:" + name);
            });
        }
    }

    private void parsePackageLock(RepositoryManifest manifest, Map<String, Candidate> output,
            Set<String> direct, Set<String> lockedNames) {
        JsonNode root = json(manifest);
        JsonNode packages = root.path("packages");
        if (packages.isObject()) {
            JsonNode rootPackage = packages.path("");
            addNpmDirectNames(rootPackage, direct);
            packages.fields().forEachRemaining(entry -> {
                if (entry.getKey().isBlank()) return;
                String name = packageNameFromLockPath(entry.getKey());
                String version = entry.getValue().path("version").asText("").trim();
                if (name == null || version.isBlank()) return;
                Candidate candidate = candidate("npm", name, version,
                        "pkg:npm/" + npmPurlName(name) + "@" + version);
                output.putIfAbsent(key(candidate), candidate);
                lockedNames.add(name);
            });
        } else if (root.path("dependencies").isObject()) {
            parseLegacyNpmDependencies(root.path("dependencies"), output, direct, lockedNames, true);
        }
    }

    private void parseLegacyNpmDependencies(JsonNode dependencies, Map<String, Candidate> output,
            Set<String> direct, Set<String> lockedNames, boolean root) {
        dependencies.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            String version = entry.getValue().path("version").asText("").trim();
            if (!version.isBlank()) {
                Candidate candidate = candidate("npm", name, version,
                        "pkg:npm/" + npmPurlName(name) + "@" + version);
                output.putIfAbsent(key(candidate), candidate);
                lockedNames.add(name);
                if (root) direct.add("npm:" + name);
            }
            if (entry.getValue().path("dependencies").isObject())
                parseLegacyNpmDependencies(entry.getValue().path("dependencies"), output, direct, lockedNames, false);
        });
    }

    private void addNpmDirectNames(JsonNode rootPackage, Set<String> direct) {
        for (String section : List.of("dependencies", "optionalDependencies", "devDependencies")) {
            JsonNode values = rootPackage.path(section);
            if (values.isObject()) values.fieldNames().forEachRemaining(name -> direct.add("npm:" + name));
        }
    }

    private void parsePom(RepositoryManifest manifest, Map<String, Candidate> output, Set<String> direct) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element project = factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest.content())))
                    .getDocumentElement();
            Map<String, String> properties = pomProperties(project);
            for (Element dependencies : childElements(project, "dependencies")) {
                for (Element dependency : childElements(dependencies, "dependency")) {
                    String group = text(dependency, "groupId");
                    String artifact = text(dependency, "artifactId");
                    if (group.isBlank() || artifact.isBlank()) continue;
                    String version = resolvePomValue(text(dependency, "version"), properties);
                    if (version.isBlank()) version = "unspecified";
                    String name = group + ":" + artifact;
                    String purl = "pkg:maven/" + group + "/" + artifact
                            + (version.equals("unspecified") ? "" : "@" + version);
                    Candidate candidate = candidate("maven", name, version, purl);
                    output.putIfAbsent(key(candidate), candidate);
                    direct.add("maven:" + name);
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Repository contains an invalid Maven POM", ex);
        }
    }

    private Map<String, String> pomProperties(Element project) {
        Map<String, String> values = new HashMap<>();
        for (Element properties : childElements(project, "properties")) {
            for (Element property : childElements(properties, null))
                values.put(property.getLocalName() == null ? property.getNodeName() : property.getLocalName(),
                        property.getTextContent().trim());
        }
        return values;
    }

    private String resolvePomValue(String value, Map<String, String> properties) {
        if (value.matches("\\$\\{[^}]+}")) return properties.getOrDefault(value.substring(2, value.length() - 1), value);
        return value;
    }

    private List<Element> childElements(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                String actual = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
                if (localName == null || localName.equals(actual)) result.add(element);
            }
        }
        return result;
    }

    private String text(Element parent, String name) {
        return childElements(parent, name).stream().findFirst().map(element -> element.getTextContent().trim()).orElse("");
    }

    private JsonNode json(RepositoryManifest manifest) {
        try { return mapper.readTree(manifest.content()); }
        catch (Exception ex) { throw new IllegalArgumentException("Repository contains invalid " + fileName(manifest.path()), ex); }
    }

    private String packageNameFromLockPath(String path) {
        String normalized = path.replace('\\', '/');
        int marker = normalized.lastIndexOf("node_modules/");
        if (marker < 0) return null;
        String name = normalized.substring(marker + "node_modules/".length());
        return name.isBlank() || name.contains("/node_modules/") ? null : name;
    }

    private Candidate candidate(String ecosystem, String name, String version, String purl) {
        return new Candidate(ecosystem, name, version, purl);
    }
    private String key(Candidate candidate) {
        return candidate.ecosystem() + "\u0000" + candidate.name() + "\u0000" + candidate.version();
    }
    private String fileName(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    private String normalizePypi(String name) { return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-"); }
    private String npmPurlName(String name) { return name.startsWith("@") ? "%40" + name.substring(1) : name; }
    private record Candidate(String ecosystem, String name, String version, String purl) { }
}
