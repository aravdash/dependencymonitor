package com.dependencyhealth.contract.repository;

import java.net.URI;
import java.util.Locale;

public record GithubRepositoryReference(String owner, String repository) {
    public static GithubRepositoryReference parse(String input) {
        if (input == null || input.isBlank() || input.length() > 500)
            throw new IllegalArgumentException("Enter a GitHub repository URL");
        String candidate = input.trim();
        if (candidate.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?")) {
            candidate = "https://github.com/" + candidate;
        }
        URI uri;
        try { uri = URI.create(candidate); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a valid GitHub repository URL"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Only HTTPS github.com repository URLs are supported");
        String[] parts = uri.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length != 2) throw new IllegalArgumentException("Use a repository URL such as https://github.com/owner/repo");
        String repository = parts[1].replaceFirst("(?i)\\.git$", "");
        // Reuse the event constructor's validation without creating an event.
        if (!parts[0].matches("[A-Za-z0-9_.-]{1,100}") || !repository.matches("[A-Za-z0-9_.-]{1,100}")
                || parts[0].startsWith(".") || repository.startsWith(".")
                || parts[0].endsWith(".") || repository.endsWith("."))
            throw new IllegalArgumentException("The GitHub owner or repository name is invalid");
        return new GithubRepositoryReference(parts[0], repository);
    }
    public String canonicalUrl() { return "https://github.com/" + owner + "/" + repository; }
    public String repositoryId() {
        return "github:" + owner.toLowerCase(Locale.ROOT) + "/" + repository.toLowerCase(Locale.ROOT);
    }
}
