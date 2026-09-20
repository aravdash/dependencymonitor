package com.dependencyhealth.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class DependencyEventCodec {
    private final ObjectMapper mapper;
    public DependencyEventCodec() { this(JsonMapper.builder().addModule(new JavaTimeModule()).build()); }
    public DependencyEventCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public DependencyEvent read(String json) {
        try {
            var tree = mapper.readTree(json);
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException("Event must be an object");
            for (String field : java.util.List.of("eventId", "source", "packageName", "ecosystem", "severity", "summary", "timestamp")) {
                if (!tree.path(field).isTextual()) throw new IllegalArgumentException(field + " must be a string");
            }
            if (!tree.path("detail").isObject()) throw new IllegalArgumentException("detail must be an object");
            DependencyEvent event = mapper.treeToValue(tree, DependencyEvent.class);
            if (event == null) throw new IllegalArgumentException("Event cannot be null");
            return event;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidEventException("Invalid dependency event JSON", ex);
        }
    }
    public String write(DependencyEvent event) {
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        try { return mapper.writeValueAsString(event); }
        catch (JsonProcessingException ex) { throw new InvalidEventException("Cannot encode event", ex); }
    }
}
