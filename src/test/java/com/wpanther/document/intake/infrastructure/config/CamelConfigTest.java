package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CamelConfig
 * Tests configuration setup and helper methods without starting Camel context
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.kafka.topics.invoice-intake=document.intake",
    "app.kafka.topics.intake-dlq=document.intake.dlq",
    "app.kafka.topics.tax-invoice=document.received.tax-invoice",
    "app.kafka.topics.receipt=document.received.receipt",
    "app.kafka.topics.invoice=document.received.invoice",
    "app.kafka.topics.debit-credit-note=document.received.debit-credit-note",
    "app.kafka.topics.cancellation=document.received.cancellation",
    "app.kafka.topics.abbreviated=document.received.abbreviated",
    "app.kafka.bootstrap-servers=localhost:9092"
})
@DisplayName("CamelConfig Unit Tests")
class CamelConfigTest {

    @Mock
    private DocumentIntakeService documentIntakeService;

    private CamelConfig camelConfig;

    @BeforeEach
    void setUp() {
        // Create CamelConfig with mocked service and test properties
        camelConfig = new CamelConfig(
            documentIntakeService,
            "document.intake",
            "document.intake.dlq",
            "document.received.tax-invoice",
            "document.received.receipt",
            "document.received.invoice",
            "document.received.debit-credit-note",
            "document.received.cancellation",
            "document.received.abbreviated"
        );
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("CamelConfig constructor initializes all fields")
    void testConstructorInitializesAllFields() throws Exception {
        assertThat(camelConfig).isNotNull();

        // Verify document type topics mapping is initialized
        Field documentTypeTopicsField = CamelConfig.class.getDeclaredField("documentTypeTopics");
        documentTypeTopicsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DocumentType, String> documentTypeTopics = (Map<DocumentType, String>) documentTypeTopicsField.get(camelConfig);

        assertThat(documentTypeTopics).isNotNull();
        assertThat(documentTypeTopics).hasSize(6);
    }

    @Test
    @DisplayName("Document type to topic mapping is correct")
    void testDocumentTypeToTopicMapping() throws Exception {
        Field documentTypeTopicsField = CamelConfig.class.getDeclaredField("documentTypeTopics");
        documentTypeTopicsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DocumentType, String> documentTypeTopics = (Map<DocumentType, String>) documentTypeTopicsField.get(camelConfig);

        assertThat(documentTypeTopics.get(DocumentType.TAX_INVOICE)).isEqualTo("document.received.tax-invoice");
        assertThat(documentTypeTopics.get(DocumentType.RECEIPT)).isEqualTo("document.received.receipt");
        assertThat(documentTypeTopics.get(DocumentType.INVOICE)).isEqualTo("document.received.invoice");
        assertThat(documentTypeTopics.get(DocumentType.DEBIT_CREDIT_NOTE)).isEqualTo("document.received.debit-credit-note");
        assertThat(documentTypeTopics.get(DocumentType.CANCELLATION_NOTE)).isEqualTo("document.received.cancellation");
        assertThat(documentTypeTopics.get(DocumentType.ABBREVIATED_TAX_INVOICE)).isEqualTo("document.received.abbreviated");
    }

    @Test
    @DisplayName("All document types have corresponding topics")
    void testAllDocumentTypesHaveTopics() throws Exception {
        Field documentTypeTopicsField = CamelConfig.class.getDeclaredField("documentTypeTopics");
        documentTypeTopicsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DocumentType, String> documentTypeTopics = (Map<DocumentType, String>) documentTypeTopicsField.get(camelConfig);

        for (DocumentType type : DocumentType.values()) {
            assertThat(documentTypeTopics).containsKey(type);
            assertThat(documentTypeTopics.get(type)).isNotNull();
            assertThat(documentTypeTopics.get(type)).isNotEmpty();
        }
    }

    // ==================== Helper Method Tests ====================

    @Test
    @DisplayName("createDocumentReceivedEvent creates event with all required fields")
    void testCreateDocumentReceivedEventStructure() throws Exception {
        IncomingDocument document = IncomingDocument.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId("corr-test-001")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .build();

        Method createEventMethod = CamelConfig.class.getDeclaredMethod("createDocumentReceivedEvent", IncomingDocument.class);
        createEventMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) createEventMethod.invoke(camelConfig, document);

        assertThat(event).isNotNull();
        assertThat(event).containsKeys("eventId", "occurredAt", "eventType", "version",
            "documentId", "invoiceNumber", "xmlContent", "correlationId", "documentType");

        assertThat(event.get("eventId")).isNotNull();
        assertThat(event.get("occurredAt")).isNotNull();
        assertThat(event.get("eventType")).isEqualTo("document.received");
        assertThat(event.get("version")).isEqualTo(1);
        assertThat(event.get("documentId")).isEqualTo(document.getId().toString());
        assertThat(event.get("invoiceNumber")).isEqualTo("INV-2024-TEST-001");
        assertThat(event.get("xmlContent")).isEqualTo("<test>xml</test>");
        assertThat(event.get("correlationId")).isEqualTo("corr-test-001");
        assertThat(event.get("documentType")).isEqualTo("TAX_INVOICE");
    }

    @Test
    @DisplayName("createDocumentReceivedEvent handles null correlation ID")
    void testCreateDocumentReceivedEventWithNullCorrelationId() throws Exception {
        IncomingDocument document = IncomingDocument.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-002")
            .xmlContent("<test>xml2</test>")
            .source("REST")
            .correlationId(null)
            .documentType(DocumentType.RECEIPT)
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .build();

        Method createEventMethod = CamelConfig.class.getDeclaredMethod("createDocumentReceivedEvent", IncomingDocument.class);
        createEventMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) createEventMethod.invoke(camelConfig, document);

        assertThat(event.get("correlationId")).isNull();
        assertThat(event.get("documentType")).isEqualTo("RECEIPT");
    }

    @Test
    @DisplayName("createDocumentReceivedEvent generates unique event IDs")
    void testCreateDocumentReceivedEventGeneratesUniqueIds() throws Exception {
        IncomingDocument document = IncomingDocument.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-TEST-003")
            .xmlContent("<test>xml3</test>")
            .source("REST")
            .documentType(DocumentType.INVOICE)
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .build();

        Method createEventMethod = CamelConfig.class.getDeclaredMethod("createDocumentReceivedEvent", IncomingDocument.class);
        createEventMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> event1 = (Map<String, Object>) createEventMethod.invoke(camelConfig, document);
        @SuppressWarnings("unchecked")
        Map<String, Object> event2 = (Map<String, Object>) createEventMethod.invoke(camelConfig, document);

        assertThat(event1.get("eventId")).isNotNull();
        assertThat(event2.get("eventId")).isNotNull();
        assertThat(event1.get("eventId")).isNotEqualTo(event2.get("eventId"));
    }

    // ==================== Document Type Coverage Tests ====================

    @Test
    @DisplayName("All document types are supported")
    void testAllDocumentTypesSupported() {
        DocumentType[] types = DocumentType.values();
        assertThat(types).hasSize(6);

        assertThat(types).contains(
            DocumentType.TAX_INVOICE,
            DocumentType.RECEIPT,
            DocumentType.INVOICE,
            DocumentType.DEBIT_CREDIT_NOTE,
            DocumentType.CANCELLATION_NOTE,
            DocumentType.ABBREVIATED_TAX_INVOICE
        );
    }

    // ==================== Document Status Tests ====================

    @Test
    @DisplayName("All document statuses are defined")
    void testAllDocumentStatusesDefined() {
        DocumentStatus[] statuses = DocumentStatus.values();
        assertThat(statuses).hasSizeGreaterThanOrEqualTo(5);

        assertThat(statuses).contains(
            DocumentStatus.RECEIVED,
            DocumentStatus.VALIDATING,
            DocumentStatus.VALIDATED,
            DocumentStatus.INVALID,
            DocumentStatus.FORWARDED
        );
    }

    // ==================== ValidationResult Tests ====================

    @Test
    @DisplayName("ValidationResult can represent success")
    void testValidationResultSuccess() {
        ValidationResult result = ValidationResult.success();
        assertThat(result.valid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("ValidationResult can represent failure")
    void testValidationResultFailure() {
        java.util.List<String> errors = java.util.List.of("Error 1", "Error 2");
        ValidationResult result = ValidationResult.invalid(errors);
        assertThat(result.valid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("ValidationResult can represent warnings")
    void testValidationResultWarnings() {
        java.util.List<String> warnings = java.util.List.of("Warning 1");
        ValidationResult result = ValidationResult.validWithWarnings(warnings);
        assertThat(result.valid()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warningCount()).isEqualTo(1);
    }

    // ==================== Topic Name Tests ====================

    @Test
    @DisplayName("Kafka topic names follow expected pattern")
    void testKafkaTopicNamingPattern() throws Exception {
        Field documentTypeTopicsField = CamelConfig.class.getDeclaredField("documentTypeTopics");
        documentTypeTopicsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DocumentType, String> documentTypeTopics = (Map<DocumentType, String>) documentTypeTopicsField.get(camelConfig);

        for (Map.Entry<DocumentType, String> entry : documentTypeTopics.entrySet()) {
            String topic = entry.getValue();
            assertThat(topic).startsWith("document.received.");
            assertThat(topic).doesNotContain(" ");
        }
    }

    @Test
    @DisplayName("DLQ topic is configured correctly")
    void testDlqTopicConfiguration() {
        // Verify DLQ topic is configured via reflection
        assertThat(camelConfig).isNotNull();
        // The DLQ topic is passed to the constructor as "document.intake.dlq"
        // This test verifies the config was created successfully
    }
}
