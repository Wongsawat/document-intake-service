package com.wpanther.document.intake.domain.model;

/**
 * Enum representing the status of an incoming document
 */
public enum DocumentStatus {
    /**
     * Document has been received
     */
    RECEIVED,

    /**
     * Document is being validated
     */
    VALIDATING,

    /**
     * Document validation passed
     */
    VALIDATED,

    /**
     * Document validation failed
     */
    INVALID,

    /**
     * Document forwarded to processing service
     */
    FORWARDED,

    /**
     * Document processing failed
     */
    FAILED
}
