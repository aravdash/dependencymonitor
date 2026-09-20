package com.dependencyhealth.contract;

import static org.assertj.core.api.Assertions.assertThat;
import com.dependencyhealth.contract.kafka.ConsumerKafkaConfiguration;
import com.dependencyhealth.contract.kafka.EventPublisher;
import com.dependencyhealth.contract.kafka.ProducerKafkaConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/** Uses a real embedded broker: independent offsets and poison-message recovery are exercised on the wire. */
@SpringBootTest(classes = KafkaIndependenceTest.TestApplication.class, properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest", "spring.kafka.consumer.group-id=test-default",
        "spring.kafka.admin.fail-fast=true", "spring.main.web-application-type=none"})
@EmbeddedKafka(partitions = 3, topics = {EventTopics.DEPENDENCY_EVENTS, EventTopics.DEAD_LETTER},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"group.initial.rebalance.delay.ms=0"})
@DirtiesContext
class KafkaIndependenceTest {
    @Autowired private EventPublisher publisher;
    @Autowired private KafkaTemplate<String, String> template;
    @Autowired private TestListeners listeners;
    @Autowired private EmbeddedKafkaBroker broker;

    @Test void independentGroupsReceiveAllEventsAndRecoverPoisonWithoutBlockingNextEvent() throws Exception {
        var first = event("first");
        publisher.publish(first);
        assertThat(listeners.dashboard.poll(30, TimeUnit.SECONDS)).isEqualTo(first);
        assertThat(listeners.alerter.poll(30, TimeUnit.SECONDS)).isEqualTo(first);

        var props = KafkaTestUtils.consumerProps("dlt-verification", "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (var deadLetters = new DefaultKafkaConsumerFactory<>(props,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(deadLetters, EventTopics.DEAD_LETTER);
            template.send(EventTopics.DEPENDENCY_EVENTS, first.partitionKey(), "{broken-json}").get(10, TimeUnit.SECONDS);
            var next = event("after poison");
            publisher.publish(next);
            assertThat(listeners.dashboard.poll(30, TimeUnit.SECONDS)).isEqualTo(next);
            assertThat(listeners.alerter.poll(30, TimeUnit.SECONDS)).isEqualTo(next);
            var recovered = KafkaTestUtils.getRecords(deadLetters, Duration.ofSeconds(30), 2);
            assertThat(recovered.count()).isEqualTo(2); // One failure per independent consumer group.
            recovered.forEach(record -> assertThat(record.value()).isEqualTo("{broken-json}"));
        }
    }
    private DependencyEvent event(String summary) {
        return new DependencyEvent(UUID.randomUUID().toString(), "osv-cve", "lodash", "npm", Severity.CRITICAL,
                summary, Map.of(), Instant.parse("2026-09-12T12:00:00Z"));
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProducerKafkaConfiguration.class, ConsumerKafkaConfiguration.class})
    static class TestApplication {
        @Bean TestListeners testListeners(DependencyEventCodec codec) { return new TestListeners(codec); }
    }
    static class TestListeners {
        final LinkedBlockingQueue<DependencyEvent> dashboard = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<DependencyEvent> alerter = new LinkedBlockingQueue<>();
        private final DependencyEventCodec codec;
        TestListeners(DependencyEventCodec codec) { this.codec = codec; }
        @KafkaListener(topics = EventTopics.DEPENDENCY_EVENTS, groupId = "independent-dashboard")
        public void dashboard(String json) { dashboard.add(codec.read(json)); }
        @KafkaListener(topics = EventTopics.DEPENDENCY_EVENTS, groupId = "independent-alerter")
        public void alerter(String json) { alerter.add(codec.read(json)); }
    }
}
