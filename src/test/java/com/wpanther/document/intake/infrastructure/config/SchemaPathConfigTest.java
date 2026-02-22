package com.wpanther.document.intake.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SchemaPathConfig Tests")
class SchemaPathConfigTest {

    private SchemaPathConfig config;

    @BeforeEach
    void setUp() {
        config = new SchemaPathConfig();
        config.setTaxInvoice("path/to/taxinvoice.xsd");
        config.setReceipt("path/to/receipt.xsd");
        config.setInvoice("path/to/invoice.xsd");
        config.setDebitCreditNote("path/to/debitcreditnote.xsd");
        config.setCancellationNote("path/to/cancellationnote.xsd");
        config.setAbbreviatedTaxInvoice("path/to/abbreviatedtaxinvoice.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for TAX_INVOICE")
    void shouldReturnCorrectSchemaPathForTaxInvoice() {
        String path = config.getSchemaPath("TAX_INVOICE");

        assertThat(path).isEqualTo("path/to/taxinvoice.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for RECEIPT")
    void shouldReturnCorrectSchemaPathForReceipt() {
        String path = config.getSchemaPath("RECEIPT");

        assertThat(path).isEqualTo("path/to/receipt.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for INVOICE")
    void shouldReturnCorrectSchemaPathForInvoice() {
        String path = config.getSchemaPath("INVOICE");

        assertThat(path).isEqualTo("path/to/invoice.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for DEBIT_CREDIT_NOTE")
    void shouldReturnCorrectSchemaPathForDebitCreditNote() {
        String path = config.getSchemaPath("DEBIT_CREDIT_NOTE");

        assertThat(path).isEqualTo("path/to/debitcreditnote.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for CANCELLATION_NOTE")
    void shouldReturnCorrectSchemaPathForCancellationNote() {
        String path = config.getSchemaPath("CANCELLATION_NOTE");

        assertThat(path).isEqualTo("path/to/cancellationnote.xsd");
    }

    @Test
    @DisplayName("Should return correct schema path for ABBREVIATED_TAX_INVOICE")
    void shouldReturnCorrectSchemaPathForAbbreviatedTaxInvoice() {
        String path = config.getSchemaPath("ABBREVIATED_TAX_INVOICE");

        assertThat(path).isEqualTo("path/to/abbreviatedtaxinvoice.xsd");
    }

    @Test
    @DisplayName("Should throw exception for unknown document type")
    void shouldThrowExceptionForUnknownDocumentType() {
        assertThatThrownBy(() -> config.getSchemaPath("UNKNOWN_TYPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown document type")
                .hasMessageContaining("TAX_INVOICE")
                .hasMessageContaining("RECEIPT");
    }
}
