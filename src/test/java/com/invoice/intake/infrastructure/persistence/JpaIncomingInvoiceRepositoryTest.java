package com.invoice.intake.infrastructure.persistence;

import com.invoice.intake.domain.model.InvoiceStatus;
import com.invoice.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JpaIncomingInvoiceRepository
 * Uses H2 in-memory database for fast testing
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("JpaIncomingInvoice Repository Tests")
class JpaIncomingInvoiceRepositoryTest {

    @Autowired
    private JpaIncomingInvoiceRepository repository;

    private IncomingInvoiceEntity testEntity;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        testEntity = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-001")
            .xmlContent("<test>xml content</test>")
            .source("TEST")
            .correlationId("corr-test-001")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.RECEIVED)
            .validationResult(Map.of("valid", true, "errors", List.of(), "warnings", List.of()))
            .build();

        testEntity = repository.save(testEntity);
    }

    // ==================== findByInvoiceNumber Tests ====================

    @Test
    @DisplayName("findByInvoiceNumber returns entity when found")
    void testFindByInvoiceNumberReturnsEntity() {
        Optional<IncomingInvoiceEntity> result = repository.findByInvoiceNumber("INV-2024-TEST-001");

        assertThat(result).isPresent();
        assertThat(result.get().getInvoiceNumber()).isEqualTo("INV-2024-TEST-001");
        assertThat(result.get().getSource()).isEqualTo("TEST");
    }

    @Test
    @DisplayName("findByInvoiceNumber returns empty when not found")
    void testFindByInvoiceNumberReturnsEmpty() {
        Optional<IncomingInvoiceEntity> result = repository.findByInvoiceNumber("NON-EXISTENT");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByInvoiceNumber returns null for null input")
    void testFindByInvoiceNumberWithNull() {
        Optional<IncomingInvoiceEntity> result = repository.findByInvoiceNumber(null);

        assertThat(result).isEmpty();
    }

    // ==================== findByDocumentType Tests ====================

    @Test
    @DisplayName("findByDocumentType returns list of matching entities")
    void testFindByDocumentType() {
        // Create additional entities with different document types
        IncomingInvoiceEntity receiptEntity = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-002")
            .xmlContent("<receipt/>")
            .source("TEST")
            .documentType(DocumentType.RECEIPT)
            .status(InvoiceStatus.VALIDATED)
            .build();
        repository.save(receiptEntity);

        IncomingInvoiceEntity anotherTaxInvoice = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-003")
            .xmlContent("<tax-invoice-2/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.VALIDATED)
            .build();
        repository.save(anotherTaxInvoice);

        List<IncomingInvoiceEntity> taxInvoices = repository.findByDocumentType(DocumentType.TAX_INVOICE);

        assertThat(taxInvoices).hasSize(2);
        assertThat(taxInvoices)
            .allMatch(e -> e.getDocumentType() == DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("findByDocumentType returns empty list when no matches")
    void testFindByDocumentTypeReturnsEmpty() {
        List<IncomingInvoiceEntity> result = repository.findByDocumentType(DocumentType.DEBIT_CREDIT_NOTE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByDocumentType handles all document types")
    void testFindByDocumentTypeAllTypes() {
        // Create one entity for each document type
        for (DocumentType type : DocumentType.values()) {
            IncomingInvoiceEntity entity = IncomingInvoiceEntity.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-" + type.name() + "-001")
                .xmlContent("<" + type.name() + "/>")
                .source("TEST")
                .documentType(type)
                .status(InvoiceStatus.RECEIVED)
                .build();
            repository.save(entity);
        }

        for (DocumentType type : DocumentType.values()) {
            List<IncomingInvoiceEntity> results = repository.findByDocumentType(type);
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getDocumentType()).isEqualTo(type);
        }
    }

    // ==================== findByStatus Tests ====================

    @Test
    @DisplayName("findByStatus returns list of entities with matching status")
    void testFindByStatus() {
        // Create entities with different statuses
        IncomingInvoiceEntity validatedEntity = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-002")
            .xmlContent("<validated/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.VALIDATED)
            .build();
        repository.save(validatedEntity);

        IncomingInvoiceEntity forwardedEntity = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-003")
            .xmlContent("<forwarded/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.FORWARDED)
            .build();
        repository.save(forwardedEntity);

        List<IncomingInvoiceEntity> received = repository.findByStatus(InvoiceStatus.RECEIVED);
        List<IncomingInvoiceEntity> validated = repository.findByStatus(InvoiceStatus.VALIDATED);
        List<IncomingInvoiceEntity> forwarded = repository.findByStatus(InvoiceStatus.FORWARDED);

        assertThat(received).hasSize(1);
        assertThat(validated).hasSize(1);
        assertThat(forwarded).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus returns empty list when no matches")
    void testFindByStatusReturnsEmpty() {
        List<IncomingInvoiceEntity> result = repository.findByStatus(InvoiceStatus.FAILED);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByStatus handles all status values")
    void testFindByStatusAllStatuses() {
        // Create one entity for each status
        for (InvoiceStatus status : InvoiceStatus.values()) {
            IncomingInvoiceEntity entity = IncomingInvoiceEntity.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-" + status.name() + "-001")
                .xmlContent("<" + status.name() + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(status)
                .build();
            repository.save(entity);
        }

        for (InvoiceStatus status : InvoiceStatus.values()) {
            List<IncomingInvoiceEntity> results = repository.findByStatus(status);
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getStatus()).isEqualTo(status);
        }
    }

    // ==================== countByStatus Tests ====================

    @Test
    @DisplayName("countByStatus returns correct count")
    void testCountByStatus() {
        // Create more entities with RECEIVED status
        IncomingInvoiceEntity entity2 = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-002")
            .xmlContent("<test2/>")
            .source("TEST")
            .status(InvoiceStatus.RECEIVED)
            .build();
        repository.save(entity2);

        IncomingInvoiceEntity entity3 = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-003")
            .xmlContent("<test3/>")
            .source("TEST")
            .status(InvoiceStatus.RECEIVED)
            .build();
        repository.save(entity3);

        long count = repository.countByStatus(InvoiceStatus.RECEIVED);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByStatus returns zero when no matches")
    void testCountByStatusReturnsZero() {
        long count = repository.countByStatus(InvoiceStatus.INVALID);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("countByStatus counts all statuses correctly")
    void testCountByStatusAllStatuses() {
        // Add entities for each status
        for (InvoiceStatus status : InvoiceStatus.values()) {
            IncomingInvoiceEntity entity = IncomingInvoiceEntity.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-" + status.name() + "-001")
                .xmlContent("<" + status.name() + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(status)
                .build();
            repository.save(entity);
        }

        for (InvoiceStatus status : InvoiceStatus.values()) {
            long count = repository.countByStatus(status);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== existsByInvoiceNumber Tests ====================

    @Test
    @DisplayName("existsByInvoiceNumber returns true when invoice exists")
    void testExistsByInvoiceNumberReturnsTrue() {
        boolean exists = repository.existsByInvoiceNumber("INV-2024-TEST-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByInvoiceNumber returns false when invoice not found")
    void testExistsByInvoiceNumberReturnsFalse() {
        boolean exists = repository.existsByInvoiceNumber("NON-EXISTENT");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByInvoiceNumber returns false for null input")
    void testExistsByInvoiceNumberWithNull() {
        boolean exists = repository.existsByInvoiceNumber(null);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByInvoiceNumber is case-sensitive")
    void testExistsByInvoiceNumberIsCaseSensitive() {
        boolean exists = repository.existsByInvoiceNumber("inv-2024-test-001");

        assertThat(exists).isFalse();
    }

    // ==================== CRUD Operations Tests ====================

    @Test
    @DisplayName("save creates new entity")
    void testSaveCreatesEntity() {
        IncomingInvoiceEntity newEntity = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-NEW-001")
            .xmlContent("<new/>")
            .source("TEST")
            .documentType(DocumentType.INVOICE)
            .status(InvoiceStatus.RECEIVED)
            .build();

        IncomingInvoiceEntity saved = repository.save(newEntity);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("save updates existing entity")
    void testSaveUpdatesEntity() {
        testEntity.setStatus(InvoiceStatus.FORWARDED);
        testEntity.setProcessedAt(java.time.LocalDateTime.now());

        IncomingInvoiceEntity updated = repository.save(testEntity);

        assertThat(updated.getStatus()).isEqualTo(InvoiceStatus.FORWARDED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById returns entity when exists")
    void testFindById() {
        Optional<IncomingInvoiceEntity> result = repository.findById(testEntity.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getInvoiceNumber()).isEqualTo("INV-2024-TEST-001");
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void testFindByIdNotFound() {
        Optional<IncomingInvoiceEntity> result = repository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all entities")
    void testFindAll() {
        // Add more entities
        for (int i = 2; i <= 5; i++) {
            IncomingInvoiceEntity entity = IncomingInvoiceEntity.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2024-TEST-00" + i)
                .xmlContent("<test" + i + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(InvoiceStatus.RECEIVED)
                .build();
            repository.save(entity);
        }

        List<IncomingInvoiceEntity> all = repository.findAll();

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
            IncomingInvoiceEntity entity = IncomingInvoiceEntity.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2024-COUNT-00" + i)
                .xmlContent("<count" + i + "/>")
                .source("TEST")
                .documentType(DocumentType.TAX_INVOICE)
                .status(InvoiceStatus.RECEIVED)
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
        IncomingInvoiceEntity taxInvoice1 = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-TAX-001")
            .xmlContent("<tax1/>")
            .source("TEST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.RECEIVED)
            .build();
        repository.save(taxInvoice1);

        IncomingInvoiceEntity receipt1 = IncomingInvoiceEntity.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-RCPT-001")
            .xmlContent("<receipt1/>")
            .source("TEST")
            .documentType(DocumentType.RECEIPT)
            .status(InvoiceStatus.RECEIVED)
            .build();
        repository.save(receipt1);

        List<IncomingInvoiceEntity> receivedTaxInvoices = repository.findByDocumentType(DocumentType.TAX_INVOICE);
        List<IncomingInvoiceEntity> receivedInvoices = repository.findByStatus(InvoiceStatus.RECEIVED);

        assertThat(receivedTaxInvoices).hasSizeGreaterThanOrEqualTo(1);
        assertThat(receivedInvoices).hasSizeGreaterThanOrEqualTo(2);
    }
}
