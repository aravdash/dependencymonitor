package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class GithubArchiveClient {
    private static final int MAX_ARCHIVE_ENTRIES = 50_000;
    private static final int MAX_MANIFESTS = 500;
    private static final int MAX_MANIFEST_BYTES = 2_000_000;
    private static final Set<String> EXACT_NAMES = Set.of("package.json", "package-lock.json", "pom.xml");
    private final GithubSbomProperties properties;
    private final HttpClient http;

    @Autowired
    GithubArchiveClient(GithubSbomProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
    }

    GithubArchiveClient(GithubSbomProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    List<RepositoryManifest> fetch(RepositoryScanRequest request) {
        URI uri = URI.create("https://codeload.github.com/" + request.owner() + "/"
                + request.repository() + "/zip/HEAD");
        byte[] archive = download(uri);
        return manifests(archive);
    }

    private byte[] download(URI uri) {
        GithubApiException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(properties.requestTimeout()).GET()
                        .header("Accept", "application/zip")
                        .header("User-Agent", "dependency-health-monitor/1.0").build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() == 200) return bounded(body, properties.maxResponseBytes());
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500
                        && response.statusCode() != 408 && response.statusCode() != 429) {
                    throw new GithubApiException("The public repository source archive was not found", response.statusCode());
                }
                last = new GithubApiException("GitHub source archive is temporarily unavailable", response.statusCode());
            } catch (IOException ex) {
                last = new GithubApiException("Cannot download the public repository source archive", 0);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new GithubApiException("Repository archive download interrupted", 0);
            }
            if (attempt < 3) pause(Duration.ofMillis(500L << (attempt - 1)));
        }
        throw last;
    }

    private List<RepositoryManifest> manifests(byte[] archive) {
        List<RepositoryManifest> manifests = new ArrayList<>();
        long expandedBytes = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES)
                    throw new GithubApiException("Repository archive contains too many files", 0);
                boolean manifest = !entry.isDirectory() && isSupportedManifest(entry.getName());
                ByteArrayOutputStream captured = manifest ? new ByteArrayOutputStream() : null;
                int read;
                int entryBytes = 0;
                while ((read = zip.read(buffer)) >= 0) {
                    expandedBytes += read;
                    entryBytes += read;
                    if (expandedBytes > (long) properties.maxResponseBytes() * 4)
                        throw new GithubApiException("Repository archive expands beyond the configured limit", 0);
                    if (manifest) {
                        if (entryBytes > MAX_MANIFEST_BYTES)
                            throw new GithubApiException("A repository dependency manifest is too large", 0);
                        captured.write(buffer, 0, read);
                    }
                }
                if (manifest) {
                    if (manifests.size() >= MAX_MANIFESTS)
                        throw new GithubApiException("Repository contains too many dependency manifests", 0);
                    manifests.add(new RepositoryManifest(entry.getName(), captured.toString(StandardCharsets.UTF_8)));
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new GithubApiException("GitHub returned an invalid repository archive", 0);
        }
        if (manifests.isEmpty())
            throw new GithubApiException("No supported dependency manifests were found in the repository", 0);
        return List.copyOf(manifests);
    }

    private boolean isSupportedManifest(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("/node_modules/") || normalized.contains("/.git/")
                || normalized.contains("/vendor/")) return false;
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return EXACT_NAMES.contains(name) || name.matches("requirements[^/]*\\.txt");
    }

    private byte[] bounded(InputStream stream, int limit) throws IOException {
        byte[] bytes = stream.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Repository archive exceeds configured limit");
        return bytes;
    }

    private void pause(Duration duration) {
        try { Thread.sleep(duration.toMillis()); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GithubApiException("Repository archive retry interrupted", 0);
        }
    }
}
