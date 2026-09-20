package com.dependencyhealth.contract;

public final class InvalidEventException extends IllegalArgumentException {
    public InvalidEventException(String message, Throwable cause) { super(message, cause); }
}
