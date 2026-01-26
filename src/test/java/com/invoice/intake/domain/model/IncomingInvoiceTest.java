package com.invoice.intake.domain.model;

import com.invoice.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IncomingInvoice aggregate root
 * Tests cover builder pattern, state machine transitions, and business logic
 */
@DisplayName("IncomingInvoice Aggregate Root Tests")
class IncomingInvoiceTest {

    private static final String TEST_INVOICE_NUMBER = "INV-2024-001";
    private static final String TEST_XML_CONTENT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:TaxInvoice_CrossIndustryInvoice xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID>v2.1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>INV-2024-001</ram:ID>
            </rsm:ExchangedDocument>
        </rsm:TaxInvoice_CrossIndustryInvoice>
        """;
    private static final String TEST_SOURCE = "REST";
    private static final String TEST_CORRELATION_ID = "corr-123";

    private IncomingInvoice.Builder validBuilder;

    @BeforeEach
    void setUp() {
        validBuilder = IncomingInvoice.builder()
            .invoiceNumber(TEST_INVOICE_NUMBER)
            .xmlContent(TEST_XML_CONTENT)
            .source(TEST_SOURCE)
            .correlationId(TEST_CORRELATION_ID)
            .documentType(DocumentType.TAX_INVOICE);
    }

    // ==================== Builder Pattern Tests ====================

    @Test
    @DisplayName("Builder with all fields creates complete invoice")
    void testBuilderWithAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        IncomingInvoice invoice = validBuilder
            .id(id)
            .status(InvoiceStatus.RECEIVED)
            .receivedAt(now)
            .processedAt(now.plusMinutes(5))
            .build();

        assertThat(invoice.getId()).isEqualTo(id);
        assertThat(invoice.getInvoiceNumber()).isEqualTo(TEST_INVOICE_NUMBER);
        assertThat(invoice.getXmlContent()).isEqualTo(TEST_XML_CONTENT);
        assertThat(invoice.getSource()).isEqualTo(TEST_SOURCE);
        assertThat(invoice.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(invoice.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getReceivedAt()).isEqualTo(now);
        assertThat(invoice.getProcessedAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    @DisplayName("Builder with defaults creates valid invoice")
    void testBuilderWithDefaults() {
        IncomingInvoice invoice = validBuilder.build();

        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getReceivedAt()).isNotNull();
        assertThat(invoice.getProcessedAt()).isNull();
        assertThat(invoice.getValidationResult()).isNull();
    }

    @Test
    @DisplayName("Builder generates UUID when not provided")
    void testBuilderGeneratesUuidWhenNotProvided() {
        IncomingInvoice invoice1 = validBuilder.build();
        IncomingInvoice invoice2 = validBuilder.build();

        assertThat(invoice1.getId()).isNotNull();
        assertThat(invoice2.getId()).isNotNull();
        assertThat(invoice1.getId()).isNotEqualTo(invoice2.getId());
    }

    @Test
    @DisplayName("Builder throws on null invoice number")
    void testBuilderThrowsOnNullInvoiceNumber() {
        assertThatThrownBy(() -> validBuilder.invoiceNumber(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Invoice number is required");
    }

    @Test
    @DisplayName("Builder throws on null XML content")
    void testBuilderThrowsOnNullXmlContent() {
        assertThatThrownBy(() -> validBuilder.xmlContent(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("XML content is required");
    }

    @Test
    @DisplayName("Builder throws on null source")
    void testBuilderThrowsOnNullSource() {
        assertThatThrownBy(() -> validBuilder.source(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Source is required");
    }

    @Test
    @DisplayName("Builder throws on blank invoice number")
    void testBuilderThrowsOnBlankInvoiceNumber() {
        assertThatThrownBy(() -> validBuilder.invoiceNumber("   ").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invoice number cannot be blank");
    }

    @Test
    @DisplayName("Builder throws on blank XML content")
    void testBuilderThrowsOnBlankXmlContent() {
        assertThatThrownBy(() -> validBuilder.xmlContent("   ").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("XML content cannot be blank");
    }

    @Test
    @DisplayName("Builder allows null correlation ID")
    void testBuilderAllowsNullCorrelationId() {
        IncomingInvoice invoice = validBuilder.correlationId(null).build();

        assertThat(invoice.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Builder allows null document type")
    void testBuilderAllowsNullDocumentType() {
        IncomingInvoice invoice = validBuilder.documentType(null).build();

        assertThat(invoice.getDocumentType()).isNull();
    }

    @Test
    @DisplayName("Builder allows null validation result")
    void testBuilderAllowsNullValidationResult() {
        IncomingInvoice invoice = validBuilder.validationResult(null).build();

        assertThat(invoice.getValidationResult()).isNull();
    }

    // ==================== State Machine: Normal Flow Tests ====================

    @Test
    @DisplayName("Start validation transitions RECEIVED to VALIDATING")
    void testStartValidationFromReceived() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.RECEIVED)
            .build();

        invoice.startValidation();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALIDATING);
    }

    @Test
    @DisplayName("Mark validated with success transitions VALIDATING to VALIDATED")
    void testMarkValidatedSuccess() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .build();

        ValidationResult successResult = ValidationResult.success();
        invoice.markValidated(successResult);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALIDATED);
        assertThat(invoice.getValidationResult()).isEqualTo(successResult);
        assertThat(invoice.isValid()).isTrue();
    }

    @Test
    @DisplayName("Mark validated with failure transitions VALIDATING to INVALID")
    void testMarkValidatedInvalid() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .build();

        ValidationResult failureResult = ValidationResult.invalid(List.of("Validation failed"));
        invoice.markValidated(failureResult);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.INVALID);
        assertThat(invoice.getValidationResult()).isEqualTo(failureResult);
        assertThat(invoice.isValid()).isFalse();
    }

    @Test
    @DisplayName("Mark forwarded transitions VALIDATED to FORWARDED")
    void testMarkForwardedFromValidated() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        invoice.markForwarded();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FORWARDED);
        assertThat(invoice.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Full happy path through state machine")
    void testFullHappyPath() {
        IncomingInvoice invoice = validBuilder.build();

        // RECEIVED -> VALIDATING
        invoice.startValidation();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALIDATING);

        // VALIDATING -> VALIDATED
        invoice.markValidated(ValidationResult.success());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALIDATED);
        assertThat(invoice.isValid()).isTrue();

        // VALIDATED -> FORWARDED
        invoice.markForwarded();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FORWARDED);
        assertThat(invoice.getProcessedAt()).isNotNull();
    }

    // ==================== State Machine: Invalid Transition Tests ====================

    @Test
    @DisplayName("Start validation throws when not in RECEIVED status")
    void testStartValidationThrowsWhenNotReceived() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .build();

        assertThatThrownBy(invoice::startValidation)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only start validation from RECEIVED status");
    }

    @Test
    @DisplayName("Start validation throws from VALIDATED status")
    void testStartValidationThrowsFromValidated() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATED)
            .build();

        assertThatThrownBy(invoice::startValidation)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark validated throws when not in VALIDATING status")
    void testMarkValidatedThrowsWhenNotValidating() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.RECEIVED)
            .build();

        assertThatThrownBy(() -> invoice.markValidated(ValidationResult.success()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only mark validated from VALIDATING status");
    }

    @Test
    @DisplayName("Mark validated throws from FORWARDED status")
    void testMarkValidatedThrowsFromForwarded() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.FORWARDED)
            .build();

        assertThatThrownBy(() -> invoice.markValidated(ValidationResult.success()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark forwarded throws when not in VALIDATED status")
    void testMarkForwardedThrowsWhenNotValidated() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .build();

        assertThatThrownBy(invoice::markForwarded)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only forward validated invoices");
    }

    @Test
    @DisplayName("Mark forwarded throws from INVALID status")
    void testMarkForwardedThrowsFromInvalid() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.INVALID)
            .build();

        assertThatThrownBy(invoice::markForwarded)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark forwarded from RECEIVED throws")
    void testMarkForwardedThrowsFromReceived() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.RECEIVED)
            .build();

        assertThatThrownBy(invoice::markForwarded)
            .isInstanceOf(IllegalStateException.class);
    }

    // ==================== Business Logic Tests ====================

    @Test
    @DisplayName("isValid returns true when validation passed")
    void testIsValidReturnsTrueWhenValidationPassed() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        assertThat(invoice.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid returns false when validation failed")
    void testIsValidReturnsFalseWhenValidationFailed() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.INVALID)
            .validationResult(ValidationResult.invalid(List.of("Error")))
            .build();

        assertThat(invoice.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when not validated")
    void testIsValidReturnsFalseWhenNotValidated() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.RECEIVED)
            .validationResult(null)
            .build();

        assertThat(invoice.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when result has warnings but still valid")
    void testIsValidReturnsTrueWithWarnings() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.validWithWarnings(List.of("Warning")))
            .build();

        assertThat(invoice.isValid()).isTrue();
    }

    @Test
    @DisplayName("canBeForwarded returns true only when VALIDATED and valid")
    void testCanBeForwardedReturnsTrueOnlyWhenValidatedAndValid() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        assertThat(invoice.canBeForwarded()).isTrue();
    }

    @Test
    @DisplayName("canBeForwarded returns false when not VALIDATED")
    void testCanBeForwardedReturnsFalseWhenNotValidated() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.RECEIVED)
            .build();

        assertThat(invoice.canBeForwarded()).isFalse();
    }

    @Test
    @DisplayName("canBeForwarded returns false when VALIDATED but invalid")
    void testCanBeForwardedReturnsFalseWhenInvalid() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.INVALID)
            .validationResult(ValidationResult.invalid(List.of("Error")))
            .build();

        assertThat(invoice.canBeForwarded()).isFalse();
    }

    @Test
    @DisplayName("Getters return correct values")
    void testGettersReturnCorrectValues() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        IncomingInvoice invoice = validBuilder
            .id(id)
            .receivedAt(now)
            .build();

        assertThat(invoice.getId()).isEqualTo(id);
        assertThat(invoice.getInvoiceNumber()).isEqualTo(TEST_INVOICE_NUMBER);
        assertThat(invoice.getXmlContent()).isEqualTo(TEST_XML_CONTENT);
        assertThat(invoice.getSource()).isEqualTo(TEST_SOURCE);
        assertThat(invoice.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(invoice.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getReceivedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Document type is set correctly")
    void testDocumentTypeIsSet() {
        for (DocumentType type : DocumentType.values()) {
            IncomingInvoice invoice = validBuilder.documentType(type).build();

            assertThat(invoice.getDocumentType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Mark failed sets status to FAILED")
    void testMarkFailedSetsStatusToFailed() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .build();

        invoice.markFailed("Processing error");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getProcessedAt()).isNotNull();
        assertThat(invoice.getValidationResult()).isNotNull();
        assertThat(invoice.getValidationResult().valid()).isFalse();
    }

    @Test
    @DisplayName("Mark failed from any state")
    void testMarkFailedFromAnyState() {
        for (InvoiceStatus status : InvoiceStatus.values()) {
            IncomingInvoice invoice = validBuilder.status(status).build();

            invoice.markFailed("Error");

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        }
    }

    @Test
    @DisplayName("Mark failed sets validation result if null")
    void testMarkFailedSetsValidationResultIfNull() {
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .validationResult(null)
            .build();

        invoice.markFailed("Error message");

        assertThat(invoice.getValidationResult()).isNotNull();
        assertThat(invoice.getValidationResult().valid()).isFalse();
        assertThat(invoice.getValidationResult().errors()).contains("Error message");
    }

    @Test
    @DisplayName("Mark failed preserves existing validation result")
    void testMarkFailedPreservesExistingValidationResult() {
        ValidationResult existingResult = ValidationResult.invalid(List.of("Existing error"));
        IncomingInvoice invoice = validBuilder
            .status(InvoiceStatus.VALIDATING)
            .validationResult(existingResult)
            .build();

        invoice.markFailed("New error");

        assertThat(invoice.getValidationResult()).isEqualTo(existingResult);
    }

    @Test
    @DisplayName("Equality based on ID")
    void testEqualityBasedOnId() {
        UUID id = UUID.randomUUID();

        IncomingInvoice invoice1 = validBuilder.id(id).build();
        IncomingInvoice invoice2 = validBuilder.id(id).invoiceNumber("DIFFERENT").build();

        // Same ID should be considered equal (business identity)
        assertThat(invoice1.getId()).isEqualTo(invoice2.getId());
    }
}
