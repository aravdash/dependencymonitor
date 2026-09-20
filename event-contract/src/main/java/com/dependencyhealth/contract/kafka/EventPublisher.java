package com.dependencyhealth.contract.kafka;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.EventTopics;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

/** A completed call means Kafka acknowledged the record; errors reach the scanner's poll boundary. */
public class EventPublisher {
    private final KafkaTemplate<String, String> template;
    private final DependencyEventCodec codec;
    private final Duration timeout;

    public EventPublisher(KafkaTemplate<String, String> template, DependencyEventCodec codec, Duration timeout) {
        this.template = template; this.codec = codec; this.timeout = timeout;
    }
    public void publish(DependencyEvent event) {
        try {
            template.send(EventTopics.DEPENDENCY_EVENTS, event.partitionKey(), codec.write(event))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Kafka did not acknowledge event " + event.eventId(), ex);
        }
    }
}
