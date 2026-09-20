package com.dependencyhealth.ingestor;

import com.dependencyhealth.contract.kafka.ConsumerKafkaConfiguration;
import com.dependencyhealth.contract.kafka.ProducerKafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties(GithubSbomProperties.class)
@Import({ProducerKafkaConfiguration.class, ConsumerKafkaConfiguration.class})
public class RepositoryIngestorApplication {
    public static void main(String[] args) { SpringApplication.run(RepositoryIngestorApplication.class, args); }
}
