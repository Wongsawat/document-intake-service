package com.wpanther.document.intake.infrastructure.adapter.out.persistence;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
            .documentNumber("INV-2024-001")
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
        entity = IncomingDocumentEntity.builder()
            .id(existingId)
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .build();

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
        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .status(DocumentStatus.VALIDATED)
            .build();

        entity.onCreate();

        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
    }

    // ==================== Builder Pattern Tests ====================

    @Test
    @DisplayName("Builder creates entity with all fields")
    void testBuilderCreatesEntityWithAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String validationResult = "{\"valid\":true,\"errors\":[],\"warnings\":[]}";

        IncomingDocumentEntity fullEntity = IncomingDocumentEntity.builder()
            .id(id)
            .documentNumber("INV-2024-002")
            .xmlContent("<xml>content</xml>")
            .source("KAFKA")
            .correlationId("corr-456")
            .documentType(DocumentType.RECEIPT)
            .status(DocumentStatus.VALIDATED)
            .validationResult(validationResult)
            .receivedAt(now)
            .processedAt(now.plusSeconds(300))
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(fullEntity.getId()).isEqualTo(id);
        assertThat(fullEntity.getDocumentNumber()).isEqualTo("INV-2024-002");
        assertThat(fullEntity.getXmlContent()).isEqualTo("<xml>content</xml>");
        assertThat(fullEntity.getSource()).isEqualTo("KAFKA");
        assertThat(fullEntity.getCorrelationId()).isEqualTo("corr-456");
        assertThat(fullEntity.getDocumentType()).isEqualTo(DocumentType.RECEIPT);
        assertThat(fullEntity.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
        assertThat(fullEntity.getValidationResult()).isEqualTo(validationResult);
        assertThat(fullEntity.getReceivedAt()).isEqualTo(now);
        assertThat(fullEntity.getProcessedAt()).isEqualTo(now.plusSeconds(300));
        assertThat(fullEntity.getCreatedAt()).isEqualTo(now);
        assertThat(fullEntity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Builder allows creating multiple entities with different values")
    void testBuilderCreatesMultipleEntities() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String validationResult = "{\"valid\":true,\"errors\":[],\"warnings\":[]}";

        IncomingDocumentEntity entity1 = IncomingDocumentEntity.builder()
            .id(id1)
            .documentNumber("INV-2024-001")
            .xmlContent("<xml>content1</xml>")
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.RECEIVED)
            .validationResult(validationResult)
            .build();

        IncomingDocumentEntity entity2 = IncomingDocumentEntity.builder()
            .id(id2)
            .documentNumber("INV-2024-999")
            .xmlContent("<xml>content2</xml>")
            .source("KAFKA")
            .documentType(DocumentType.INVOICE)
            .status(DocumentStatus.INVALID)
            .validationResult(validationResult)
            .build();

        assertThat(entity1.getId()).isEqualTo(id1);
        assertThat(entity1.getDocumentNumber()).isEqualTo("INV-2024-001");
        assertThat(entity1.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(entity1.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);

        assertThat(entity2.getId()).isEqualTo(id2);
        assertThat(entity2.getDocumentNumber()).isEqualTo("INV-2024-999");
        assertThat(entity2.getStatus()).isEqualTo(DocumentStatus.INVALID);
        assertThat(entity2.getDocumentType()).isEqualTo(DocumentType.INVOICE);
    }

    // ==================== Enum Persistence Tests ====================

    @Test
    @DisplayName("Entity stores DocumentType enum correctly")
    void testDocumentTypeEnum() {
        for (DocumentType type : DocumentType.values()) {
            IncomingDocumentEntity testEntity = IncomingDocumentEntity.builder()
                .documentNumber("INV-" + type.name())
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
                .documentNumber("INV-" + status.name())
                .xmlContent("<test/>")
                .source("TEST")
                .status(status)
                .build();

            assertThat(testEntity.getStatus()).isEqualTo(status);
        }
    }

    // ==================== JSON Field Tests ====================

    @Test
    @DisplayName("Entity stores validationResult as JSON text")
    void testValidationResultJson() {
        String validationResult = "{\"valid\":true,\"errors\":[],\"warnings\":[\"Warning 1\"]}";

        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .validationResult(validationResult)
            .build();

        assertThat(entity.getValidationResult()).isNotNull();
        assertThat(entity.getValidationResult()).contains("valid");
        assertThat(entity.getValidationResult()).contains("Warning 1");
    }

    @Test
    @DisplayName("Entity handles null validationResult")
    void testNullValidationResult() {
        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .validationResult(null)
            .build();

        assertThat(entity.getValidationResult()).isNull();
    }

    @Test
    @DisplayName("Entity handles empty validationResult")
    void testEmptyValidationResult() {
        String emptyResult = "{}";
        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .validationResult(emptyResult)
            .build();

        assertThat(entity.getValidationResult()).isNotNull();
        assertThat(entity.getValidationResult()).isEqualTo("{}");
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("No-args constructor creates empty entity")
    void testNoArgsConstructor() {
        IncomingDocumentEntity emptyEntity = new IncomingDocumentEntity();

        assertThat(emptyEntity.getId()).isNull();
        assertThat(emptyEntity.getDocumentNumber()).isNull();
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
        Instant now = Instant.now();
        String validationResult = "{\"valid\":true,\"errors\":[],\"warnings\":[]}";

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
            now.plusSeconds(60),
            now,
            now
        );

        assertThat(fullEntity.getId()).isEqualTo(id);
        assertThat(fullEntity.getDocumentNumber()).isEqualTo("INV-2024-003");
        assertThat(fullEntity.getXmlContent()).isEqualTo("<xml>full</xml>");
        assertThat(fullEntity.getSource()).isEqualTo("KAFKA");
        assertThat(fullEntity.getCorrelationId()).isEqualTo("corr-full");
        assertThat(fullEntity.getDocumentType()).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
        assertThat(fullEntity.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(fullEntity.getValidationResult()).isEqualTo(validationResult);
        assertThat(fullEntity.getReceivedAt()).isEqualTo(now);
        assertThat(fullEntity.getProcessedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(fullEntity.getCreatedAt()).isEqualTo(now);
        assertThat(fullEntity.getUpdatedAt()).isEqualTo(now);
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Entity handles null correlationId")
    void testNullCorrelationId() {
        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId(null)
            .build();

        assertThat(entity.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Entity handles null processedAt")
    void testNullProcessedAt() {
        entity = IncomingDocumentEntity.builder()
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .processedAt(null)
            .build();

        assertThat(entity.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("Entity handles all document types including null")
    void testAllDocumentTypesIncludingNull() {
        for (DocumentType type : DocumentType.values()) {
            IncomingDocumentEntity testEntity = IncomingDocumentEntity.builder()
                .documentNumber("INV-" + type.name())
                .xmlContent("<test/>")
                .source("TEST")
                .documentType(type)
                .build();
            assertThat(testEntity.getDocumentType()).isEqualTo(type);
        }

        IncomingDocumentEntity nullTypeEntity = IncomingDocumentEntity.builder()
            .documentNumber("INV-null")
            .xmlContent("<test/>")
            .source("TEST")
            .documentType(null)
            .build();
        assertThat(nullTypeEntity.getDocumentType()).isNull();
    }
}
