package com.wpanther.document.intake.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing an incoming document
 *
 * This aggregate encapsulates the lifecycle of document intake including
 * validation, status tracking, and forwarding to processing service.
 */
public class IncomingDocument {

    // Identity
    private final UUID id;

    // Document Data
    private final String documentNumber;
    private final String xmlContent;
    private final String source;
    private final String correlationId;
    private final DocumentType documentType;

    // Validation
    private DocumentStatus status;
    private ValidationResult validationResult;

    // Timestamps
    private final Instant receivedAt;
    private Instant processedAt;

    private IncomingDocument(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.documentNumber = Objects.requireNonNull(builder.documentNumber, "Document number is required");
        this.xmlContent = Objects.requireNonNull(builder.xmlContent, "XML content is required");
        this.source = Objects.requireNonNull(builder.source, "Source is required");
        this.correlationId = builder.correlationId;
        this.documentType = builder.documentType;
        this.status = builder.status != null ? builder.status : DocumentStatus.RECEIVED;
        this.validationResult = builder.validationResult;
        this.receivedAt = builder.receivedAt != null ? builder.receivedAt : Instant.now();
        this.processedAt = builder.processedAt;

        validateInvariant();
    }

    /**
     * Validate business invariants
     */
    private void validateInvariant() {
        if (documentNumber.isBlank()) {
            throw new IllegalStateException(
                "Document number cannot be blank. This is a validation invariant violation. " +
                "Ensure document number is provided during document creation."
            );
        }

        if (xmlContent.isBlank()) {
            throw new IllegalStateException(
                "XML content cannot be blank. This is a validation invariant violation. " +
                "Ensure XML content is provided during document creation."
            );
        }
    }

    /**
     * Start validation
     */
    public void startValidation() {
        if (this.status != DocumentStatus.RECEIVED) {
            throw new IllegalStateException(
                "Can only start validation from RECEIVED status. Current status: " + this.status + ". " +
                "Document must be in RECEIVED state to start validation process."
            );
        }
        this.status = DocumentStatus.VALIDATING;
    }

    /**
     * Mark validation result
     */
    public void markValidated(ValidationResult result) {
        if (this.status != DocumentStatus.VALIDATING) {
            throw new IllegalStateException(
                "Can only mark validated from VALIDATING status. Current status: " + this.status + ". " +
                "Document must be in VALIDATING state to be marked as validated."
            );
        }

        Objects.requireNonNull(result, "Validation result is required");
        this.validationResult = result;
        this.status = result.valid() ? DocumentStatus.VALIDATED : DocumentStatus.INVALID;
    }

    /**
     * Mark as forwarded to processing service
     */
    public void markForwarded() {
        if (this.status != DocumentStatus.VALIDATED) {
            throw new IllegalStateException(
                "Can only forward validated documents. Current status: " + this.status + ". " +
                "Document must be in VALIDATED state to be forwarded."
            );
        }
        this.status = DocumentStatus.FORWARDED;
        this.processedAt = Instant.now();
    }

    /**
     * Mark as failed
     */
    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.processedAt = Instant.now();

        if (this.validationResult == null) {
            this.validationResult = ValidationResult.invalid(List.of(errorMessage));
        }
    }

    /**
     * Check if document is valid
     */
    public boolean isValid() {
        return validationResult != null && validationResult.valid();
    }

    /**
     * Check if document can be forwarded
     */
    public boolean canBeForwarded() {
        return status == DocumentStatus.VALIDATED && isValid();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public String getSource() {
        return source;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    /**
     * Builder for IncomingDocument
     */
    public static class Builder {
        private UUID id;
        private String documentNumber;
        private String xmlContent;
        private String source;
        private String correlationId;
        private DocumentType documentType;
        private DocumentStatus status;
        private ValidationResult validationResult;
        private Instant receivedAt;
        private Instant processedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder documentNumber(String documentNumber) {
            this.documentNumber = documentNumber;
            return this;
        }

        public Builder xmlContent(String xmlContent) {
            this.xmlContent = xmlContent;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder documentType(DocumentType documentType) {
            this.documentType = documentType;
            return this;
        }

        public Builder status(DocumentStatus status) {
            this.status = status;
            return this;
        }

        public Builder validationResult(ValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public Builder receivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public IncomingDocument build() {
            return new IncomingDocument(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
