package com.dependencyhealth.contract.http;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExternalApiConfiguration {
    @Bean public ExternalApiClient externalApiClient(@Value("${health.http.connect-timeout:5s}") Duration connectTimeout,
                                                    @Value("${health.http.request-timeout:15s}") Duration requestTimeout,
                                                    @Value("${health.http.max-attempts:3}") int attempts) {
        return new ExternalApiClient(HttpClient.newBuilder().connectTimeout(connectTimeout).build(),
                JsonMapper.builder().build(), requestTimeout, attempts);
    }
}
