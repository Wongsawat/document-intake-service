package com.wpanther.document.intake.infrastructure.persistence;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity for IncomingDocument aggregate
 */
@Entity
@Table(name = "incoming_invoices", indexes = {
    @Index(name = "idx_incoming_invoice_number", columnList = "invoice_number"),
    @Index(name = "idx_incoming_status", columnList = "status"),
    @Index(name = "idx_incoming_received", columnList = "received_at"),
    @Index(name = "idx_incoming_document_type", columnList = "document_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingDocumentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "xml_content", nullable = false, columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result")
    private Map<String, Object> validationResult;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = DocumentStatus.RECEIVED;
        }
    }
}
