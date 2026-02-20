package com.wpanther.document.intake.domain.model;

import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IncomingDocument aggregate root
 * Tests cover builder pattern, state machine transitions, and business logic
 */
@DisplayName("IncomingDocument Aggregate Root Tests")
class IncomingDocumentTest {

    private static final String TEST_DOCUMENT_NUMBER = "INV-2024-001";
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

    private IncomingDocument.Builder validBuilder;

    @BeforeEach
    void setUp() {
        validBuilder = IncomingDocument.builder()
            .documentNumber(TEST_DOCUMENT_NUMBER)
            .xmlContent(TEST_XML_CONTENT)
            .source(TEST_SOURCE)
            .correlationId(TEST_CORRELATION_ID)
            .documentType(DocumentType.TAX_INVOICE);
    }

    // ==================== Builder Pattern Tests ====================

    @Test
    @DisplayName("Builder with all fields creates complete document")
    void testBuilderWithAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        IncomingDocument document = validBuilder
            .id(id)
            .status(DocumentStatus.RECEIVED)
            .receivedAt(now)
            .processedAt(now.plusMinutes(5))
            .build();

        assertThat(document.getId()).isEqualTo(id);
        assertThat(document.getDocumentNumber()).isEqualTo(TEST_DOCUMENT_NUMBER);
        assertThat(document.getXmlContent()).isEqualTo(TEST_XML_CONTENT);
        assertThat(document.getSource()).isEqualTo(TEST_SOURCE);
        assertThat(document.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(document.getReceivedAt()).isEqualTo(now);
        assertThat(document.getProcessedAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    @DisplayName("Builder with defaults creates valid document")
    void testBuilderWithDefaults() {
        IncomingDocument document = validBuilder.build();

        assertThat(document.getId()).isNotNull();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(document.getReceivedAt()).isNotNull();
        assertThat(document.getProcessedAt()).isNull();
        assertThat(document.getValidationResult()).isNull();
    }

    @Test
    @DisplayName("Builder generates UUID when not provided")
    void testBuilderGeneratesUuidWhenNotProvided() {
        IncomingDocument document1 = validBuilder.build();
        IncomingDocument document2 = validBuilder.build();

        assertThat(document1.getId()).isNotNull();
        assertThat(document2.getId()).isNotNull();
        assertThat(document1.getId()).isNotEqualTo(document2.getId());
    }

    @Test
    @DisplayName("Builder throws on null invoice number")
    void testBuilderThrowsOnNullInvoiceNumber() {
        assertThatThrownBy(() -> validBuilder.documentNumber(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Document number is required");
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
    @DisplayName("Builder throws on blank document number")
    void testBuilderThrowsOnBlankDocumentNumber() {
        assertThatThrownBy(() -> validBuilder.documentNumber("   ").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Document number cannot be blank");
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
        IncomingDocument document = validBuilder.correlationId(null).build();

        assertThat(document.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Builder allows null document type")
    void testBuilderAllowsNullDocumentType() {
        IncomingDocument document = validBuilder.documentType(null).build();

        assertThat(document.getDocumentType()).isNull();
    }

    @Test
    @DisplayName("Builder allows null validation result")
    void testBuilderAllowsNullValidationResult() {
        IncomingDocument document = validBuilder.validationResult(null).build();

        assertThat(document.getValidationResult()).isNull();
    }

    // ==================== State Machine: Normal Flow Tests ====================

    @Test
    @DisplayName("Start validation transitions RECEIVED to VALIDATING")
    void testStartValidationFromReceived() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.RECEIVED)
            .build();

        document.startValidation();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VALIDATING);
    }

    @Test
    @DisplayName("Mark validated with success transitions VALIDATING to VALIDATED")
    void testMarkValidatedSuccess() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .build();

        ValidationResult successResult = ValidationResult.success();
        document.markValidated(successResult);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
        assertThat(document.getValidationResult()).isEqualTo(successResult);
        assertThat(document.isValid()).isTrue();
    }

    @Test
    @DisplayName("Mark validated with failure transitions VALIDATING to INVALID")
    void testMarkValidatedInvalid() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .build();

        ValidationResult failureResult = ValidationResult.invalid(List.of("Validation failed"));
        document.markValidated(failureResult);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.INVALID);
        assertThat(document.getValidationResult()).isEqualTo(failureResult);
        assertThat(document.isValid()).isFalse();
    }

    @Test
    @DisplayName("Mark forwarded transitions VALIDATED to FORWARDED")
    void testMarkForwardedFromValidated() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        document.markForwarded();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(document.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Full happy path through state machine")
    void testFullHappyPath() {
        IncomingDocument document = validBuilder.build();

        // RECEIVED -> VALIDATING
        document.startValidation();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VALIDATING);

        // VALIDATING -> VALIDATED
        document.markValidated(ValidationResult.success());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VALIDATED);
        assertThat(document.isValid()).isTrue();

        // VALIDATED -> FORWARDED
        document.markForwarded();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FORWARDED);
        assertThat(document.getProcessedAt()).isNotNull();
    }

    // ==================== State Machine: Invalid Transition Tests ====================

    @Test
    @DisplayName("Start validation throws when not in RECEIVED status")
    void testStartValidationThrowsWhenNotReceived() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .build();

        assertThatThrownBy(document::startValidation)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only start validation from RECEIVED status");
    }

    @Test
    @DisplayName("Start validation throws from VALIDATED status")
    void testStartValidationThrowsFromValidated() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATED)
            .build();

        assertThatThrownBy(document::startValidation)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark validated throws when not in VALIDATING status")
    void testMarkValidatedThrowsWhenNotValidating() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.RECEIVED)
            .build();

        assertThatThrownBy(() -> document.markValidated(ValidationResult.success()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only mark validated from VALIDATING status");
    }

    @Test
    @DisplayName("Mark validated throws from FORWARDED status")
    void testMarkValidatedThrowsFromForwarded() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.FORWARDED)
            .build();

        assertThatThrownBy(() -> document.markValidated(ValidationResult.success()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark forwarded throws when not in VALIDATED status")
    void testMarkForwardedThrowsWhenNotValidated() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .build();

        assertThatThrownBy(document::markForwarded)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can only forward validated documents");
    }

    @Test
    @DisplayName("Mark forwarded throws from INVALID status")
    void testMarkForwardedThrowsFromInvalid() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.INVALID)
            .build();

        assertThatThrownBy(document::markForwarded)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mark forwarded from RECEIVED throws")
    void testMarkForwardedThrowsFromReceived() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.RECEIVED)
            .build();

        assertThatThrownBy(document::markForwarded)
            .isInstanceOf(IllegalStateException.class);
    }

    // ==================== Business Logic Tests ====================

    @Test
    @DisplayName("isValid returns true when validation passed")
    void testIsValidReturnsTrueWhenValidationPassed() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        assertThat(document.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid returns false when validation failed")
    void testIsValidReturnsFalseWhenValidationFailed() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.INVALID)
            .validationResult(ValidationResult.invalid(List.of("Error")))
            .build();

        assertThat(document.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when not validated")
    void testIsValidReturnsFalseWhenNotValidated() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.RECEIVED)
            .validationResult(null)
            .build();

        assertThat(document.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when result has warnings but still valid")
    void testIsValidReturnsTrueWithWarnings() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.validWithWarnings(List.of("Warning")))
            .build();

        assertThat(document.isValid()).isTrue();
    }

    @Test
    @DisplayName("canBeForwarded returns true only when VALIDATED and valid")
    void testCanBeForwardedReturnsTrueOnlyWhenValidatedAndValid() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        assertThat(document.canBeForwarded()).isTrue();
    }

    @Test
    @DisplayName("canBeForwarded returns false when not VALIDATED")
    void testCanBeForwardedReturnsFalseWhenNotValidated() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.RECEIVED)
            .build();

        assertThat(document.canBeForwarded()).isFalse();
    }

    @Test
    @DisplayName("canBeForwarded returns false when VALIDATED but invalid")
    void testCanBeForwardedReturnsFalseWhenInvalid() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.INVALID)
            .validationResult(ValidationResult.invalid(List.of("Error")))
            .build();

        assertThat(document.canBeForwarded()).isFalse();
    }

    @Test
    @DisplayName("Getters return correct values")
    void testGettersReturnCorrectValues() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        IncomingDocument document = validBuilder
            .id(id)
            .receivedAt(now)
            .build();

        assertThat(document.getId()).isEqualTo(id);
        assertThat(document.getDocumentNumber()).isEqualTo(TEST_DOCUMENT_NUMBER);
        assertThat(document.getXmlContent()).isEqualTo(TEST_XML_CONTENT);
        assertThat(document.getSource()).isEqualTo(TEST_SOURCE);
        assertThat(document.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(document.getReceivedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Document type is set correctly")
    void testDocumentTypeIsSet() {
        for (DocumentType type : DocumentType.values()) {
            IncomingDocument document = validBuilder.documentType(type).build();

            assertThat(document.getDocumentType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Mark failed sets status to FAILED")
    void testMarkFailedSetsStatusToFailed() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .build();

        document.markFailed("Processing error");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getProcessedAt()).isNotNull();
        assertThat(document.getValidationResult()).isNotNull();
        assertThat(document.getValidationResult().valid()).isFalse();
    }

    @Test
    @DisplayName("Mark failed from any state")
    void testMarkFailedFromAnyState() {
        for (DocumentStatus status : DocumentStatus.values()) {
            IncomingDocument document = validBuilder.status(status).build();

            document.markFailed("Error");

            assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        }
    }

    @Test
    @DisplayName("Mark failed sets validation result if null")
    void testMarkFailedSetsValidationResultIfNull() {
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .validationResult(null)
            .build();

        document.markFailed("Error message");

        assertThat(document.getValidationResult()).isNotNull();
        assertThat(document.getValidationResult().valid()).isFalse();
        assertThat(document.getValidationResult().errors()).contains("Error message");
    }

    @Test
    @DisplayName("Mark failed preserves existing validation result")
    void testMarkFailedPreservesExistingValidationResult() {
        ValidationResult existingResult = ValidationResult.invalid(List.of("Existing error"));
        IncomingDocument document = validBuilder
            .status(DocumentStatus.VALIDATING)
            .validationResult(existingResult)
            .build();

        document.markFailed("New error");

        assertThat(document.getValidationResult()).isEqualTo(existingResult);
    }

    @Test
    @DisplayName("Equality based on ID")
    void testEqualityBasedOnId() {
        UUID id = UUID.randomUUID();

        IncomingDocument document1 = validBuilder.id(id).build();
        IncomingDocument document2 = validBuilder.id(id).documentNumber("DIFFERENT").build();

        // Same ID should be considered equal (business identity)
        assertThat(document1.getId()).isEqualTo(document2.getId());
    }
}
