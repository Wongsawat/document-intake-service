package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Trace event for notification-service.
 * Published to topic: trace.document.received
 * <p>
 * This event provides visibility into the document intake process,
 * allowing notification-service to send status updates to stakeholders.
 */
@Getter
@Builder
public class DocumentReceivedTraceEvent {

    /**
     * Unique identifier for this trace event.
     */
    @JsonProperty("eventId")
    @Builder.Default
    private final UUID eventId = UUID.randomUUID();

    /**
     * Timestamp when this event was created.
     */
    @JsonProperty("occurredAt")
    @Builder.Default
    private final Instant occurredAt = Instant.now();

    /**
     * ID of the IncomingDocument.
     */
    @JsonProperty("documentId")
    private final String documentId;

    /**
     * Type of document (TAX_INVOICE, INVOICE, RECEIPT, etc.)
     */
    @JsonProperty("documentType")
    private final String documentType;

    /**
     * The invoice/tax invoice number from the document.
     */
    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    /**
     * Correlation ID for tracing the request across all services.
     */
    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Current status of the document (RECEIVED, VALIDATED, FORWARDED, etc.)
     */
    @JsonProperty("status")
    private final String status;

    /**
     * Source of the document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    private final String source;
}
