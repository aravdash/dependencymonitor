package com.dependencyhealth.github;

import com.dependencyhealth.contract.http.ExternalApiConfiguration;
import com.dependencyhealth.contract.kafka.ProducerKafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({GithubScannerProperties.class, GithubWatchlist.class})
@Import({ProducerKafkaConfiguration.class, ExternalApiConfiguration.class})
public class GithubActivityScannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GithubActivityScannerApplication.class, args);
    }

    @Bean
    Clock scannerClock() {
        return Clock.systemUTC();
    }
}
