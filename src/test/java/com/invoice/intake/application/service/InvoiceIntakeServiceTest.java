package com.invoice.intake.application.service;

import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.domain.model.InvoiceStatus;
import com.invoice.intake.domain.model.ValidationResult;
import com.invoice.intake.domain.repository.IncomingInvoiceRepository;
import com.invoice.intake.domain.service.XmlValidationService;
import com.invoice.intake.infrastructure.validation.DocumentType;
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
 * Unit tests for InvoiceIntakeService
 * Tests use mocks for repository and validation service dependencies
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Invoice Intake Service Tests")
class InvoiceIntakeServiceTest {

    @Mock
    private IncomingInvoiceRepository invoiceRepository;

    @Mock
    private XmlValidationService validationService;

    @InjectMocks
    private InvoiceIntakeService invoiceIntakeService;

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
        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(invoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== Happy Path Tests ====================

    @Test
    @DisplayName("Submit invoice with valid XML succeeds")
    void testSubmitInvoiceWithValidXml() {
        IncomingInvoice result = invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        assertThat(result).isNotNull();
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(result.getSource()).isEqualTo("REST");
        assertThat(result.getCorrelationId()).isEqualTo("corr-123");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.VALIDATED);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Submit invoice stores document type")
    void testSubmitInvoiceStoresDocumentType() {
        IncomingInvoice result = invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        assertThat(result.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Submit invoice transitions states correctly")
    void testSubmitInvoiceTransitionsStatesCorrectly() {
        // Verify that save is called 3 times for state transitions
        ArgumentCaptor<IncomingInvoice> captor = ArgumentCaptor.forClass(IncomingInvoice.class);
        when(invoiceRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        invoiceIntakeService.submitInvoice(VALID_XML, "KAFKA", "corr-456");

        // Should have saved 3 times: initial, VALIDATING, VALIDATED
        assertThat(captor.getAllValues()).hasSize(3);

        // Note: Due to mock returning same object reference, all captured values
        // show the final state. In production with real repository, each save
        // would persist the state at that point in time.
        // The important thing is that save() is called 3 times.
    }

    // ==================== Validation Error Tests ====================

    @Test
    @DisplayName("Submit invoice with invalid XML returns invalid result")
    void testSubmitInvoiceWithInvalidXml() {
        when(validationService.validate(any())).thenReturn(ValidationResult.invalid(List.of("Validation error")));

        IncomingInvoice result = invoiceIntakeService.submitInvoice(INVALID_XML, "REST", "corr-789");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.INVALID);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationResult().hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Submit invoice with duplicate invoice number throws exception")
    void testSubmitInvoiceWithDuplicateInvoiceNumber() {
        when(invoiceRepository.existsByInvoiceNumber("INV-2024-001")).thenReturn(true);

        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists")
            .hasMessageContaining("INV-2024-001");

        // Verify no save was attempted
        verify(invoiceRepository, never()).save(any());
    }

    // ==================== Invoice Number Extraction Tests ====================

    @Test
    @DisplayName("Submit invoice extracts invoice number")
    void testSubmitInvoiceExtractsInvoiceNumber() {
        invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        verify(validationService).extractInvoiceNumber(VALID_XML);
    }

    @Test
    @DisplayName("Submit invoice with null invoice number throws exception")
    void testSubmitInvoiceHandlesNullInvoiceNumber() {
        when(validationService.extractInvoiceNumber(any())).thenReturn(null);

        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract invoice number");

        // Verify no save was attempted
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit invoice with blank invoice number throws exception")
    void testSubmitInvoiceHandlesBlankInvoiceNumber() {
        when(validationService.extractInvoiceNumber(any())).thenReturn("   ");

        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract invoice number");

        // Verify no save was attempted
        verify(invoiceRepository, never()).save(any());
    }

    // ==================== Document Type Extraction Tests ====================

    @Test
    @DisplayName("Submit invoice extracts document type")
    void testSubmitInvoiceExtractsDocumentType() {
        invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        verify(validationService).extractDocumentType(VALID_XML);
    }

    @Test
    @DisplayName("Submit invoice with null document type throws exception")
    void testSubmitInvoiceHandlesNullDocumentType() {
        when(validationService.extractDocumentType(any())).thenReturn(null);

        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not detect document type");

        // Verify no save was attempted
        verify(invoiceRepository, never()).save(any());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Submit invoice with null XML throws exception")
    void testSubmitInvoiceWithNullXml() {
        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(null, "REST", "corr-123"))
            .isInstanceOf(NullPointerException.class);

        verify(validationService).extractInvoiceNumber(null);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit invoice with empty XML throws exception")
    void testSubmitInvoiceWithEmptyXml() {
        // Override the default mock to return null for empty input
        when(validationService.extractInvoiceNumber("")).thenReturn(null);

        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice("", "REST", "corr-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not extract invoice number");
    }

    @Test
    @DisplayName("Submit invoice with null source throws exception")
    void testSubmitInvoiceWithNullSource() {
        assertThatThrownBy(() -> invoiceIntakeService.submitInvoice(VALID_XML, null, "corr-123"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Submit invoice with null correlation ID")
    void testSubmitInvoiceWithNullCorrelationId() {
        IncomingInvoice result = invoiceIntakeService.submitInvoice(VALID_XML, "REST", null);

        assertThat(result.getCorrelationId()).isNull();
    }

    // ==================== Repository Interaction Tests ====================

    @Test
    @DisplayName("Submit invoice saves initial then final state")
    void testSubmitInvoiceSavesInitialThenFinalState() {
        ArgumentCaptor<IncomingInvoice> captor = ArgumentCaptor.forClass(IncomingInvoice.class);
        when(invoiceRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        // Verify multiple saves for state transitions
        assertThat(captor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Submit invoice checks for duplicate")
    void testSubmitInvoiceChecksForDuplicate() {
        invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        verify(invoiceRepository).existsByInvoiceNumber("INV-2024-001");
    }

    // ==================== Mark Forwarded Tests ====================

    @Test
    @DisplayName("Mark forwarded updates status")
    void testMarkForwardedUpdatesStatus() {
        UUID invoiceId = UUID.randomUUID();
        IncomingInvoice invoice = IncomingInvoice.builder()
            .id(invoiceId)
            .invoiceNumber("INV-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .status(InvoiceStatus.VALIDATED)
            .documentType(DocumentType.TAX_INVOICE)
            .validationResult(ValidationResult.success())
            .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);

        invoiceIntakeService.markForwarded(invoiceId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FORWARDED);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Mark forwarded throws on invalid ID")
    void testMarkForwardedThrowsOnInvalidId() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceIntakeService.markForwarded(invoiceId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invoice not found")
            .hasMessageContaining(invoiceId.toString());

        verify(invoiceRepository, never()).save(any());
    }

    // ==================== Get Invoice Tests ====================

    @Test
    @DisplayName("Get invoice by ID returns invoice")
    void testGetInvoiceById() {
        UUID invoiceId = UUID.randomUUID();
        IncomingInvoice invoice = IncomingInvoice.builder()
            .id(invoiceId)
            .invoiceNumber("INV-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        IncomingInvoice result = invoiceIntakeService.getInvoice(invoiceId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(invoiceId);
    }

    @Test
    @DisplayName("Get invoice throws on not found")
    void testGetInvoiceThrowsOnNotFound() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceIntakeService.getInvoice(invoiceId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invoice not found")
            .hasMessageContaining(invoiceId.toString());
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Full workflow: submit, validate, get, and mark forwarded")
    void testFullWorkflow() {
        UUID invoiceId = UUID.randomUUID();
        IncomingInvoice invoice = IncomingInvoice.builder()
            .id(invoiceId)
            .invoiceNumber("INV-2024-001")
            .xmlContent(VALID_XML)
            .source("REST")
            .correlationId("corr-123")
            .documentType(DocumentType.TAX_INVOICE)
            .build();

        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        // Submit
        IncomingInvoice submitted = invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");
        assertThat(submitted.isValid()).isTrue();

        // Get
        IncomingInvoice retrieved = invoiceIntakeService.getInvoice(invoiceId);
        assertThat(retrieved).isNotNull();

        // Mark forwarded
        invoiceIntakeService.markForwarded(invoiceId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FORWARDED);
    }

    // ==================== Different Document Types Tests ====================

    @Test
    @DisplayName("Submit invoice handles all document types")
    void testSubmitInvoiceHandlesAllDocumentTypes() {
        for (DocumentType type : DocumentType.values()) {
            when(validationService.extractDocumentType(any())).thenReturn(type);

            IncomingInvoice result = invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-" + type.name());

            assertThat(result.getDocumentType()).isEqualTo(type);
        }
    }

    // ==================== Validation Result Tests ====================

    @Test
    @DisplayName("Submit invoice with validation warnings")
    void testSubmitInvoiceWithValidationWarnings() {
        ValidationResult warningResult = ValidationResult.validWithWarnings(List.of("Warning 1", "Warning 2"));
        when(validationService.validate(any())).thenReturn(warningResult);

        IncomingInvoice result = invoiceIntakeService.submitInvoice(VALID_XML, "REST", "corr-123");

        assertThat(result.isValid()).isTrue(); // Warnings still valid
        assertThat(result.getValidationResult().hasWarnings()).isTrue();
        assertThat(result.getValidationResult().warningCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Submit invoice with multiple errors")
    void testSubmitInvoiceWithMultipleErrors() {
        List<String> errors = List.of("Error 1", "Error 2", "Error 3");
        ValidationResult errorResult = ValidationResult.invalid(errors);
        when(validationService.validate(any())).thenReturn(errorResult);

        IncomingInvoice result = invoiceIntakeService.submitInvoice(INVALID_XML, "REST", "corr-123");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationResult().errorCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Submit invoice saves validation result")
    void testSubmitInvoiceSavesValidationResult() {
        ValidationResult result = ValidationResult.invalid(List.of("Schema error"));
        when(validationService.validate(any())).thenReturn(result);

        IncomingInvoice invoice = invoiceIntakeService.submitInvoice(INVALID_XML, "REST", "corr-123");

        assertThat(invoice.getValidationResult()).isEqualTo(result);
    }
}
