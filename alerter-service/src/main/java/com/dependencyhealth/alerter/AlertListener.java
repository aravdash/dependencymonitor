package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.EventTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertListener {
    private final DependencyEventCodec codec;
    private final AlertProperties properties;
    private final WebhookAlertSender sender;

    public AlertListener(DependencyEventCodec codec, AlertProperties properties, WebhookAlertSender sender) {
        this.codec = codec;
        this.properties = properties;
        this.sender = sender;
    }

    @KafkaListener(topics = EventTopics.DEPENDENCY_EVENTS)
    public void onEvent(String payload) {
        DependencyEvent event = codec.read(payload);
        if (event.severity().atLeast(properties.threshold())) {
            // Propagate delivery failures so Kafka retries before committing the record.
            sender.send(event);
        }
    }
}
