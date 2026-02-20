package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
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
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all the information the orchestrator needs to begin
 * orchestrating the multi-step document processing pipeline.
 */
@Getter
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartSagaCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;
    private static final String DOCUMENT_TYPE_PATTERN =
        "TAX_INVOICE|RECEIPT|INVOICE|DEBIT_CREDIT_NOTE|CANCELLATION_NOTE|ABBREVIATED_TAX_INVOICE";

    /**
     * ID of IncomingDocument that triggered this saga.
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
     * The full XML content of the document.
     * This will be passed through the pipeline for processing and signing.
     */
    @JsonProperty("xmlContent")
    @NotBlank(message = "XML content is required")
    @Size(max = 10485760, message = "XML content must not exceed 10MB")
    private final String xmlContent;

    /**
     * Correlation ID for tracing the request across all services.
     */
    @JsonProperty("correlationId")
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private final String correlationId;

    /**
     * Source of the document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private final String source;

    /**
     * Constructor for Builder pattern - used when creating new instances.
     * Calls super() to auto-generate eventId, occurredAt, eventType, version.
     */
    @Builder
    private StartSagaCommand(String documentId, String documentType, String documentNumber,
                             String xmlContent, String correlationId, String source) {
        super();
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
        this.source = source;
    }

    /**
     * Constructor for Jackson deserialization - includes all fields from parent class.
     * Used when consuming from Kafka.
     */
    @JsonCreator
    private StartSagaCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("source") String source) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
        this.source = source;
    }
}
