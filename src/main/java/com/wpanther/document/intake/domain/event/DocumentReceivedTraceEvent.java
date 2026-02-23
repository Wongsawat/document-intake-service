package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is received by document-intake-service.
 * Published to topic: trace.document.received
 * <p>
 * This event marks the initial receipt of a document and is used for
 * distributed tracing across microservices.
 * <p>
 * Extends {@link TraceEvent} (non-sealed) as required by the sealed
 * {@code IntegrationEvent} hierarchy. Field mappings to {@code TraceEvent}:
 * {@code sagaId} ← {@code documentId} (before a saga exists, the document ID
 * serves as the correlation handle); {@code traceType} ← {@code status}
 * (the document lifecycle status IS the type of trace event).
 * {@code source} is inherited from {@code TraceEvent} — not redeclared here.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentReceivedTraceEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;

    /**
     * ID of IncomingDocument that was received.
     */
    @JsonProperty("documentId")
    @NotBlank(message = "Document ID is required")
    @Size(max = 100, message = "Document ID must not exceed 100 characters")
    private final String documentId;

    /**
     * Type of document (TAX_INVOICE, RECEIPT, INVOICE, etc.)
     */
    @JsonProperty("documentType")
    @NotBlank(message = "Document type is required")
    @Size(max = 50, message = "Document type must not exceed 50 characters")
    private final String documentType;

    /**
     * The document number from the document.
     */
    @JsonProperty("documentNumber")
    @NotBlank(message = "Document number is required")
    @Size(max = 50, message = "Document number must not exceed 50 characters")
    private final String documentNumber;

    /**
     * Correlation ID for tracing this request across all services.
     */
    @JsonProperty("correlationId")
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private final String correlationId;

    /**
     * Current status of document (RECEIVED, VALIDATED, etc.)
     */
    @JsonProperty("status")
    @NotBlank(message = "Status is required")
    @Size(max = 50, message = "Status must not exceed 50 characters")
    private final String status;

    // source is inherited from TraceEvent — not redeclared here.

    /**
     * Creation constructor used by the Lombok builder.
     * Maps: documentId → sagaId, status → traceType, source → TraceEvent.source.
     */
    @Builder
    private DocumentReceivedTraceEvent(
            String documentId,
            String documentType,
            String documentNumber,
            String correlationId,
            String status,
            String source) {
        super(documentId, source, status);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.correlationId = correlationId;
        this.status = status;
    }

    /**
     * Jackson deserialization factory. Always delegates to the builder (creation path)
     * so that {@code eventId} and {@code version} are always auto-generated, matching
     * the original {@code @Jacksonized} behaviour.
     */
    @JsonCreator
    static DocumentReceivedTraceEvent fromJson(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") String status,
            @JsonProperty("source") String source) {
        return DocumentReceivedTraceEvent.builder()
                .documentId(documentId)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .correlationId(correlationId)
                .status(status)
                .source(source)
                .build();
    }
}
