package com.dependencyhealth.contract;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DependencyEventCodecTest {
    private final DependencyEventCodec codec = new DependencyEventCodec();
    @Test void roundTripsPlainJsonWithoutJavaTypeHeaders() {
        var event = new DependencyEvent(UUID.randomUUID().toString(), "osv-cve", "lodash", "npm",
                Severity.CRITICAL, "Vulnerability found", Map.of("ids", java.util.List.of("GHSA-example")),
                Instant.parse("2026-09-12T12:00:00Z"));
        String json = codec.write(event);
        assertThat(json).contains("\"severity\":\"critical\"", "\"timestamp\":\"2026-09-12T12:00:00Z\"")
                .doesNotContain("@class", "__TypeId__");
        assertThat(codec.read(json)).isEqualTo(event);
        assertThat(codec.read(json.substring(0, json.length() - 1) + ",\"newField\":true}")).isEqualTo(event);
    }
    @Test void rejectsPoisonMessagesBeforeConsumersProduceSideEffects() {
        for (String json : java.util.List.of("null", "{}", "not json", "{\"severity\":\"urgent\"}"))
            assertThatThrownBy(() -> codec.read(json)).isInstanceOf(InvalidEventException.class);
    }
    @Test void rejectsMissingFieldsBadIdsUnknownSeverityAndTrailingJson() {
        var valid = new DependencyEvent(UUID.randomUUID().toString(), "license-check", "flask", "pypi",
                Severity.WARNING, "Unknown license", Map.of(), Instant.now());
        String json = codec.write(valid);
        assertThatThrownBy(() -> codec.read(json.replace(valid.eventId(), "1-1-1-1-1"))).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.read(json.replace("\"warning\"", "\"CRITICAL\""))).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.read(json + " {}")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.read(json.replace("\"detail\":{}", "\"detail\":null"))).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.read(json.replace("\"flask\"", "123"))).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.read(json.replace("\"" + valid.timestamp() + "\"", "1700000000")))
                .isInstanceOf(InvalidEventException.class);
    }
}
