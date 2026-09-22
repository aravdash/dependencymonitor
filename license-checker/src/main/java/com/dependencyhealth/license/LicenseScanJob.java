package com.dependencyhealth.license;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.Severity;
import com.dependencyhealth.contract.kafka.EventPublisher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LicenseScanJob {
    private static final Logger log = LoggerFactory.getLogger(LicenseScanJob.class);
    private final WatchlistProperties watchlist;
    private final RegistryLicenseClient client;
    private final LicensePolicy policy;
    private final EventPublisher publisher;

    public LicenseScanJob(WatchlistProperties watchlist, RegistryLicenseClient client, LicensePolicy policy, EventPublisher publisher) {
        this.watchlist = watchlist;
        this.client = client;
        this.policy = policy;
        this.publisher = publisher;
    }

    @Scheduled(initialDelayString = "${scanner.initial-delay:10s}", fixedDelayString = "${scanner.poll-interval:60s}")
    public void scan() {
        for (var pkg : watchlist.packages()) {
            try {
                scanPackage(pkg, Map.of());
            } catch (RuntimeException ex) {
                log.warn("License scan failed for {}:{}; will retry on next poll: {}", pkg.ecosystem(), pkg.name(), ex.getMessage());
            }
        }
    }

    void scanPackage(WatchlistProperties.WatchedPackage pkg, Map<String, Object> context) {
        boolean repositoryScan = Boolean.TRUE.equals(context.get("repositoryScan"));
        boolean unpinned = repositoryScan && "unspecified".equals(context.get("discoveredVersion"));
        if (unpinned && pkg.ecosystem().equals("maven")) {
            Map<String, Object> detail = new LinkedHashMap<>(context);
            detail.put("scanSkipped", true);
            detail.put("reason", "Maven metadata requires an exact artifact version");
            publish(pkg, Severity.WARNING,
                    "Dependency version is not pinned; exact license analysis is unavailable", detail);
            return;
        }
        LicenseMetadata metadata = client.fetch(pkg);
        var decision = policy.evaluate(metadata.licenses());
        Map<String, Object> detail = new LinkedHashMap<>(context);
        detail.put("version", metadata.version());
        detail.put("licenses", metadata.licenses().stream()
                .map(value -> value.length() > 4000 ? value.substring(0, 4000) + " [truncated]" : value).toList());
        detail.put("matchedRules", decision.matches());
        detail.put("metadataUrl", metadata.metadataUrl());
        detail.put("provenance", metadata.provenance());
        detail.put("policy", "Flag any disallowed term, including OR alternatives and WITH exceptions, for review");
        Severity severity = decision.severity();
        String summary = decision.summary();
        if (unpinned) {
            detail.put("versionAssumption", "Latest registry release used because the repository did not pin a version");
            if (severity == Severity.INFO) severity = Severity.WARNING;
            summary = summary + "; checked latest release because repository version is unpinned";
        }
        publish(pkg, severity, summary, detail);
        log.info("License scan complete for {}:{}: {}", pkg.ecosystem(), pkg.name(), summary);
    }

    private void publish(WatchlistProperties.WatchedPackage pkg, Severity severity, String summary,
            Map<String, Object> detail) {
        publisher.publish(new DependencyEvent(UUID.randomUUID().toString(), "license-check", pkg.name(),
                pkg.ecosystem(), severity, summary, detail, Instant.now()));
    }
}
