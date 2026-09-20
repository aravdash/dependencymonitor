package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DashboardController {
    private final EventRepository repository;

    public DashboardController(EventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/packages")
    public PageResponse<PackageStatus> packages(
            @RequestParam(required = false) String ecosystem,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validatePage(limit, offset);
        return repository.packages(normalizeEcosystem(ecosystem), limit, offset);
    }

    @GetMapping("/packages/{name}/events")
    public PageResponse<DependencyEvent> packageEvents(
            @PathVariable String name,
            @RequestParam(required = false) String ecosystem,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return packageEventsByQuery(name, ecosystem, limit, offset);
    }

    // A query parameter can represent scoped npm names such as @scope/package without
    // requiring the HTTP server to allow encoded slashes in URL path segments.
    @GetMapping("/packages/events")
    public PageResponse<DependencyEvent> packageEventsByQuery(
            @RequestParam String name,
            @RequestParam(required = false) String ecosystem,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validatePage(limit, offset);
        if (name.isBlank() || name.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must contain 1 to 512 characters");
        }
        return repository.packageEvents(name, normalizeEcosystem(ecosystem), limit, offset);
    }

    @GetMapping("/events/recent")
    public PageResponse<DependencyEvent> recentEvents(
            @RequestParam(required = false) String ecosystem,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validatePage(limit, offset);
        return repository.recentEvents(normalizeEcosystem(ecosystem), limit, offset);
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0 || offset > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 200; offset must be between 0 and 1000000");
        }
    }

    private static String normalizeEcosystem(String ecosystem) {
        if (ecosystem == null || ecosystem.isBlank()) {
            return null;
        }
        if (ecosystem.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ecosystem must be at most 64 characters");
        }
        return ecosystem.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
