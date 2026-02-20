package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.etax.validation.DocumentSchematron;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentType enum
 */
class DocumentTypeTest {

    @Test
    void testAllDocumentTypesExist() {
        // Verify all expected document types exist
        assertThat(DocumentType.values()).hasSize(6);
        assertThat(DocumentType.values()).containsExactlyInAnyOrder(
            DocumentType.TAX_INVOICE,
            DocumentType.RECEIPT,
            DocumentType.INVOICE,
            DocumentType.DEBIT_CREDIT_NOTE,
            DocumentType.CANCELLATION_NOTE,
            DocumentType.ABBREVIATED_TAX_INVOICE
        );
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void testDocumentTypeHasRequiredProperties(DocumentType type) {
        assertThat(type.getContextPath()).isNotNull();
        assertThat(type.getNamespaceUri()).isNotNull();
        assertThat(type.getJaxbClass()).isNotNull();
        assertThat(type.getRootElementName()).isNotNull();
        assertThat(type.toDocumentSchematron()).isNotNull();
    }

    @Test
    void testFromNamespaceUriTaxInvoice() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    void testFromNamespaceUriReceipt() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.RECEIPT);
    }

    @Test
    void testFromNamespaceUriInvoice() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void testFromNamespaceUriDebitCreditNote() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.DEBIT_CREDIT_NOTE);
    }

    @Test
    void testFromNamespaceUriCancellationNote() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.CANCELLATION_NOTE);
    }

    @Test
    void testFromNamespaceUriAbbreviatedTaxInvoice() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2"
        );
        assertThat(result).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
    }

    @Test
    void testFromNamespaceUriUnknownReturnsNull() {
        DocumentType result = DocumentType.fromNamespaceUri(
            "urn:unknown:namespace:uri"
        );
        assertThat(result).isNull();
    }

    @Test
    void testFromNamespaceUriNullReturnsNull() {
        DocumentType result = DocumentType.fromNamespaceUri(null);
        assertThat(result).isNull();
    }

    @Test
    void testFromRootElementNameTaxInvoice() {
        DocumentType result = DocumentType.fromRootElementName("TaxInvoice_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    void testFromRootElementNameReceipt() {
        DocumentType result = DocumentType.fromRootElementName("Receipt_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.RECEIPT);
    }

    @Test
    void testFromRootElementNameInvoice() {
        DocumentType result = DocumentType.fromRootElementName("Invoice_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void testFromRootElementNameDebitCreditNote() {
        DocumentType result = DocumentType.fromRootElementName("DebitCreditNote_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.DEBIT_CREDIT_NOTE);
    }

    @Test
    void testFromRootElementNameCancellationNote() {
        DocumentType result = DocumentType.fromRootElementName("CancellationNote_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.CANCELLATION_NOTE);
    }

    @Test
    void testFromRootElementNameAbbreviatedTaxInvoice() {
        DocumentType result = DocumentType.fromRootElementName("AbbreviatedTaxInvoice_CrossIndustryInvoice");
        assertThat(result).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
    }

    @Test
    void testFromRootElementNameUnknownReturnsNull() {
        DocumentType result = DocumentType.fromRootElementName("UnknownDocument");
        assertThat(result).isNull();
    }

    @Test
    void testFromRootElementNameNullReturnsNull() {
        DocumentType result = DocumentType.fromRootElementName(null);
        assertThat(result).isNull();
    }

    @Test
    void testContextPathsContainRequiredPackages() {
        assertThat(DocumentType.TAX_INVOICE.getContextPath()).contains("taxinvoice");
        assertThat(DocumentType.RECEIPT.getContextPath()).contains("receipt");
        assertThat(DocumentType.INVOICE.getContextPath()).contains("invoice");
        assertThat(DocumentType.DEBIT_CREDIT_NOTE.getContextPath()).contains("debitcreditnote");
        assertThat(DocumentType.CANCELLATION_NOTE.getContextPath()).contains("cancellationnote");
        assertThat(DocumentType.ABBREVIATED_TAX_INVOICE.getContextPath()).contains("abbreviatedtaxinvoice");
    }

    @Test
    void testToDocumentSchematronMappings() {
        assertThat(DocumentType.TAX_INVOICE.toDocumentSchematron()).isEqualTo(DocumentSchematron.TAX_INVOICE);
        assertThat(DocumentType.RECEIPT.toDocumentSchematron()).isEqualTo(DocumentSchematron.RECEIPT);
        assertThat(DocumentType.INVOICE.toDocumentSchematron()).isEqualTo(DocumentSchematron.INVOICE);
        assertThat(DocumentType.DEBIT_CREDIT_NOTE.toDocumentSchematron()).isEqualTo(DocumentSchematron.DEBIT_CREDIT_NOTE);
        assertThat(DocumentType.CANCELLATION_NOTE.toDocumentSchematron()).isEqualTo(DocumentSchematron.CANCELLATION_NOTE);
        assertThat(DocumentType.ABBREVIATED_TAX_INVOICE.toDocumentSchematron()).isEqualTo(DocumentSchematron.ABBREVIATED_TAX_INVOICE);
    }
}
