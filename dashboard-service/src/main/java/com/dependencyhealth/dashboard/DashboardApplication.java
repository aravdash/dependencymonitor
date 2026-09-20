package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.kafka.ConsumerKafkaConfiguration;
import com.dependencyhealth.contract.kafka.ProducerKafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ConsumerKafkaConfiguration.class, ProducerKafkaConfiguration.class})
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }
}
