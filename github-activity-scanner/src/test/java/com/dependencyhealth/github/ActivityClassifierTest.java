package com.dependencyhealth.github;

import com.dependencyhealth.contract.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityClassifierTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private final ActivityClassifier classifier = new ActivityClassifier(TestSettings.properties());

    @Test
    void inactivityUsesCalendarMonthsAndRequiresStrictlyOlderCommit() {
        Instant cutoff = NOW.atZone(ZoneOffset.UTC).minusMonths(12).toInstant();
        assertThat(classifier.classify(new ActivitySnapshot(cutoff.minusSeconds(1), 0, 0, true), NOW).findingType())
                .isEqualTo("inactive");
        assertThat(classifier.classify(new ActivitySnapshot(cutoff, 0, 0, true), NOW).severity())
                .isEqualTo(Severity.INFO);
    }

    @Test
    void comparesDailyRatesAcrossDifferentLengthWindows() {
        // 28 baseline commits over 28 days = seven expected over the seven-day recent window.
        assertThat(classifier.classify(new ActivitySnapshot(NOW, 21, 28, true), NOW).findingType())
                .isEqualTo("commit-spike");
        assertThat(classifier.classify(new ActivitySnapshot(NOW, 20, 28, true), NOW).findingType())
                .isEqualTo("normal-activity");
    }

    @Test
    void quietBaselineStillRequiresMinimumRecentVolume() {
        assertThat(classifier.classify(new ActivitySnapshot(NOW, 9, 0, true), NOW).severity()).isEqualTo(Severity.INFO);
        assertThat(classifier.classify(new ActivitySnapshot(NOW, 10, 0, true), NOW).findingType()).isEqualTo("commit-spike");
    }

    @Test
    void neverClaimsSpikeWithTruncatedBaseline() {
        var result = classifier.classify(new ActivitySnapshot(NOW, 300, 0, false), NOW);
        assertThat(result.findingType()).isEqualTo("incomplete-history");
        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void repositoryWithNoCommitsIsAWarning() {
        assertThat(classifier.classify(new ActivitySnapshot(null, 0, 0, true), NOW).severity()).isEqualTo(Severity.WARNING);
    }
}
