package com.dependencyhealth.license;

import static org.assertj.core.api.Assertions.assertThat;

import com.dependencyhealth.contract.http.ExternalApiClient;
import com.dependencyhealth.contract.kafka.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

@SpringBootTest(classes = LicenseCheckerApplication.class, properties = {
        "scanner.initial-delay=1h", "spring.kafka.admin.auto-create=false"
})
class LicenseApplicationContextTest {
    @Autowired ApplicationContext context;
    @Autowired WatchlistProperties watchlist;
    @Autowired LicenseProperties properties;
    @Autowired ScheduledAnnotationBeanPostProcessor scheduling;

    @Test void bindsTheEightPackageYamlWatchlistAndWiresTheWorker() {
        assertThat(watchlist.packages()).hasSize(8);
        assertThat(watchlist.packages()).extracting(WatchlistProperties.WatchedPackage::ecosystem)
                .containsOnly("npm", "pypi", "maven");
        assertThat(watchlist.packages().stream().filter(pkg -> pkg.ecosystem().equals("maven")))
                .allSatisfy(pkg -> assertThat(pkg.version()).isNotBlank());
        assertThat(properties.disallowed()).contains("GPL-*", "LGPL-*", "AGPL-*");
        assertThat(context.getBean(LicenseScanJob.class)).isNotNull();
        assertThat(context.getBean(RegistryLicenseClient.class)).isNotNull();
        assertThat(context.getBean(EventPublisher.class)).isNotNull();
        assertThat(context.getBean(ExternalApiClient.class)).isNotNull();
        assertThat(scheduling.getScheduledTasks()).hasSize(1);
    }
}
