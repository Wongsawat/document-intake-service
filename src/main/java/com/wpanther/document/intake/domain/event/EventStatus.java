package com.wpanther.document.intake.domain.event;

/**
 * Enum representing the status of a document in the trace event system.
 * Used by DocumentReceivedTraceEvent for tracking document lifecycle.
 */
public enum EventStatus {
    /**
     * Document has been received but not yet validated.
     */
    RECEIVED("RECEIVED"),

    /**
     * Document is currently being validated.
     */
    VALIDATING("VALIDATING"),

    /**
     * Document has passed all validations.
     */
    VALIDATED("VALIDATED"),

    /**
     * Document has been forwarded to downstream services.
     */
    FORWARDED("FORWARDED"),

    /**
     * Document failed validation.
     */
    INVALID("INVALID"),

    /**
     * Document processing failed due to system error.
     */
    FAILED("FAILED");

    private final String value;

    EventStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Get EventStatus from string value.
     *
     * @param value the string value
     * @return the matching EventStatus, or null if not found
     */
    public static EventStatus fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (EventStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }

        return null;
    }
}
