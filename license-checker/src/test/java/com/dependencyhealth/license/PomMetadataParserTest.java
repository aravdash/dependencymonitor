package com.dependencyhealth.license;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PomMetadataParserTest {
    private final PomMetadataParser parser = new PomMetadataParser();

    @Test
    void readsOnlyProjectLicensesAndExposesParentForInheritance() {
        var metadata = parser.parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent>
                  <profiles><profile><licenses><license><name>Wrong profile license</name></license></licenses></profile></profiles>
                </project>
                """);
        assertThat(metadata.licenses()).isEmpty();
        assertThat(metadata.parent().repositoryPath()).isEqualTo("org/example/parent/1.0/parent-1.0.pom");
    }

    @Test
    void localLicenseOverridesParent() {
        var metadata = parser.parse("""
                <project><licenses><license><name>Apache License, Version 2.0</name></license></licenses>
                  <parent><groupId>ignored</groupId><artifactId>parent</artifactId><version>${revision}</version></parent>
                </project>
                """);
        assertThat(metadata.licenses()).containsExactly("Apache License, Version 2.0");
        assertThat(metadata.parent()).isNull();
    }

    @Test
    void rejectsExternalEntitiesAndCoordinateTraversal() {
        assertThatThrownBy(() -> parser.parse("""
                <!DOCTYPE project [<!ENTITY injected SYSTEM "file:///etc/passwd">]>
                <project><licenses><license><name>&injected;</name></license></licenses></project>
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PomMetadataParser.Coordinates("org.example", "../secret", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
