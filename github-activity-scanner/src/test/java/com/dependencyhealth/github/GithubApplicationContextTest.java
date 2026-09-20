package com.dependencyhealth.github;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.dependencyhealth.contract.kafka.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GithubActivityScannerApplication.class, properties = {
        "scanner.github.initial-delay-ms=3600000",
        "spring.kafka.admin.auto-create=false"
})
class GithubApplicationContextTest {
    @Autowired private ApplicationContext context;
    @Autowired private GithubWatchlist watchlist;
    @Autowired private GithubScannerProperties properties;
    @Autowired private ScheduledAnnotationBeanPostProcessor scheduling;

    @Test
    void bindsDefaultWatchlistAndWiresScheduledProducerWithoutCallingExternalServices() {
        assertThat(watchlist.packages()).hasSize(8);
        assertThat(watchlist.packages()).extracting(GithubWatchlist.WatchedPackage::name)
                .containsExactly("lodash", "express", "chalk", "requests", "flask", "numpy",
                        "org.springframework.boot:spring-boot-starter-web",
                        "com.fasterxml.jackson.core:jackson-databind");
        assertThat(watchlist.packages()).extracting(GithubWatchlist.WatchedPackage::ecosystem)
                .containsOnly("npm", "pypi", "maven");
        assertThat(watchlist.packages()).allSatisfy(pkg -> assertThat(pkg.repository()).contains("/"));
        assertThat(properties.inactiveMonths()).isPositive();
        assertThat(properties.maxCommitPages()).isPositive();
        assertThat(context.getBean(GithubScanScheduler.class)).isNotNull();
        assertThat(context.getBean(GithubApiClient.class)).isNotNull();
        assertThat(context.getBean(ActivityClassifier.class)).isNotNull();
        assertThat(context.getBean(EventPublisher.class)).isNotNull();
        assertThat(context.getBean(ExternalApiClient.class)).isNotNull();
        assertThat(scheduling.getScheduledTasks()).hasSize(1);
    }
}
