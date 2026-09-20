package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.DependencyEvent;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WebhookAlertSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookAlertSender.class);
    private final RestClient client;
    private final AlertProperties properties;

    public WebhookAlertSender(RestClient client, AlertProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @PostConstruct
    void describeMode() {
        if (properties.webhookUrl().isBlank()) {
            log.warn("ALERT_WEBHOOK_URL is unset; qualifying alerts will be logged. Threshold: {}", properties.threshold());
        }
    }

    public void send(DependencyEvent event) {
        String message = "Dependency health: " + event.severity().name() + "\n"
                + "Package: " + event.packageName() + " (" + event.ecosystem() + ")\n"
                + "Scanner: " + event.source() + "\n"
                + event.summary() + "\n"
                + "Observed: " + event.timestamp() + "\n"
                + "Event: " + event.eventId();
        if (properties.webhookUrl().isBlank()) {
            log.warn("DEPENDENCY ALERT {}", message);
            return;
        }
        try {
            ResponseEntity<Void> response = client.post().uri(properties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message, "mrkdwn", false, "unfurl_links", false, "unfurl_media", false))
                    .retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Alert webhook returned HTTP " + response.getStatusCode().value());
            }
            log.info("Alert delivered for event {}", event.eventId());
        } catch (RestClientException exception) {
            // HTTP client exception messages may contain the secret webhook URL or body.
            // The Kafka error handler will retry using this redacted failure message.
            throw new IllegalStateException("Alert webhook delivery failed: " + exception.getClass().getSimpleName());
        }
    }
}
