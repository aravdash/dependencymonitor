package com.dependencyhealth.contract.kafka;

import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.EventTopics;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaInfrastructureConfiguration {
    @Bean public DependencyEventCodec dependencyEventCodec() { return new DependencyEventCodec(); }
    @Bean public RepositoryMessageCodec repositoryMessageCodec(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new RepositoryMessageCodec(mapper);
    }

    @Bean public ProducerFactory<String, String> producerFactory(KafkaProperties properties, ObjectProvider<SslBundles> sslBundles) {
        var config = properties.buildProducerProperties(sslBundles.getIfAvailable());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
        config.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        config.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean public KafkaAdmin.NewTopics dependencyTopics(@Value("${health.kafka.partitions:3}") int partitions,
                                                       @Value("${health.kafka.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(EventTopics.DEPENDENCY_EVENTS).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(EventTopics.DEAD_LETTER).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(EventTopics.REPOSITORY_SCAN_REQUESTS).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(EventTopics.REPOSITORY_SCAN_REQUESTS + ".DLT").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(EventTopics.REPOSITORY_INVENTORY).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(EventTopics.REPOSITORY_INVENTORY + ".DLT").partitions(partitions).replicas(replicas).build());
    }
}
