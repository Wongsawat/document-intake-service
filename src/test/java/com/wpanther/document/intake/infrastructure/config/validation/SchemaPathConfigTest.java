package com.wpanther.document.intake.infrastructure.config.validation;

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

    @Test
    @DisplayName("Getters should return configured values")
    void testGettersReturnConfiguredValues() {
        assertThat(config.getTaxInvoice()).isEqualTo("path/to/taxinvoice.xsd");
        assertThat(config.getReceipt()).isEqualTo("path/to/receipt.xsd");
        assertThat(config.getInvoice()).isEqualTo("path/to/invoice.xsd");
        assertThat(config.getDebitCreditNote()).isEqualTo("path/to/debitcreditnote.xsd");
        assertThat(config.getCancellationNote()).isEqualTo("path/to/cancellationnote.xsd");
        assertThat(config.getAbbreviatedTaxInvoice()).isEqualTo("path/to/abbreviatedtaxinvoice.xsd");
    }

    @Test
    @DisplayName("Should handle null schema path")
    void testShouldHandleNullSchemaPath() {
        SchemaPathConfig emptyConfig = new SchemaPathConfig();
        // When schema path is null, getSchemaPath returns null (does not throw)
        String path = emptyConfig.getSchemaPath("TAX_INVOICE");
        assertThat(path).isNull();
    }

    @Test
    @DisplayName("Should be case sensitive for document types")
    void testShouldBeCaseSensitiveForDocumentTypes() {
        assertThatThrownBy(() -> config.getSchemaPath("tax_invoice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("All getters work independently")
    void testAllGettersWorkIndependently() {
        SchemaPathConfig testConfig = new SchemaPathConfig();
        testConfig.setTaxInvoice("/path/to/tax.xsd");
        testConfig.setReceipt("/path/to/receipt.xsd");
        testConfig.setInvoice("/path/to/invoice.xsd");
        testConfig.setDebitCreditNote("/path/to/debit.xsd");
        testConfig.setCancellationNote("/path/to/cancel.xsd");
        testConfig.setAbbreviatedTaxInvoice("/path/to/abbrev.xsd");

        assertThat(testConfig.getTaxInvoice()).isEqualTo("/path/to/tax.xsd");
        assertThat(testConfig.getReceipt()).isEqualTo("/path/to/receipt.xsd");
        assertThat(testConfig.getInvoice()).isEqualTo("/path/to/invoice.xsd");
        assertThat(testConfig.getDebitCreditNote()).isEqualTo("/path/to/debit.xsd");
        assertThat(testConfig.getCancellationNote()).isEqualTo("/path/to/cancel.xsd");
        assertThat(testConfig.getAbbreviatedTaxInvoice()).isEqualTo("/path/to/abbrev.xsd");
    }

    @Test
    @DisplayName("Should handle empty string document type")
    void testShouldHandleEmptyStringDocumentType() {
        assertThatThrownBy(() -> config.getSchemaPath(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Multiple SchemaPathConfig instances are independent")
    void testMultipleInstancesAreIndependent() {
        SchemaPathConfig config1 = new SchemaPathConfig();
        SchemaPathConfig config2 = new SchemaPathConfig();

        config1.setTaxInvoice("path1.xsd");
        config2.setTaxInvoice("path2.xsd");

        assertThat(config1.getTaxInvoice()).isEqualTo("path1.xsd");
        assertThat(config2.getTaxInvoice()).isEqualTo("path2.xsd");
    }
}
