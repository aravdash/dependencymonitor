package com.dependencyhealth.github;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.http.ExternalApiException;
import com.dependencyhealth.contract.kafka.EventPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class GithubScanScheduler {
    private static final Logger log = LoggerFactory.getLogger(GithubScanScheduler.class);
    private final GithubApiClient client;
    private final ActivityClassifier classifier;
    private final EventPublisher publisher;
    private final GithubWatchlist watchlist;
    private final GithubScannerProperties properties;
    private final Clock clock;
    private final Map<String, Failure> failures = new HashMap<>();
    private Instant apiRetryAt = Instant.EPOCH;

    public GithubScanScheduler(GithubApiClient client, ActivityClassifier classifier, EventPublisher publisher,
                               GithubWatchlist watchlist, GithubScannerProperties properties, Clock clock) {
        this.client = client;
        this.classifier = classifier;
        this.publisher = publisher;
        this.watchlist = watchlist;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void reportConfiguration() {
        if (properties.token().isBlank()) {
            log.warn("GITHUB_TOKEN is unset: GitHub requests are unauthenticated and limited to 60 requests/hour per IP. "
                    + "The 60-second demo poll can exhaust this quickly; set GITHUB_TOKEN or increase GITHUB_POLL_INTERVAL_MS.");
        }
        if (watchlist.packages().isEmpty()) {
            log.warn("GitHub watchlist is empty; no packages will be scanned");
        }
        log.info("GitHub scanner configured for {} packages", watchlist.packages().size());
    }

    @Scheduled(fixedDelayString = "${scanner.github.poll-interval-ms:60000}",
            initialDelayString = "${scanner.github.initial-delay-ms:10000}")
    public void scan() {
        if (clock.instant().isBefore(apiRetryAt)) {
            return;
        }
        // Spring's single fixed-delay invocation serializes access to the cooldown state.
        Map<String, ActivitySnapshot> repositoryCache = new HashMap<>();
        for (GithubWatchlist.WatchedPackage pkg : watchlist.packages()) {
            String key = pkg.ecosystem() + ":" + pkg.name();
            Instant now = clock.instant();
            Failure previousFailure = failures.get(key);
            if (previousFailure != null && now.isBefore(previousFailure.retryAt())) {
                continue;
            }
            try {
                ActivitySnapshot snapshot = repositoryCache.computeIfAbsent(pkg.repository(), repo -> client.fetch(repo, now));
                ActivityClassifier.Assessment assessment = classifier.classify(snapshot, now);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("repository", pkg.repository());
                detail.put("repositoryUrl", "https://github.com/" + pkg.repository());
                detail.put("findingType", assessment.findingType());
                detail.put("lastCommit", snapshot.lastCommit() == null ? null : snapshot.lastCommit().toString());
                detail.put("recentCommits", snapshot.recentCommits());
                detail.put("baselineCommits", snapshot.baselineCommits());
                detail.put("recentWindowDays", properties.recentWindowDays());
                detail.put("baselineWindowDays", properties.baselineWindowDays());
                detail.put("historyComplete", snapshot.historyComplete());
                detail.put("inactiveMonths", properties.inactiveMonths());
                publisher.publish(new DependencyEvent(UUID.randomUUID().toString(), "github-activity", pkg.name(),
                        pkg.ecosystem(), assessment.severity(), assessment.summary(), detail, now));
                failures.remove(key);
                log.info("Published {} activity finding for {}:{} ({})", assessment.severity(), pkg.ecosystem(),
                        pkg.name(), assessment.findingType());
            } catch (Exception exception) {
                long previousDelay = previousFailure == null ? 0 : previousFailure.delayMs();
                long delay = previousDelay == 0 ? properties.failureBackoffMs()
                        : Math.min(properties.maxFailureBackoffMs(), previousDelay > Long.MAX_VALUE / 2
                        ? Long.MAX_VALUE : previousDelay * 2);
                Instant retryAt = clock.instant().plusMillis(delay);
                failures.put(key, new Failure(delay, retryAt));
                // Exceptions from the HTTP layer never include request authentication headers.
                log.warn("GitHub scan failed for {}:{}; retry after {}: {}", pkg.ecosystem(), pkg.name(),
                        retryAt, exception.toString());
                if (exception instanceof ExternalApiException apiException
                        && (apiException.statusCode() == 429
                        || (apiException.statusCode() == 403 && !apiException.retryAfter().isZero()))) {
                    Instant serverRetryAt = clock.instant().plus(apiException.retryAfter());
                    apiRetryAt = serverRetryAt.isAfter(retryAt) ? serverRetryAt : retryAt;
                    log.warn("GitHub API rate limit reached; pausing all repositories until {}", apiRetryAt);
                    break;
                }
            }
        }
    }

    private record Failure(long delayMs, Instant retryAt) {
    }
}
