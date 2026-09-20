package com.dependencyhealth.contract.repository;

import com.dependencyhealth.contract.InvalidEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class RepositoryMessageCodec {
    private final ObjectMapper mapper;
    public RepositoryMessageCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new InvalidEventException("Cannot encode repository message", ex); }
    }
    public RepositoryScanRequest readRequest(String json) { return read(json, RepositoryScanRequest.class); }
    public RepositoryInventoryEvent readInventory(String json) { return read(json, RepositoryInventoryEvent.class); }
    private <T> T read(String json, Class<T> type) {
        try {
            var tree = mapper.readTree(json);
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException("Message must be an object");
            return mapper.treeToValue(tree, type);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidEventException("Invalid repository message JSON", ex);
        }
    }
}
