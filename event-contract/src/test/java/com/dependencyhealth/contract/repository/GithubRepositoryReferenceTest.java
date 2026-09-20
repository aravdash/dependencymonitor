package com.dependencyhealth.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GithubRepositoryReferenceTest {
    @Test void acceptsCanonicalCloneAndShorthandForms() {
        assertThat(GithubRepositoryReference.parse("https://github.com/Google/guava").canonicalUrl())
                .isEqualTo("https://github.com/Google/guava");
        assertThat(GithubRepositoryReference.parse("https://github.com/Google/guava.git/").repository())
                .isEqualTo("guava");
        assertThat(GithubRepositoryReference.parse("Google/guava").repositoryId())
                .isEqualTo("github:google/guava");
    }
    @Test void rejectsUrlsThatCouldEscapeTheGithubRepositoryBoundary() {
        for (String input : List.of("http://github.com/a/b", "https://example.com/a/b",
                "https://github.com/a/b/issues", "https://user@github.com/a/b",
                "https://github.com/a/b?tab=readme", "../etc/passwd")) {
            assertThatThrownBy(() -> GithubRepositoryReference.parse(input))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void repositoryMessagesRoundTrip() {
        var codec = new RepositoryMessageCodec(new com.fasterxml.jackson.databind.ObjectMapper());
        var request = new RepositoryScanRequest(UUID.randomUUID().toString(), "github", "Google", "guava",
                "https://github.com/Google/guava", Instant.parse("2026-09-20T12:00:00Z"));
        assertThat(codec.readRequest(codec.write(request))).isEqualTo(request);
        var inventory = new RepositoryInventoryEvent(request.requestId(), request.repositoryId(), request.repositoryUrl(),
                InventoryStatus.COMPLETE, List.of(new DiscoveredDependency("guava", "maven", "33.4.0-jre",
                "pkg:maven/com.google.guava/guava@33.4.0-jre", true, "Apache-2.0")), 0, "Complete", request.requestedAt());
        assertThat(codec.readInventory(codec.write(inventory))).isEqualTo(inventory);
    }
}
