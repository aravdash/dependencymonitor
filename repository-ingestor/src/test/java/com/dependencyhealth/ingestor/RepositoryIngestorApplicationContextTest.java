package com.dependencyhealth.ingestor;

import static org.assertj.core.api.Assertions.assertThat;

import com.dependencyhealth.contract.kafka.RepositoryMessagePublisher;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = RepositoryIngestorApplication.class, properties = {
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false"
})
class RepositoryIngestorApplicationContextTest {
    @Autowired ApplicationContext context;
    @Autowired GithubSbomProperties properties;

    @Test
    void bindsDefaultsAndWiresTheEventDrivenWorker() {
        assertThat(properties.apiBaseUrl().toString()).isEqualTo("https://api.github.com");
        assertThat(properties.maxDependencies()).isEqualTo(20_000);
        assertThat(context.getBean(GithubSbomClient.class)).isNotNull();
        assertThat(context.getBean(GithubArchiveClient.class)).isNotNull();
        assertThat(context.getBean(SpdxInventoryParser.class)).isNotNull();
        assertThat(context.getBean(ManifestInventoryParser.class)).isNotNull();
        assertThat(context.getBean(RepositoryDependencyDiscoverer.class)).isNotNull();
        assertThat(context.getBean(RepositoryIngestionListener.class)).isNotNull();
        assertThat(context.getBean(RepositoryMessageCodec.class)).isNotNull();
        assertThat(context.getBean(RepositoryMessagePublisher.class)).isNotNull();
    }
}
