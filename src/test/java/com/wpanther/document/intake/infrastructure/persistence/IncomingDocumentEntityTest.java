package com.wpanther.document.intake.infrastructure.persistence;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IncomingDocumentEntity JPA entity
 * Tests entity lifecycle, JPA annotations, and field mappings
 */
@DisplayName("IncomingDocument Entity Tests")
class IncomingDocumentEntityTest {

    private IncomingDocumentEntity entity;

    @BeforeEach
    void setUp() {
        entity = IncomingDocumentEntity.builder()
            .invoiceNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId("corr-123")
            .documentType(DocumentType.TAX_INVOICE)
            .build();
    }

    // ==================== @PrePersist Lifecycle Tests ====================

    @Test
    @DisplayName("@PrePersist generates UUID when id is null")
    void testPrePersistGeneratesUuid() {
        assertThat(entity.getId()).isNull();

        entity.onCreate();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getId()).isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("@PrePersist keeps existing UUID when id is set")
    void testPrePersistKeepsExistingUuid() {
        UUID existingId = UUID.randomUUID();
        entity.setId(existingId);

        entity.onCreate();

        assertThat(entity.getId()).isEqualTo(existingId);
    }

    @Test
    @DisplayName("@PrePersist sets default status to RECEIVED when null")
    void testPrePersistSetsDefaultStatus() {
        assertThat(entity.getStatus()).isNull();

        entity.onCreate();

        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
    }

    @Test
    @DisplayName("@PrePersist keeps existing status when set")
    void testPrePersistKeepsExistingStatus() {
        entity.setStatus(DocumentStatus.VALIDATED);

        entity.onCreate();

        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
    }

    // ==================== Builder Pattern Tests ====================

    @Test
    @DisplayName("Builder creates entity with all fields")
    void testBuilderCreatesEntityWithAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("valid", true);

        IncomingDocumentEntity fullEntity = IncomingDocumentEntity.builder()
            .id(id)
            .invoiceNumber("INV-2024-002")
            .xmlContent("<xml>content</xml>")
            .source("KAFKA")
            .correlationId("corr-456")
            .documentType(DocumentType.RECEIPT)
            .status(DocumentStatus.VALIDATED)
            .validationResult(validationResult)
            .receivedAt(now)
            .processedAt(now.plusMinutes(5))
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(fullEntity.getId()).isEqualTo(id);
        assertThat(fullEntity.getInvoiceNumber()).isEqualTo("INV-2024-002");
        assertThat(fullEntity.getXmlContent()).isEqualTo("<xml>content</xml>");
        assertThat(fullEntity.getSource()).isEqualTo("KAFKA");
        assertThat(fullEntity.getCorrelationId()).isEqualTo("corr-456");
        assertThat(fullEntity.getDocumentType()).isEqualTo(DocumentType.RECEIPT);
        assertThat(fullEntity.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
        assertThat(fullEntity.getValidationResult()).isEqualTo(validationResult);
        assertThat(fullEntity.getReceivedAt()).isEqualTo(now);
        assertThat(fullEntity.getProcessedAt()).isEqualTo(now.plusMinutes(5));
        assertThat(fullEntity.getCreatedAt()).isEqualTo(now);
        assertThat(fullEntity.getUpdatedAt()).isEqualTo(now);
    }

    // ==================== Enum Persistence Tests ====================

    @Test
    @DisplayName("Entity stores DocumentType enum correctly")
    void testDocumentTypeEnum() {
        for (DocumentType type : DocumentType.values()) {
            IncomingDocumentEntity testEntity = IncomingDocumentEntity.builder()
                .invoiceNumber("INV-" + type.name())
                .xmlContent("<test/>")
                .source("TEST")
                .documentType(type)
                .build();

            assertThat(testEntity.getDocumentType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Entity stores DocumentStatus enum correctly")
    void testDocumentStatusEnum() {
        for (DocumentStatus status : DocumentStatus.values()) {
            IncomingDocumentEntity testEntity = IncomingDocumentEntity.builder()
                .invoiceNumber("INV-" + status.name())
                .xmlContent("<test/>")
                .source("TEST")
                .status(status)
                .build();

            assertThat(testEntity.getStatus()).isEqualTo(status);
        }
    }

    // ==================== JSONB Field Tests ====================

    @Test
    @DisplayName("Entity stores validationResult as JSONB map")
    void testValidationResultJsonb() {
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("valid", true);
        validationResult.put("errors", new String[]{});
        validationResult.put("warnings", new String[]{"Warning 1"});

        entity.setValidationResult(validationResult);

        assertThat(entity.getValidationResult()).isNotNull();
        assertThat(entity.getValidationResult().get("valid")).isEqualTo(true);
        assertThat(entity.getValidationResult().get("warnings")).isInstanceOf(String[].class);
    }

    @Test
    @DisplayName("Entity handles null validationResult")
    void testNullValidationResult() {
        entity.setValidationResult(null);

        assertThat(entity.getValidationResult()).isNull();
    }

    @Test
    @DisplayName("Entity handles empty validationResult")
    void testEmptyValidationResult() {
        Map<String, Object> emptyResult = new HashMap<>();
        entity.setValidationResult(emptyResult);

        assertThat(entity.getValidationResult()).isNotNull();
        assertThat(entity.getValidationResult()).isEmpty();
    }

    // ==================== Field Assignment Tests ====================

    @Test
    @DisplayName("Setters update all fields correctly")
    void testSettersUpdateFields() {
        UUID id = UUID.randomUUID();
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("valid", false);
        validationResult.put("errors", new String[]{"Error 1"});

        entity.setId(id);
        entity.setInvoiceNumber("INV-2024-999");
        entity.setXmlContent("<updated>xml</updated>");
        entity.setSource("UPDATED");
        entity.setCorrelationId("corr-updated");
        entity.setDocumentType(DocumentType.INVOICE);
        entity.setStatus(DocumentStatus.INVALID);
        entity.setValidationResult(validationResult);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getInvoiceNumber()).isEqualTo("INV-2024-999");
        assertThat(entity.getXmlContent()).isEqualTo("<updated>xml</updated>");
        assertThat(entity.getSource()).isEqualTo("UPDATED");
        assertThat(entity.getCorrelationId()).isEqualTo("corr-updated");
        assertThat(entity.getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.INVALID);
        assertThat(entity.getValidationResult()).isEqualTo(validationResult);
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("No-args constructor creates empty entity")
    void testNoArgsConstructor() {
        IncomingDocumentEntity emptyEntity = new IncomingDocumentEntity();

        assertThat(emptyEntity.getId()).isNull();
        assertThat(emptyEntity.getInvoiceNumber()).isNull();
        assertThat(emptyEntity.getXmlContent()).isNull();
        assertThat(emptyEntity.getSource()).isNull();
        assertThat(emptyEntity.getCorrelationId()).isNull();
        assertThat(emptyEntity.getDocumentType()).isNull();
        assertThat(emptyEntity.getStatus()).isNull();
        assertThat(emptyEntity.getValidationResult()).isNull();
    }

    @Test
    @DisplayName("All-args constructor creates entity with all values")
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("valid", true);

        IncomingDocumentEntity fullEntity = new IncomingDocumentEntity(
            id,
            "INV-2024-003",
            "<xml>full</xml>",
            "KAFKA",
            "corr-full",
            DocumentType.ABBREVIATED_TAX_INVOICE,
            DocumentStatus.FORWARDED,
            validationResult,
            now,
            now.plusMinutes(1),
            now,
            now
        );

        assertThat(fullEntity.getId()).isEqualTo(id);
        assertThat(fullEntity.getInvoiceNumber()).isEqualTo("INV-2024-003");
        assertThat(fullEntity.getXmlContent()).isEqualTo("<xml>full</xml>");
        assertThat(fullEntity.getSource()).isEqualTo("KAFKA");
        assertThat(fullEntity.getCorrelationId()).isEqualTo("corr-full");
        assertThat(fullEntity.getDocumentType()).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
        assertThat(fullEntity.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(fullEntity.getValidationResult()).isEqualTo(validationResult);
        assertThat(fullEntity.getReceivedAt()).isEqualTo(now);
        assertThat(fullEntity.getProcessedAt()).isEqualTo(now.plusMinutes(1));
        assertThat(fullEntity.getCreatedAt()).isEqualTo(now);
        assertThat(fullEntity.getUpdatedAt()).isEqualTo(now);
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Entity handles null correlationId")
    void testNullCorrelationId() {
        entity.setCorrelationId(null);

        assertThat(entity.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Entity handles null processedAt")
    void testNullProcessedAt() {
        entity.setProcessedAt(null);

        assertThat(entity.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("Entity handles all document types including null")
    void testAllDocumentTypesIncludingNull() {
        for (DocumentType type : DocumentType.values()) {
            entity.setDocumentType(type);
            assertThat(entity.getDocumentType()).isEqualTo(type);
        }

        entity.setDocumentType(null);
        assertThat(entity.getDocumentType()).isNull();
    }
}
