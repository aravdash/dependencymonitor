package com.dependencyhealth.github;

import com.dependencyhealth.contract.Severity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class ActivityClassifier {
    private final GithubScannerProperties properties;

    public ActivityClassifier(GithubScannerProperties properties) {
        this.properties = properties;
    }

    public Assessment classify(ActivitySnapshot snapshot, Instant now) {
        if (snapshot.lastCommit() == null) {
            return new Assessment(Severity.WARNING, "no-commits", "Repository has no commits");
        }
        Instant inactiveCutoff = now.atZone(ZoneOffset.UTC).minusMonths(properties.inactiveMonths()).toInstant();
        if (snapshot.lastCommit().isBefore(inactiveCutoff)) {
            return new Assessment(Severity.WARNING, "inactive",
                    "No commits in more than " + properties.inactiveMonths() + " months");
        }
        // A partial baseline could create a false spike; never extrapolate from truncated history.
        if (!snapshot.historyComplete()) {
            return new Assessment(Severity.INFO, "incomplete-history",
                    "Recent commits found; spike assessment skipped because the pagination limit was reached");
        }
        double expectedRecentCommits = snapshot.baselineCommits()
                * (double) properties.recentWindowDays() / properties.baselineWindowDays();
        if (snapshot.recentCommits() >= properties.spikeMinimumCommits()
                && (expectedRecentCommits == 0
                || snapshot.recentCommits() >= expectedRecentCommits * properties.spikeMultiplier())) {
            return new Assessment(Severity.WARNING, "commit-spike",
                    "Commit activity increased: " + snapshot.recentCommits() + " commits in "
                            + properties.recentWindowDays() + " days versus " + snapshot.baselineCommits()
                            + " in the preceding " + properties.baselineWindowDays() + " days");
        }
        return new Assessment(Severity.INFO, "normal-activity", "Repository activity is within configured thresholds");
    }

    public record Assessment(Severity severity, String findingType, String summary) {
    }
}
