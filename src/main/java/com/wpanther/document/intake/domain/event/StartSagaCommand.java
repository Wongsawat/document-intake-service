package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import com.wpanther.saga.domain.model.IntegrationEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all information orchestrator needs to begin
 * orchestrating multi-step document processing pipeline.
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
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
     * The document number from document.
     */
    @JsonProperty("documentNumber")
    @NotBlank(message = "Document number is required")
    @Size(max = 50, message = "Document number must not exceed 50 characters")
    private final String documentNumber;

    /**
     * The full XML content of document.
     * This will be passed through pipeline for processing and signing.
     */
    @JsonProperty("xmlContent")
    @NotBlank(message = "XML content is required")
    @Size(max = 10485760, message = "XML content must not exceed 10MB")
    private final String xmlContent;

    /**
     * Correlation ID for tracing request across all services.
     */
    @JsonProperty("correlationId")
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private final String correlationId;

    /**
     * Source of document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private final String source;
}
