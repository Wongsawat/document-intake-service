package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Trace event for notification-service.
 * Published to topic: trace.document.received
 * <p>
 * This event provides visibility into the document intake process,
 * allowing notification-service to send status updates to stakeholders.
 */
@Getter
@Builder
@Jacksonized  // Enable Jackson builder deserialization
public class DocumentReceivedTraceEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

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

    /**
     * Constructor for creating a new DocumentReceivedTraceEvent.
     * Initializes the base IntegrationEvent fields.
     */
    public DocumentReceivedTraceEvent() {
        super();
        this.documentId = null;
        this.documentType = null;
        this.invoiceNumber = null;
        this.correlationId = null;
        this.status = null;
        this.source = null;
    }

    /**
     * Full constructor for Builder.
     * Note: eventId, occurredAt, eventType, and version are set by IntegrationEvent base class.
     */
    @Builder
    private DocumentReceivedTraceEvent(String documentId, String documentType, String invoiceNumber,
                                       String correlationId, String status, String source) {
        super();
        this.documentId = documentId;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.correlationId = correlationId;
        this.status = status;
        this.source = source;
    }
}
