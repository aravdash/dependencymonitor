package com.dependencyhealth.dashboard;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/repositories")
public class RepositoryController {
    private final RepositoryScanService service;
    private final RepositoryScanRepository repository;
    public RepositoryController(RepositoryScanService service, RepositoryScanRepository repository) {
        this.service = service; this.repository = repository;
    }
    @PostMapping
    public ResponseEntity<RepositoryScanView> submit(@RequestBody Map<String, Object> body) {
        Object value = body.get("repositoryUrl");
        if (!(value instanceof String url)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repositoryUrl is required");
        try { return ResponseEntity.accepted().body(service.submit(url)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        catch (IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not queue repository scan"); }
    }
    @GetMapping
    public PageResponse<RepositoryScanView> recent(@RequestParam(defaultValue = "20") int limit,
                                                   @RequestParam(defaultValue = "0") int offset) {
        validatePage(limit, offset); return repository.recent(limit, offset);
    }
    @GetMapping("/{requestId}")
    public RepositoryScanView scan(@PathVariable String requestId) {
        validateUuid(requestId);
        return repository.find(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @GetMapping("/{requestId}/dependencies")
    public PageResponse<RepositoryDependencyView> dependencies(@PathVariable String requestId,
            @RequestParam(required = false) String ecosystem, @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validateUuid(requestId); validatePage(limit, offset);
        if (repository.find(requestId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String normalized = ecosystem == null || ecosystem.isBlank()
                ? null : ecosystem.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !java.util.List.of("npm", "pypi", "maven").contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ecosystem must be npm, pypi, or maven");
        return repository.dependencies(requestId, normalized, limit, offset);
    }
    private void validateUuid(String value) {
        try { UUID.fromString(value); } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId must be a UUID");
        }
    }
    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 500 || offset < 0 || offset > 1_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1 to 500 and offset must be non-negative");
    }
}
