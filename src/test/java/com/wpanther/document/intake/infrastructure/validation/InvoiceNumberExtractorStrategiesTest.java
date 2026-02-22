package com.wpanther.document.intake.infrastructure.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InvoiceNumberExtractorStrategies Tests")
class InvoiceNumberExtractorStrategiesTest {

    @Test
    @DisplayName("Should return null for null document")
    void shouldReturnNullForNullDocument() {
        String result = InvoiceNumberExtractorStrategies.RECEIPT.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for wrong document type")
    void shouldReturnNullForWrongDocumentType() {
        String result = InvoiceNumberExtractorStrategies.RECEIPT.extractInvoiceNumber("wrong type");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null TAX_INVOICE document")
    void shouldReturnNullForNullTaxInvoiceDocument() {
        String result = InvoiceNumberExtractorStrategies.TAX_INVOICE.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null INVOICE document")
    void shouldReturnNullForNullInvoiceDocument() {
        String result = InvoiceNumberExtractorStrategies.INVOICE.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null DEBIT_CREDIT_NOTE document")
    void shouldReturnNullForNullDebitCreditNoteDocument() {
        String result = InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null CANCELLATION_NOTE document")
    void shouldReturnNullForNullCancellationNoteDocument() {
        String result = InvoiceNumberExtractorStrategies.CANCELLATION_NOTE.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null ABBREVIATED_TAX_INVOICE document")
    void shouldReturnNullForNullAbbreviatedTaxInvoiceDocument() {
        String result = InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE.extractInvoiceNumber(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("All strategies return null for wrong document types")
    void testAllStrategiesReturnNullForWrongDocumentTypes() {
        // Test all strategies with wrong type
        assertThat(InvoiceNumberExtractorStrategies.TAX_INVOICE.extractInvoiceNumber("wrong type")).isNull();
        assertThat(InvoiceNumberExtractorStrategies.RECEIPT.extractInvoiceNumber("wrong type")).isNull();
        assertThat(InvoiceNumberExtractorStrategies.INVOICE.extractInvoiceNumber("wrong type")).isNull();
        assertThat(InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE.extractInvoiceNumber("wrong type")).isNull();
        assertThat(InvoiceNumberExtractorStrategies.CANCELLATION_NOTE.extractInvoiceNumber("wrong type")).isNull();
        assertThat(InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE.extractInvoiceNumber("wrong type")).isNull();
    }

    @Test
    @DisplayName("All strategies return null for null input")
    void testAllStrategiesReturnNullForNullInput() {
        // Test all strategies with null input
        assertThat(InvoiceNumberExtractorStrategies.TAX_INVOICE.extractInvoiceNumber(null)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.RECEIPT.extractInvoiceNumber(null)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.INVOICE.extractInvoiceNumber(null)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE.extractInvoiceNumber(null)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.CANCELLATION_NOTE.extractInvoiceNumber(null)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE.extractInvoiceNumber(null)).isNull();
    }

    @Test
    @DisplayName("Strategies handle objects without getExchangedDocument method")
    void testStrategiesHandleObjectsWithoutRequiredMethod() {
        // Test with object that doesn't have getExchangedDocument method
        Object invalidObject = new Object();

        // All strategies should return null when the expected method doesn't exist
        assertThat(InvoiceNumberExtractorStrategies.TAX_INVOICE.extractInvoiceNumber(invalidObject)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.RECEIPT.extractInvoiceNumber(invalidObject)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.INVOICE.extractInvoiceNumber(invalidObject)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE.extractInvoiceNumber(invalidObject)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.CANCELLATION_NOTE.extractInvoiceNumber(invalidObject)).isNull();
        assertThat(InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE.extractInvoiceNumber(invalidObject)).isNull();
    }

    @Test
    @DisplayName("Strategies handle objects where getExchangedDocument returns null")
    void testStrategiesHandleNullExchangedDocument() {
        // Create a mock object with getExchangedDocument that returns null
        // This tests the reflection path where document is null
        Object mockWithNullExchangedDocument = new Object() {
            public Object getExchangedDocument() {
                return null;
            }
        };

        // Should return null when exchangedDocument is null
        assertThat(InvoiceNumberExtractorStrategies.TAX_INVOICE.extractInvoiceNumber(mockWithNullExchangedDocument)).isNull();
    }

    @Test
    @DisplayName("Enum has all required values")
    void testEnumHasAllRequiredValues() {
        assertThat(InvoiceNumberExtractorStrategies.values()).hasSize(6);
        assertThat(InvoiceNumberExtractorStrategies.values()).containsExactlyInAnyOrder(
            InvoiceNumberExtractorStrategies.TAX_INVOICE,
            InvoiceNumberExtractorStrategies.RECEIPT,
            InvoiceNumberExtractorStrategies.INVOICE,
            InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE,
            InvoiceNumberExtractorStrategies.CANCELLATION_NOTE,
            InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE
        );
    }
}
