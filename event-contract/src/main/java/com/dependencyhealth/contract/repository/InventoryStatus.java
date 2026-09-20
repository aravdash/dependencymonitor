package com.dependencyhealth.contract.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum InventoryStatus {
    COMPLETE, PARTIAL, FAILED;
    @JsonValue public String value() { return name().toLowerCase(Locale.ROOT); }
    @JsonCreator public static InventoryStatus fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("status is required");
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
