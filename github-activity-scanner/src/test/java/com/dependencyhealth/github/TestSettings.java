package com.dependencyhealth.github;

final class TestSettings {
    private TestSettings() { }

    static GithubScannerProperties properties() {
        return properties(3, "");
    }

    static GithubScannerProperties properties(int pages, String token) {
        return new GithubScannerProperties("https://api.github.com", token, 12, 7, 28, 10, 3.0,
                pages, 60_000, 3_600_000);
    }
}
