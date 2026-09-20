package com.dependencyhealth.contract.kafka;

import com.dependencyhealth.contract.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@EnableKafka
@Configuration(proxyBeanMethods = false)
@Import(KafkaInfrastructureConfiguration.class)
public class ConsumerKafkaConfiguration {
    @Bean public ConsumerFactory<String, String> consumerFactory(KafkaProperties properties, ObjectProvider<SslBundles> sslBundles) {
        var config = properties.buildConsumerProperties(sslBundles.getIfAvailable());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Bound a poll's work below max.poll.interval even with slow webhooks and retries.
        config.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean public DefaultErrorHandler dependencyErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        // Never commit a failed source record if publishing it to the DLT also fails.
        recoverer.setFailIfSendResultIsError(true);
        var backoff = new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(1000L); backoff.setMultiplier(2); backoff.setMaxInterval(5000L);
        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    @Bean public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> factory, DefaultErrorHandler errorHandler,
            @Value("${spring.kafka.listener.concurrency:1}") int concurrency) {
        var container = new ConcurrentKafkaListenerContainerFactory<String, String>();
        container.setConsumerFactory(factory);
        container.setConcurrency(concurrency);
        container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        container.setCommonErrorHandler(errorHandler);
        return container;
    }
}
