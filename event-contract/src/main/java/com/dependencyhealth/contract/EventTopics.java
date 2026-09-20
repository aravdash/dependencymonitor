package com.dependencyhealth.contract;

public final class EventTopics {
    public static final String DEPENDENCY_EVENTS = "dependency-events";
    public static final String DEAD_LETTER = DEPENDENCY_EVENTS + ".DLT";
    public static final String REPOSITORY_SCAN_REQUESTS = "repository-scan-requests";
    public static final String REPOSITORY_INVENTORY = "repository-inventory";
    private EventTopics() { }
}
