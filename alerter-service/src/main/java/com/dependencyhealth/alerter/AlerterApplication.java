package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.kafka.ConsumerKafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ConsumerKafkaConfiguration.class)
@EnableConfigurationProperties(AlertProperties.class)
public class AlerterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlerterApplication.class, args);
    }
}
