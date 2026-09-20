package com.dependencyhealth.license;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.kafka.EventPublisher;
import java.time.Instant;
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
                LicenseMetadata metadata = client.fetch(pkg);
                var decision = policy.evaluate(metadata.licenses());
                Map<String, Object> detail = Map.of("version", metadata.version(),
                        "licenses", metadata.licenses().stream().map(s -> s.length() > 4000 ? s.substring(0, 4000) + " [truncated]" : s).toList(),
                        "matchedRules", decision.matches(), "metadataUrl", metadata.metadataUrl(),
                        "provenance", metadata.provenance(), "policy", "Flag any disallowed term, including OR alternatives and WITH exceptions, for review");
                publisher.publish(new DependencyEvent(UUID.randomUUID().toString(), "license-check", pkg.name(), pkg.ecosystem(),
                        decision.severity(), decision.summary(), detail, Instant.now()));
                log.info("License scan complete for {}:{}: {}", pkg.ecosystem(), pkg.name(), decision.summary());
            } catch (RuntimeException ex) {
                log.warn("License scan failed for {}:{}; will retry on next poll: {}", pkg.ecosystem(), pkg.name(), ex.getMessage());
            }
        }
    }
}
