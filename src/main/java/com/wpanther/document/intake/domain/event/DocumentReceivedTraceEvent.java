package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import com.wpanther.saga.domain.model.IntegrationEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is received by document-intake-service.
 * Published to topic: document.events.trace
 * <p>
 * This event marks the initial receipt of a document and is used for
 * distributed tracing across microservices.
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentReceivedTraceEvent extends IntegrationEvent {

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

    /**
     * Source of document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private final String source;
}
