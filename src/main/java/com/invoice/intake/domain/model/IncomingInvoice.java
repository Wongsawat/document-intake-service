package com.invoice.intake.domain.model;

import com.invoice.intake.infrastructure.validation.DocumentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing an incoming invoice
 *
 * This aggregate encapsulates the lifecycle of invoice intake including
 * validation, status tracking, and forwarding to processing service.
 */
public class IncomingInvoice {

    // Identity
    private final UUID id;

    // Invoice Data
    private final String invoiceNumber;
    private final String xmlContent;
    private final String source;
    private final String correlationId;
    private final DocumentType documentType;

    // Validation
    private InvoiceStatus status;
    private ValidationResult validationResult;

    // Timestamps
    private final LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    private IncomingInvoice(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.invoiceNumber = Objects.requireNonNull(builder.invoiceNumber, "Invoice number is required");
        this.xmlContent = Objects.requireNonNull(builder.xmlContent, "XML content is required");
        this.source = Objects.requireNonNull(builder.source, "Source is required");
        this.correlationId = builder.correlationId;
        this.documentType = builder.documentType;
        this.status = builder.status != null ? builder.status : InvoiceStatus.RECEIVED;
        this.validationResult = builder.validationResult;
        this.receivedAt = builder.receivedAt != null ? builder.receivedAt : LocalDateTime.now();
        this.processedAt = builder.processedAt;

        validateInvariant();
    }

    /**
     * Validate business invariants
     */
    private void validateInvariant() {
        if (invoiceNumber.isBlank()) {
            throw new IllegalStateException("Invoice number cannot be blank");
        }

        if (xmlContent.isBlank()) {
            throw new IllegalStateException("XML content cannot be blank");
        }
    }

    /**
     * Start validation
     */
    public void startValidation() {
        if (this.status != InvoiceStatus.RECEIVED) {
            throw new IllegalStateException("Can only start validation from RECEIVED status");
        }
        this.status = InvoiceStatus.VALIDATING;
    }

    /**
     * Mark validation result
     */
    public void markValidated(ValidationResult result) {
        if (this.status != InvoiceStatus.VALIDATING) {
            throw new IllegalStateException("Can only mark validated from VALIDATING status");
        }

        Objects.requireNonNull(result, "Validation result is required");
        this.validationResult = result;
        this.status = result.valid() ? InvoiceStatus.VALIDATED : InvoiceStatus.INVALID;
    }

    /**
     * Mark as forwarded to processing service
     */
    public void markForwarded() {
        if (this.status != InvoiceStatus.VALIDATED) {
            throw new IllegalStateException("Can only forward validated invoices");
        }
        this.status = InvoiceStatus.FORWARDED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Mark as failed
     */
    public void markFailed(String errorMessage) {
        this.status = InvoiceStatus.FAILED;
        this.processedAt = LocalDateTime.now();

        if (this.validationResult == null) {
            this.validationResult = ValidationResult.invalid(List.of(errorMessage));
        }
    }

    /**
     * Check if invoice is valid
     */
    public boolean isValid() {
        return validationResult != null && validationResult.valid();
    }

    /**
     * Check if invoice can be forwarded
     */
    public boolean canBeForwarded() {
        return status == InvoiceStatus.VALIDATED && isValid();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
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

    public InvoiceStatus getStatus() {
        return status;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    /**
     * Builder for IncomingInvoice
     */
    public static class Builder {
        private UUID id;
        private String invoiceNumber;
        private String xmlContent;
        private String source;
        private String correlationId;
        private DocumentType documentType;
        private InvoiceStatus status;
        private ValidationResult validationResult;
        private LocalDateTime receivedAt;
        private LocalDateTime processedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
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

        public Builder status(InvoiceStatus status) {
            this.status = status;
            return this;
        }

        public Builder validationResult(ValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public Builder receivedAt(LocalDateTime receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        public Builder processedAt(LocalDateTime processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public IncomingInvoice build() {
            return new IncomingInvoice(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
