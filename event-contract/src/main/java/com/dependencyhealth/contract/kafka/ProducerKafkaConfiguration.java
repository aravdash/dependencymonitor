package com.dependencyhealth.contract.kafka;

import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.repository.RepositoryMessageCodec;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
@Import(KafkaInfrastructureConfiguration.class)
public class ProducerKafkaConfiguration {
    @Bean public EventPublisher eventPublisher(KafkaTemplate<String, String> template, DependencyEventCodec codec,
                                              @Value("${health.kafka.send-timeout:35s}") Duration timeout) {
        return new EventPublisher(template, codec, timeout);
    }
    @Bean public RepositoryMessagePublisher repositoryMessagePublisher(KafkaTemplate<String, String> template,
            RepositoryMessageCodec codec, @Value("${health.kafka.send-timeout:35s}") Duration timeout) {
        return new RepositoryMessagePublisher(template, codec, timeout);
    }
}
