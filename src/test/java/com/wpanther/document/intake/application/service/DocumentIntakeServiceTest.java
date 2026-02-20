package com.wpanther.document.intake.application.service;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.repository.IncomingDocumentRepository;
import com.wpanther.document.intake.domain.service.XmlValidationService;
import com.wpanther.document.intake.infrastructure.messaging.EventPublisher;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
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
 * Unit tests for DocumentIntakeService
 * Tests use mocks for repository and validation service dependencies
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Document Intake Service Tests")
class DocumentIntakeServiceTest {

    @Mock
    private IncomingDocumentRepository documentRepository;

    @Mock
    private XmlValidationService validationService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private DocumentIntakeService documentIntakeService;

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
        when(validationService.extractInvoiceNumber(any())).thenReturn("INV-2024-001");
        when(validationService.extractDocumentType(any())).thenReturn(DocumentType.TAX_INVOICE);
        when(validationService.validate(any())).thenReturn(ValidationResult.success());
        when(documentRepository.existsByDocumentNumber(any())).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

        verify(validationService).extractInvoiceNumber(VALID_XML);
    }

    @Test
    @DisplayName("Submit document with null document number throws exception")
    void testSubmitInvoiceHandlesNullInvoiceNumber() {
        when(validationService.extractInvoiceNumber(any())).thenReturn(null);

        assertThatThrownBy(() -> documentIntakeService.submitDocument(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract document number");

        // Verify no save was attempted
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit document with blank document number throws exception")
    void testSubmitInvoiceHandlesBlankInvoiceNumber() {
        when(validationService.extractInvoiceNumber(any())).thenReturn("   ");

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

        verify(validationService).extractInvoiceNumber(null);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit document with empty XML throws exception")
    void testSubmitInvoiceWithEmptyXml() {
        // Override the default mock to return null for empty input
        when(validationService.extractInvoiceNumber("")).thenReturn(null);

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

    // ==================== Mark Forwarded Tests ====================

    @Test
    @DisplayName("Mark forwarded updates status")
    void testMarkForwardedUpdatesStatus() {
        UUID documentId = UUID.randomUUID();
        IncomingDocument document = IncomingDocument.builder()
            .id(documentId)
            .documentNumber("INV-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .status(DocumentStatus.VALIDATED)
            .documentType(DocumentType.TAX_INVOICE)
            .validationResult(ValidationResult.success())
            .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenReturn(document);

        documentIntakeService.markForwarded(documentId);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Mark forwarded throws on invalid ID")
    void testMarkForwardedThrowsOnInvalidId() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentIntakeService.markForwarded(documentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Document not found")
            .hasMessageContaining(documentId.toString());

        verify(documentRepository, never()).save(any());
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

        // Note: submitDocument already marks as forwarded, so the markForwarded method
        // is tested separately in testMarkForwardedUpdatesStatus
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
}
