package com.dependencyhealth.license;

import com.dependencyhealth.contract.http.ExternalApiConfiguration;
import com.dependencyhealth.contract.kafka.ConsumerKafkaConfiguration;
import com.dependencyhealth.contract.kafka.ProducerKafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Import({ProducerKafkaConfiguration.class, ConsumerKafkaConfiguration.class, ExternalApiConfiguration.class})
public class LicenseCheckerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LicenseCheckerApplication.class, args);
    }
}
