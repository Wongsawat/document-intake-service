package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

/**
 * Trace event for notification-service.
 * Published to topic: trace.document.received
 * <p>
 * This event provides visibility into document intake process,
 * allowing notification-service to send status updates to stakeholders.
 */
@Getter
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentReceivedTraceEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;
    private static final String DOCUMENT_TYPE_PATTERN =
        "TAX_INVOICE|RECEIPT|INVOICE|DEBIT_CREDIT_NOTE|CANCELLATION_NOTE|ABBREVIATED_TAX_INVOICE";
    private static final String STATUS_PATTERN =
        "RECEIVED|VALIDATING|VALIDATED|FORWARDED|INVALID|FAILED";

    /**
     * ID of IncomingDocument.
     */
    @JsonProperty("documentId")
    @NotBlank(message = "Document ID is required")
    @Size(max = 100, message = "Document ID must not exceed 100 characters")
    private final String documentId;

    /**
     * Type of document (TAX_INVOICE, INVOICE, RECEIPT, etc.)
     */
    @JsonProperty("documentType")
    @NotBlank(message = "Document type is required")
    @Pattern(regexp = DOCUMENT_TYPE_PATTERN, message = "Invalid document type")
    private final String documentType;

    /**
     * The document number from the document.
     */
    @JsonProperty("documentNumber")
    @NotBlank(message = "Document number is required")
    @Size(max = 50, message = "Document number must not exceed 50 characters")
    private final String documentNumber;

    /**
     * Correlation ID for tracing the request across all services.
     */
    @JsonProperty("correlationId")
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private final String correlationId;

    /**
     * Current status of document (RECEIVED, VALIDATED, FORWARDED, etc.)
     */
    @JsonProperty("status")
    @NotBlank(message = "Status is required")
    @Pattern(regexp = STATUS_PATTERN, message = "Invalid status")
    private final String status;

    /**
     * Source of the document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private final String source;

    /**
     * Full constructor for Builder.
     * Note: eventId, occurredAt, eventType, and version are set by IntegrationEvent base class.
     */
    @Builder
    private DocumentReceivedTraceEvent(String documentId, String documentType, String documentNumber,
                                       String correlationId, String status, String source) {
        super();
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.correlationId = correlationId;
        this.status = status;
        this.source = source;
    }

    /**
     * Constructor for Jackson deserialization - includes all fields from parent class.
     * Used when consuming from Kafka.
     */
    @JsonCreator
    private DocumentReceivedTraceEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") String status,
            @JsonProperty("source") String source) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.correlationId = correlationId;
        this.status = status;
        this.source = source;
    }
}
