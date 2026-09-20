package com.dependencyhealth.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Severity {
    INFO, WARNING, CRITICAL;

    public int rank() { return ordinal(); }
    public boolean atLeast(Severity threshold) { return rank() >= threshold.rank(); }
    @JsonValue public String value() { return name().toLowerCase(Locale.ROOT); }
    @JsonCreator public static Severity fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("severity is required");
        for (Severity severity : values()) {
            if (severity.value().equals(value)) return severity;
        }
        throw new IllegalArgumentException("Invalid severity: " + value);
    }
}
