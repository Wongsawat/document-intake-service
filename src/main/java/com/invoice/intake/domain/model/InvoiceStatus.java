package com.invoice.intake.domain.model;

/**
 * Enum representing the status of an incoming invoice
 */
public enum InvoiceStatus {
    /**
     * Invoice has been received
     */
    RECEIVED,

    /**
     * Invoice is being validated
     */
    VALIDATING,

    /**
     * Invoice validation passed
     */
    VALIDATED,

    /**
     * Invoice validation failed
     */
    INVALID,

    /**
     * Invoice forwarded to processing service
     */
    FORWARDED,

    /**
     * Invoice processing failed
     */
    FAILED
}
