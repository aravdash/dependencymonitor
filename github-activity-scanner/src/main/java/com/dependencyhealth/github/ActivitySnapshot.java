package com.dependencyhealth.github;

import java.time.Instant;

public record ActivitySnapshot(Instant lastCommit, int recentCommits, int baselineCommits,
                               boolean historyComplete) {
}
