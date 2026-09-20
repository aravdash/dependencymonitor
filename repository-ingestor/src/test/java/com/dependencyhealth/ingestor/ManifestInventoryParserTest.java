package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestInventoryParserTest {
    private final ManifestInventoryParser parser = new ManifestInventoryParser(properties(), new ObjectMapper());

    @Test
    void discoversRobofleetStyleUnpinnedPythonRequirementsAcrossServices() {
        var inventory = parser.parse(List.of(
                new RepositoryManifest("RoboFleet-HEAD/ingestion/requirements.txt", """
                        fastapi
                        uvicorn[standard]
                        redis
                        prometheus-client
                        """),
                new RepositoryManifest("RoboFleet-HEAD/robot_agent/requirements.txt", "websockets\n"),
                new RepositoryManifest("RoboFleet-HEAD/scheduler/requirements.txt", """
                        fastapi
                        redis
                        httpx
                        """)));

        assertThat(inventory.dependencies()).hasSize(6);
        assertThat(inventory.dependencies()).extracting("packageName")
                .containsExactly("fastapi", "uvicorn", "redis", "prometheus-client", "websockets", "httpx");
        assertThat(inventory.dependencies()).allSatisfy(dependency -> {
            assertThat(dependency.ecosystem()).isEqualTo("pypi");
            assertThat(dependency.version()).isEqualTo("unspecified");
            assertThat(dependency.direct()).isTrue();
        });
    }

    @Test
    void prefersLockedNpmVersionsAndReadsMavenDirectDependencies() {
        var inventory = parser.parse(List.of(
                new RepositoryManifest("app/package.json", """
                        {"dependencies":{"lodash":"^4.17.0"},"devDependencies":{"vitest":"^3.0.0"}}
                        """),
                new RepositoryManifest("app/package-lock.json", """
                        {"lockfileVersion":3,"packages":{
                          "":{"dependencies":{"lodash":"^4.17.0"},"devDependencies":{"vitest":"^3.0.0"}},
                          "node_modules/lodash":{"version":"4.17.21"},
                          "node_modules/vitest":{"version":"3.0.9"},
                          "node_modules/tinybench":{"version":"3.1.1"}
                        }}
                        """),
                new RepositoryManifest("service/pom.xml", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <properties><spring.version>6.2.0</spring.version></properties>
                          <dependencyManagement><dependencies><dependency><groupId>ignored</groupId><artifactId>bom</artifactId><version>1</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>${spring.version}</version></dependency></dependencies>
                        </project>
                        """)));

        assertThat(inventory.dependencies()).extracting("ecosystem", "packageName", "version", "direct")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("npm", "lodash", "4.17.21", true),
                        org.assertj.core.groups.Tuple.tuple("npm", "vitest", "3.0.9", true),
                        org.assertj.core.groups.Tuple.tuple("npm", "tinybench", "3.1.1", false),
                        org.assertj.core.groups.Tuple.tuple("maven", "org.springframework:spring-core", "6.2.0", true));
    }

    private GithubSbomProperties properties() {
        return new GithubSbomProperties(URI.create("https://api.github.test"), "2026-03-10", "",
                Duration.ofSeconds(2), Duration.ofMillis(1), 3, 1_000_000, 100);
    }
}
