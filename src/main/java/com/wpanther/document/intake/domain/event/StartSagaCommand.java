package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all information orchestrator needs to begin
 * orchestrating multi-step document processing pipeline.
 * <p>
 * Extends {@link SagaCommand} (non-sealed) as required by the sealed
 * {@code IntegrationEvent} hierarchy. {@code sagaId} and {@code sagaStep}
 * are {@code null} on creation because the saga has not started yet — the
 * orchestrator-service assigns them when it creates the saga instance.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartSagaCommand extends SagaCommand {

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

    // correlationId is inherited from SagaCommand — not redeclared here.

    /**
     * Source of document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private final String source;

    /**
     * Creation constructor used by the Lombok builder.
     * sagaId and sagaStep are {@code null} because the saga has not started yet.
     */
    @Builder
    private StartSagaCommand(
            String documentId,
            String documentType,
            String documentNumber,
            String xmlContent,
            String correlationId,
            String source) {
        super(null, null, correlationId);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.source = source;
    }

    /**
     * Jackson deserialization factory. Always delegates to the builder (creation path)
     * so that {@code eventId} and {@code version} are always auto-generated, matching
     * the original {@code @Jacksonized} behaviour. Saga-infrastructure fields
     * ({@code sagaId}, {@code sagaStep}) present in JSON are intentionally ignored
     * since they are assigned by the orchestrator, not by this service.
     */
    @JsonCreator
    static StartSagaCommand fromJson(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("source") String source) {
        return StartSagaCommand.builder()
                .documentId(documentId)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .xmlContent(xmlContent)
                .correlationId(correlationId)
                .source(source)
                .build();
    }
}
