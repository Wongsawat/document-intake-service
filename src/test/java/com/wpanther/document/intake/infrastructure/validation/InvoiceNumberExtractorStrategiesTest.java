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
}
