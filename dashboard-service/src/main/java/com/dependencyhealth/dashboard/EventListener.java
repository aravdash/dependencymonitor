package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.EventTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventListener {
    private static final Logger log = LoggerFactory.getLogger(EventListener.class);
    private final DependencyEventCodec codec;
    private final EventRepository repository;

    public EventListener(DependencyEventCodec codec, EventRepository repository) {
        this.codec = codec;
        this.repository = repository;
    }

    @KafkaListener(topics = EventTopics.DEPENDENCY_EVENTS)
    public void onEvent(String payload) {
        DependencyEvent event = codec.read(payload);
        boolean inserted = repository.save(event);
        log.debug("Event {} {}", event.eventId(), inserted ? "persisted" : "already persisted");
        // Let decode/database failures reach the Kafka error handler. The offset is committed
        // only after the insert completes (or the failed record is safely sent to the DLT).
    }
}
