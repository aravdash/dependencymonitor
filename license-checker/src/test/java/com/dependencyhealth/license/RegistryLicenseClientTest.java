package com.dependencyhealth.license;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistryLicenseClientTest {
    private final ExternalApiClient http = mock(ExternalApiClient.class);
    private final URI registry = URI.create("https://registry.example");
    private final RegistryLicenseClient client = new RegistryLicenseClient(http,
            new LicenseProperties(List.of("GPL-*"), registry, registry, registry, 5), new PomMetadataParser());

    @Test
    void fetchesParentPomLicensesFromSameRepository() {
        when(http.getText(URI.create("https://registry.example/com/example/app/1.0/app-1.0.pom"))).thenReturn("""
                <project><parent><groupId>com.example</groupId><artifactId>parent</artifactId><version>2.0</version></parent></project>
                """);
        when(http.getText(URI.create("https://registry.example/com/example/parent/2.0/parent-2.0.pom"))).thenReturn("""
                <project><licenses><license><name>Apache-2.0</name></license></licenses></project>
                """);
        var metadata = client.fetch(new WatchlistProperties.WatchedPackage("com.example:app", "maven", "1.0"));
        assertThat(metadata.licenses()).containsExactly("Apache-2.0");
        assertThat(metadata.version()).isEqualTo("1.0");
        assertThat(metadata.provenance()).contains("parent depth 1");
    }

    @Test
    void encodesScopedNpmPackagesAndReadsLegacyLicenseObjects() throws Exception {
        when(http.getJson(any(URI.class))).thenReturn(new ObjectMapper().readTree("""
                {"name":"@org/lib","version":"1.2.0","license":{"type":"MIT"}}
                """));
        var metadata = client.fetch(new WatchlistProperties.WatchedPackage("@org/lib", "npm", ""));
        assertThat(metadata.licenses()).containsExactly("MIT");
        verify(http).getJson(URI.create("https://registry.example/%40org%2Flib/latest"));
    }

    @Test
    void pypiPrefersLicenseExpressionOverLegacyFreeText() throws Exception {
        when(http.getJson(any(URI.class))).thenReturn(new ObjectMapper().readTree("""
                {"info":{"name":"lib","version":"1.0","license_expression":"MIT","license":"UNKNOWN",
                  "classifiers":["License :: OSI Approved :: GNU General Public License v3 (GPLv3)"]}}
                """));
        assertThat(client.fetch(new WatchlistProperties.WatchedPackage("lib", "pypi", "")).licenses()).containsExactly("MIT");
    }
}
