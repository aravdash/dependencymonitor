package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SpdxInventoryParserTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SpdxInventoryParser parser = new SpdxInventoryParser(properties(100));

    @Test void parsesSupportedPurlsDirectnessLicensesAndSkipsTheRepositoryRoot() throws Exception {
        var inventory = parser.parse(json.readTree("""
                {"sbom":{"packages":[
                  {"SPDXID":"root","externalRefs":[{"referenceType":"purl","referenceLocator":"pkg:github/acme/app@main"}]},
                  {"SPDXID":"npm","licenseConcluded":"MIT","externalRefs":[{"referenceType":"purl","referenceLocator":"pkg:npm/%40scope/core@1.2.3"}]},
                  {"SPDXID":"maven","licenseConcluded":"NOASSERTION","licenseDeclared":"Apache-2.0","externalRefs":[{"referenceType":"purl","referenceLocator":"pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.18.2?type=jar"}]},
                  {"SPDXID":"python","externalRefs":[{"referenceType":"purl","referenceLocator":"pkg:pypi/requests@2.32.3"}]},
                  {"SPDXID":"go","externalRefs":[{"referenceType":"purl","referenceLocator":"pkg:golang/example/module@1.0.0"}]}
                ],"relationships":[
                  {"relationshipType":"DESCRIBES","spdxElementId":"document","relatedSpdxElement":"root"},
                  {"relationshipType":"DEPENDS_ON","spdxElementId":"root","relatedSpdxElement":"npm"},
                  {"relationshipType":"DEPENDENCY_OF","spdxElementId":"python","relatedSpdxElement":"root"}
                ]}}
                """));
        assertThat(inventory.packageCount()).isEqualTo(4);
        assertThat(inventory.unsupportedCount()).isEqualTo(1);
        assertThat(inventory.dependencies()).hasSize(3);
        assertThat(inventory.dependencies()).extracting("ecosystem", "packageName", "version")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("npm", "@scope/core", "1.2.3"),
                        org.assertj.core.groups.Tuple.tuple("maven", "com.fasterxml.jackson.core:jackson-databind", "2.18.2"),
                        org.assertj.core.groups.Tuple.tuple("pypi", "requests", "2.32.3"));
        assertThat(inventory.dependencies().get(0).direct()).isTrue();
        assertThat(inventory.dependencies().get(1).declaredLicense()).isEqualTo("Apache-2.0");
        assertThat(inventory.dependencies().get(2).direct()).isTrue();
        assertThat(inventory.dependencies().get(2).declaredLicense()).isEqualTo("unknown");
    }
    @Test void enforcesShapeAndDependencyBounds() throws Exception {
        assertThatThrownBy(() -> parser.parse(json.readTree("{}"))).isInstanceOf(IllegalArgumentException.class);
        var bounded = new SpdxInventoryParser(properties(1));
        assertThatThrownBy(() -> bounded.parse(json.readTree("""
                {"packages":[{"SPDXID":"a"},{"SPDXID":"b"}],"relationships":[]}
                """))).hasMessageContaining("dependency limit");
    }
    private GithubSbomProperties properties(int maxDependencies) {
        return new GithubSbomProperties(URI.create("https://api.github.test"), "2026-03-10", "",
                Duration.ofSeconds(2), Duration.ofMillis(1), 3, 1_000_000, maxDependencies);
    }
}
