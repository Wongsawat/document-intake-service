package com.wpanther.document.intake.infrastructure.persistence;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JpaIncomingDocumentRepository
 * Uses H2 in-memory database for fast testing
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("JpaIncomingDocument Repository Tests")
class JpaIncomingDocumentRepositoryTest {

    @Autowired
    private JpaIncomingDocumentRepository repository;

    private IncomingDocumentEntity testEntity;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        testEntity = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-001")
            .xmlContent("<test>xml content</test>")
            .source("TEST")
            .correlationId("corr-test-001")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.RECEIVED)
            .validationResult("{\"valid\":true,\"errors\":[],\"warnings\":[]}")
            .build();

        testEntity = repository.save(testEntity);
    }

    // ==================== findByDocumentNumber Tests ====================

    @Test
    @DisplayName("findByDocumentNumber returns entity when found")
    void testFindByInvoiceNumberReturnsEntity() {
        Optional<IncomingDocumentEntity> result = repository.findByDocumentNumber("INV-2024-TEST-001");

        assertThat(result).isPresent();
        assertThat(result.get().getDocumentNumber()).isEqualTo("INV-2024-TEST-001");
        assertThat(result.get().getSource()).isEqualTo("TEST");
    }

    @Test
    @DisplayName("findByDocumentNumber returns empty when not found")
    void testFindByInvoiceNumberReturnsEmpty() {
        Optional<IncomingDocumentEntity> result = repository.findByDocumentNumber("NON-EXISTENT");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByDocumentNumber returns null for null input")
    void testFindByInvoiceNumberWithNull() {
        Optional<IncomingDocumentEntity> result = repository.findByDocumentNumber(null);

        assertThat(result).isEmpty();
    }

    // ==================== findByDocumentType Tests ====================

    @Test
    @DisplayName("findByDocumentType returns list of matching entities")
    void testFindByDocumentType() {
        // Create additional entities with different document types
        IncomingDocumentEntity receiptEntity = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-002")
            .xmlContent("<receipt/>")
            .source("TEST")
            .documentType(DocumentType.RECEIPT)
            .status(DocumentStatus.VALIDATED)
            .build();
        repository.save(receiptEntity);

        IncomingDocumentEntity anotherTaxInvoice = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-003")
            .xmlContent("<tax-document-2/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.VALIDATED)
            .build();
        repository.save(anotherTaxInvoice);

        List<IncomingDocumentEntity> taxInvoices = repository.findByDocumentType(DocumentType.TAX_INVOICE);

        assertThat(taxInvoices).hasSize(2);
        assertThat(taxInvoices)
            .allMatch(e -> e.getDocumentType() == DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("findByDocumentType returns empty list when no matches")
    void testFindByDocumentTypeReturnsEmpty() {
        List<IncomingDocumentEntity> result = repository.findByDocumentType(DocumentType.DEBIT_CREDIT_NOTE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByDocumentType handles all document types")
    void testFindByDocumentTypeAllTypes() {
        // Create one entity for each document type
        for (DocumentType type : DocumentType.values()) {
            IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-" + type.name() + "-001")
                .xmlContent("<" + type.name() + "/>")
                .source("TEST")
                .documentType(type)
                .status(DocumentStatus.RECEIVED)
                .build();
            repository.save(entity);
        }

        for (DocumentType type : DocumentType.values()) {
            List<IncomingDocumentEntity> results = repository.findByDocumentType(type);
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getDocumentType()).isEqualTo(type);
        }
    }

    // ==================== findByStatus Tests ====================

    @Test
    @DisplayName("findByStatus returns list of entities with matching status")
    void testFindByStatus() {
        // Create entities with different statuses
        IncomingDocumentEntity validatedEntity = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-002")
            .xmlContent("<validated/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.VALIDATED)
            .build();
        repository.save(validatedEntity);

        IncomingDocumentEntity forwardedEntity = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-003")
            .xmlContent("<forwarded/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.FORWARDED)
            .build();
        repository.save(forwardedEntity);

        List<IncomingDocumentEntity> received = repository.findByStatus(DocumentStatus.RECEIVED);
        List<IncomingDocumentEntity> validated = repository.findByStatus(DocumentStatus.VALIDATED);
        List<IncomingDocumentEntity> forwarded = repository.findByStatus(DocumentStatus.FORWARDED);

        assertThat(received).hasSize(1);
        assertThat(validated).hasSize(1);
        assertThat(forwarded).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus returns empty list when no matches")
    void testFindByStatusReturnsEmpty() {
        List<IncomingDocumentEntity> result = repository.findByStatus(DocumentStatus.FAILED);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByStatus handles all status values")
    void testFindByStatusAllStatuses() {
        // Create one entity for each status
        for (DocumentStatus status : DocumentStatus.values()) {
            IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-" + status.name() + "-001")
                .xmlContent("<" + status.name() + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(status)
                .build();
            repository.save(entity);
        }

        for (DocumentStatus status : DocumentStatus.values()) {
            List<IncomingDocumentEntity> results = repository.findByStatus(status);
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getStatus()).isEqualTo(status);
        }
    }

    // ==================== countByStatus Tests ====================

    @Test
    @DisplayName("countByStatus returns correct count")
    void testCountByStatus() {
        // Create more entities with RECEIVED status
        IncomingDocumentEntity entity2 = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-002")
            .xmlContent("<test2/>")
            .source("TEST")
            .status(DocumentStatus.RECEIVED)
            .build();
        repository.save(entity2);

        IncomingDocumentEntity entity3 = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-TEST-003")
            .xmlContent("<test3/>")
            .source("TEST")
            .status(DocumentStatus.RECEIVED)
            .build();
        repository.save(entity3);

        long count = repository.countByStatus(DocumentStatus.RECEIVED);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByStatus returns zero when no matches")
    void testCountByStatusReturnsZero() {
        long count = repository.countByStatus(DocumentStatus.INVALID);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("countByStatus counts all statuses correctly")
    void testCountByStatusAllStatuses() {
        // Add entities for each status
        for (DocumentStatus status : DocumentStatus.values()) {
            IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-" + status.name() + "-001")
                .xmlContent("<" + status.name() + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(status)
                .build();
            repository.save(entity);
        }

        for (DocumentStatus status : DocumentStatus.values()) {
            long count = repository.countByStatus(status);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== existsByDocumentNumber Tests ====================

    @Test
    @DisplayName("existsByDocumentNumber returns true when document exists")
    void testExistsByInvoiceNumberReturnsTrue() {
        boolean exists = repository.existsByDocumentNumber("INV-2024-TEST-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByDocumentNumber returns false when document not found")
    void testExistsByInvoiceNumberReturnsFalse() {
        boolean exists = repository.existsByDocumentNumber("NON-EXISTENT");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByDocumentNumber returns false for null input")
    void testExistsByInvoiceNumberWithNull() {
        boolean exists = repository.existsByDocumentNumber(null);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByDocumentNumber is case-sensitive")
    void testExistsByInvoiceNumberIsCaseSensitive() {
        boolean exists = repository.existsByDocumentNumber("inv-2024-test-001");

        assertThat(exists).isFalse();
    }

    // ==================== CRUD Operations Tests ====================

    @Test
    @DisplayName("save creates new entity")
    void testSaveCreatesEntity() {
        IncomingDocumentEntity newEntity = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-NEW-001")
            .xmlContent("<new/>")
            .source("TEST")
            .documentType(DocumentType.INVOICE)
            .status(DocumentStatus.RECEIVED)
            .build();

        IncomingDocumentEntity saved = repository.save(newEntity);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("save updates existing entity")
    void testSaveUpdatesEntity() {
        testEntity.setStatus(DocumentStatus.FORWARDED);
        testEntity.setProcessedAt(java.time.LocalDateTime.now());

        IncomingDocumentEntity updated = repository.save(testEntity);

        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById returns entity when exists")
    void testFindById() {
        Optional<IncomingDocumentEntity> result = repository.findById(testEntity.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getDocumentNumber()).isEqualTo("INV-2024-TEST-001");
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void testFindByIdNotFound() {
        Optional<IncomingDocumentEntity> result = repository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all entities")
    void testFindAll() {
        // Add more entities
        for (int i = 2; i <= 5; i++) {
            IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-2024-TEST-00" + i)
                .xmlContent("<test" + i + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.RECEIVED)
                .build();
            repository.save(entity);
        }

        List<IncomingDocumentEntity> all = repository.findAll();

        assertThat(all).hasSize(5);
    }

    @Test
    @DisplayName("deleteById removes entity")
    void testDeleteById() {
        UUID id = testEntity.getId();

        assertThat(repository.findById(id)).isPresent();

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("count returns total entity count")
    void testCount() {
        long initialCount = repository.count();

        // Add more entities
        for (int i = 0; i < 3; i++) {
            IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-2024-COUNT-00" + i)
                .xmlContent("<count" + i + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.RECEIVED)
                .build();
            repository.save(entity);
        }

        long finalCount = repository.count();

        assertThat(finalCount).isEqualTo(initialCount + 3);
    }

    // ==================== Combination Query Tests ====================

    @Test
    @DisplayName("Can combine findByDocumentType and findByStatus results")
    void testCombinedQueries() {
        // Create test data: RECEIVED TaxInvoices and RECEIVED Receipts
        IncomingDocumentEntity taxInvoice1 = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-TAX-001")
            .xmlContent("<tax1/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.RECEIVED)
            .build();
        repository.save(taxInvoice1);

        IncomingDocumentEntity receipt1 = IncomingDocumentEntity.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-RCPT-001")
            .xmlContent("<receipt1/>")
            .source("TEST")
            .documentType(DocumentType.RECEIPT)
            .status(DocumentStatus.RECEIVED)
            .build();
        repository.save(receipt1);

        List<IncomingDocumentEntity> receivedTaxInvoices = repository.findByDocumentType(DocumentType.TAX_INVOICE);
        List<IncomingDocumentEntity> receivedInvoices = repository.findByStatus(DocumentStatus.RECEIVED);

        assertThat(receivedTaxInvoices).hasSizeGreaterThanOrEqualTo(1);
        assertThat(receivedInvoices).hasSizeGreaterThanOrEqualTo(2);
    }
}
