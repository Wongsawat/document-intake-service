package com.wpanther.document.intake.application.usecase;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.repository.DocumentRepository;
import com.wpanther.document.intake.application.port.out.XmlValidationPort;
import com.wpanther.document.intake.application.port.out.DocumentEventPublisher;
import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.application.port.out.DocumentIntakeMetricsPort;
import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.dto.event.EventStatus;
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentIntakeApplicationService
 * Tests use mocks for repository and validation service dependencies
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Document Intake Service Tests")
class DocumentIntakeServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private XmlValidationPort validationService;

    @Mock
    private DocumentEventPublisher eventPublisher;

    @Mock
    private DocumentIntakeMetricsPort metrics;

    @InjectMocks
    private DocumentIntakeApplicationService documentIntakeService;

    private static final String VALID_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:TaxInvoice_CrossIndustryInvoice
            xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2"
            xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>INV-2024-001</ram:ID>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                    <ram:SellerTradeParty>
                        <ram:Name>Test Seller</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="TXID">12345678901230001</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                    </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                    <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                    <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        <ram:LineTotalAmount>1000</ram:LineTotalAmount>
                        <ram:GrandTotalAmount>1070</ram:GrandTotalAmount>
                    </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                    <ram:AssociatedDocumentLineDocument>
                        <ram:LineID>1</ram:LineID>
                    </ram:AssociatedDocumentLineDocument>
                    <ram:SpecifiedTradeProduct>
                        <ram:Name>Test Product</ram:Name>
                    </ram:SpecifiedTradeProduct>
                </ram:IncludedSupplyChainTradeLineItem>
            </rsm:SupplyChainTradeTransaction>
        </rsm:TaxInvoice_CrossIndustryInvoice>
        """;

    private static final String INVALID_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <invalid>xml</invalid>
        """;

    @BeforeEach
    void setUp() {
        // Default mock behaviors
        when(validationService.extractDocumentNumber(any())).thenReturn("INV-2024-001");
        when(validationService.extractDocumentType(any())).thenReturn(DocumentType.TAX_INVOICE);
        when(validationService.validate(any())).thenReturn(ValidationResult.success());
        when(documentRepository.existsByDocumentNumber(any())).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Metrics methods do nothing by default
        doNothing().when(metrics).incrementReceived();
        doNothing().when(metrics).incrementValidated(any());
        doNothing().when(metrics).incrementInvalid(any());
        doNothing().when(metrics).incrementForwarded(any());
        doNothing().when(metrics).incrementFailed(any());
        doNothing().when(metrics).recordProcessingTime(anyLong());
    }

    // ==================== Happy Path Tests ====================

    @Test
    @DisplayName("Submit document with valid XML succeeds and forwards")
    void testSubmitInvoiceWithValidXml() {
        IncomingDocument result = documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        assertThat(result).isNotNull();
        assertThat(result.getDocumentNumber()).isEqualTo("INV-2024-001");
        assertThat(result.getSource()).isEqualTo("REST");
        assertThat(result.getCorrelationId()).isEqualTo("corr-123");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        // After saga command is published, document is marked as FORWARDED
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Submit document stores document type")
    void testSubmitInvoiceStoresDocumentType() {
        IncomingDocument result = documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        assertThat(result.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Submit document transitions states correctly")
    void testSubmitInvoiceTransitionsStatesCorrectly() {
        // Verify that save is called 4 times for state transitions
        ArgumentCaptor<IncomingDocument> captor = ArgumentCaptor.forClass(IncomingDocument.class);
        when(documentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        documentIntakeService.submitDocument(VALID_XML, "KAFKA", "corr-456");

        // Should have saved 4 times: initial, VALIDATING, VALIDATED, FORWARDED
        assertThat(captor.getAllValues()).hasSize(4);
    }

    // ==================== Validation Error Tests ====================

    @Test
    @DisplayName("Submit document with invalid XML returns invalid result")
    void testSubmitInvoiceWithInvalidXml() {
        when(validationService.validate(any())).thenReturn(ValidationResult.invalid(List.of("Validation error")));

        IncomingDocument result = documentIntakeService.submitDocument(INVALID_XML, "REST", "corr-789");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.INVALID);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationResult().hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Submit document with duplicate document number throws exception")
    void testSubmitInvoiceWithDuplicateInvoiceNumber() {
        when(documentRepository.existsByDocumentNumber("INV-2024-001")).thenReturn(true);

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists")
            .hasMessageContaining("INV-2024-001");

        // Verify no save was attempted
        verify(documentRepository, never()).save(any());
    }

    // ==================== Invoice Number Extraction Tests ====================

    @Test
    @DisplayName("Submit document extracts document number")
    void testSubmitInvoiceExtractsInvoiceNumber() {
        documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        verify(validationService).extractDocumentNumber(VALID_XML);
    }

    @Test
    @DisplayName("Submit document with null document number throws exception")
    void testSubmitInvoiceHandlesNullInvoiceNumber() {
        when(validationService.extractDocumentNumber(any())).thenReturn(null);

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract document number");

        // Verify no save was attempted
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit document with blank document number throws exception")
    void testSubmitInvoiceHandlesBlankInvoiceNumber() {
        when(validationService.extractDocumentNumber(any())).thenReturn("   ");

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract document number");

        // Verify no save was attempted
        verify(documentRepository, never()).save(any());
    }

    // ==================== Document Type Extraction Tests ====================

    @Test
    @DisplayName("Submit document extracts document type")
    void testSubmitInvoiceExtractsDocumentType() {
        documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        verify(validationService).extractDocumentType(VALID_XML);
    }

    @Test
    @DisplayName("Submit document with null document type throws exception")
    void testSubmitInvoiceHandlesNullDocumentType() {
        when(validationService.extractDocumentType(any())).thenReturn(null);

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not detect document type");

        // Verify no save was attempted
        verify(documentRepository, never()).save(any());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Submit document with null XML throws exception")
    void testSubmitInvoiceWithNullXml() {
        assertThatThrownBy(() -> documentIntakeService.submitDocument(null, "REST", "corr-123"))
            .isInstanceOf(NullPointerException.class);

        verify(validationService).extractDocumentNumber(null);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit document with empty XML throws exception")
    void testSubmitInvoiceWithEmptyXml() {
        // Override the default mock to return null for empty input
        when(validationService.extractDocumentNumber("")).thenReturn(null);

        assertThatThrownBy(() -> documentIntakeService.submitDocument("", "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract document number");
    }

    @Test
    @DisplayName("Submit document with null source throws exception")
    void testSubmitInvoiceWithNullSource() {
        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, null, "corr-123"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Submit document with null correlation ID")
    void testSubmitInvoiceWithNullCorrelationId() {
        IncomingDocument result = documentIntakeService.submitDocument(VALID_XML, "REST", null);

        assertThat(result.getCorrelationId()).isNull();
    }

    // ==================== Repository Interaction Tests ====================

    @Test
    @DisplayName("Submit document saves initial then final state")
    void testSubmitInvoiceSavesInitialThenFinalState() {
        ArgumentCaptor<IncomingDocument> captor = ArgumentCaptor.forClass(IncomingDocument.class);
        when(documentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        // Verify multiple saves for state transitions
        assertThat(captor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Submit document checks for duplicate")
    void testSubmitInvoiceChecksForDuplicate() {
        documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        verify(documentRepository).existsByDocumentNumber("INV-2024-001");
    }

    // ==================== Get Document Tests ====================

    @Test
    @DisplayName("Get document by ID returns document")
    void testGetInvoiceById() {
        UUID documentId = UUID.randomUUID();
        IncomingDocument document = IncomingDocument.builder()
            .id(documentId)
            .documentNumber("INV-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        IncomingDocument result = documentIntakeService.getDocument(documentId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(documentId);
    }

    @Test
    @DisplayName("Get document throws on not found")
    void testGetInvoiceThrowsOnNotFound() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentIntakeService.getDocument(documentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Document not found")
            .hasMessageContaining(documentId.toString());
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Full workflow: submit, validate, get, and mark forwarded")
    void testFullWorkflow() {
        UUID documentId = UUID.randomUUID();
        IncomingDocument document = IncomingDocument.builder()
            .id(documentId)
            .documentNumber("INV-2024-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .correlationId("corr-123")
            .documentType(DocumentType.TAX_INVOICE)
            .validationResult(ValidationResult.success())
            .build();

        when(documentRepository.save(any())).thenReturn(document);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        // Submit
        IncomingDocument submitted = documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");
        assertThat(submitted.isValid()).isTrue();

        // Get
        IncomingDocument retrieved = documentIntakeService.getDocument(documentId);
        assertThat(retrieved).isNotNull();

    }

    // ==================== Different Document Types Tests ====================

    @Test
    @DisplayName("Submit document handles all document types")
    void testSubmitInvoiceHandlesAllDocumentTypes() {
        for (DocumentType type : DocumentType.values()) {
            when(validationService.extractDocumentType(any())).thenReturn(type);

            IncomingDocument result = documentIntakeService.submitDocument(VALID_XML, "REST", "corr-" + type.name());

            assertThat(result.getDocumentType()).isEqualTo(type);
        }
    }

    // ==================== Validation Result Tests ====================

    @Test
    @DisplayName("Submit document with validation warnings")
    void testSubmitInvoiceWithValidationWarnings() {
        ValidationResult warningResult = ValidationResult.validWithWarnings(List.of("Warning 1", "Warning 2"));
        when(validationService.validate(any())).thenReturn(warningResult);

        IncomingDocument result = documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123");

        assertThat(result.isValid()).isTrue(); // Warnings still valid
        assertThat(result.getValidationResult().hasWarnings()).isTrue();
        assertThat(result.getValidationResult().warningCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Submit document with multiple errors")
    void testSubmitInvoiceWithMultipleErrors() {
        List<String> errors = List.of("Error 1", "Error 2", "Error 3");
        ValidationResult errorResult = ValidationResult.invalid(errors);
        when(validationService.validate(any())).thenReturn(errorResult);

        IncomingDocument result = documentIntakeService.submitDocument(INVALID_XML, "REST", "corr-123");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationResult().errorCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Submit document saves validation result")
    void testSubmitInvoiceSavesValidationResult() {
        ValidationResult result = ValidationResult.invalid(List.of("Schema error"));
        when(validationService.validate(any())).thenReturn(result);

        IncomingDocument document = documentIntakeService.submitDocument(INVALID_XML, "REST", "corr-123");

        assertThat(document.getValidationResult()).isEqualTo(result);
    }

    // ==================== Event Publishing Tests ====================

    @Test
    @DisplayName("Submit valid document publishes three trace events and one saga command")
    void testSubmitValidDocumentPublishesCorrectEvents() {
        String correlationId = "corr-events-123";
        documentIntakeService.submitDocument(VALID_XML, "REST", correlationId);

        // Verify trace events: RECEIVED, VALIDATED, FORWARDED (3 events)
        verify(eventPublisher, times(3)).publishTraceEvent(any(DocumentReceivedTraceEvent.class));

        // Verify saga command was published
        verify(eventPublisher, times(1)).publishStartSagaCommand(any(StartSagaCommand.class));

        // Capture and verify trace event contents
        ArgumentCaptor<DocumentReceivedTraceEvent> traceCaptor = ArgumentCaptor.forClass(DocumentReceivedTraceEvent.class);
        verify(eventPublisher, times(3)).publishTraceEvent(traceCaptor.capture());

        List<DocumentReceivedTraceEvent> events = traceCaptor.getAllValues();
        assertThat(events).hasSize(3);

        // First event should be RECEIVED
        assertThat(events.get(0).getStatus()).isEqualTo(EventStatus.RECEIVED.getValue());
        assertThat(events.get(0).getCorrelationId()).isEqualTo(correlationId);

        // Second event should be VALIDATED
        assertThat(events.get(1).getStatus()).isEqualTo(EventStatus.VALIDATED.getValue());
        assertThat(events.get(1).getCorrelationId()).isEqualTo(correlationId);

        // Third event should be FORWARDED
        assertThat(events.get(2).getStatus()).isEqualTo(EventStatus.FORWARDED.getValue());
        assertThat(events.get(2).getCorrelationId()).isEqualTo(correlationId);

        // Capture and verify saga command
        ArgumentCaptor<StartSagaCommand> sagaCaptor = ArgumentCaptor.forClass(StartSagaCommand.class);
        verify(eventPublisher).publishStartSagaCommand(sagaCaptor.capture());

        StartSagaCommand sagaCommand = sagaCaptor.getValue();
        assertThat(sagaCommand.getCorrelationId()).isEqualTo(correlationId);
        assertThat(sagaCommand.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE.name());
        assertThat(sagaCommand.getSource()).isEqualTo("REST");
    }

    @Test
    @DisplayName("Submit invalid document publishes two trace events, no saga command")
    void testSubmitInvalidDocumentPublishesOnlyTraceEvent() {
        when(validationService.validate(any())).thenReturn(ValidationResult.invalid(List.of("Validation error")));

        String correlationId = "corr-invalid-123";
        documentIntakeService.submitDocument(VALID_XML, "KAFKA", correlationId);

        // Verify two trace events: RECEIVED and INVALID
        verify(eventPublisher, times(2)).publishTraceEvent(any(DocumentReceivedTraceEvent.class));

        // Verify saga command was NOT published
        verify(eventPublisher, never()).publishStartSagaCommand(any(StartSagaCommand.class));

        // Capture and verify trace events
        ArgumentCaptor<DocumentReceivedTraceEvent> traceCaptor = ArgumentCaptor.forClass(DocumentReceivedTraceEvent.class);
        verify(eventPublisher, times(2)).publishTraceEvent(traceCaptor.capture());

        List<DocumentReceivedTraceEvent> events = traceCaptor.getAllValues();
        assertThat(events).hasSize(2);

        // First event should be RECEIVED
        assertThat(events.get(0).getStatus()).isEqualTo(EventStatus.RECEIVED.getValue());
        assertThat(events.get(0).getCorrelationId()).isEqualTo(correlationId);

        // Second event should be INVALID
        assertThat(events.get(1).getStatus()).isEqualTo(EventStatus.INVALID.getValue());
        assertThat(events.get(1).getCorrelationId()).isEqualTo(correlationId);
        assertThat(events.get(1).getSource()).isEqualTo("KAFKA");
    }

    @Test
    @DisplayName("Submit document with warnings publishes correct events")
    void testSubmitDocumentWithWarningsPublishesCorrectEvents() {
        ValidationResult warningResult = ValidationResult.validWithWarnings(List.of("Warning 1"));
        when(validationService.validate(any())).thenReturn(warningResult);

        documentIntakeService.submitDocument(VALID_XML, "REST", "corr-warning");

        // Documents with warnings are still valid, so full event sequence should be published
        verify(eventPublisher, times(3)).publishTraceEvent(any(DocumentReceivedTraceEvent.class));
        verify(eventPublisher, times(1)).publishStartSagaCommand(any(StartSagaCommand.class));
    }

    @Test
    @DisplayName("Submit document failure before validation publishes no trace events")
    void testSubmitDocumentFailureBeforeValidationPublishesNoEvents() {
        when(validationService.extractDocumentNumber(any())).thenReturn(null);

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-fail"))
            .isInstanceOf(IllegalArgumentException.class);

        // No trace events should be published when document number extraction fails
        verify(eventPublisher, never()).publishTraceEvent(any(DocumentReceivedTraceEvent.class));
        verify(eventPublisher, never()).publishStartSagaCommand(any(StartSagaCommand.class));
    }
}
