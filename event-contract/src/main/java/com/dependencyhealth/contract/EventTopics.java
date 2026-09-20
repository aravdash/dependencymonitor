package com.dependencyhealth.contract;

public final class EventTopics {
    public static final String DEPENDENCY_EVENTS = "dependency-events";
    public static final String DEAD_LETTER = DEPENDENCY_EVENTS + ".DLT";
    private EventTopics() { }
}
