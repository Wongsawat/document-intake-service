package com.wpanther.document.intake.infrastructure.adapter.out.validation;

import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.validation.DocumentSchematron;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TedaDocumentType adapter.
 * Verifies the mapping between domain DocumentType and teda library types.
 */
@DisplayName("TedaDocumentType Adapter Tests")
class TedaDocumentTypeTest {

    @Test
    @DisplayName("Should map all domain DocumentType values to TedaDocumentType")
    void testMapAllDomainTypes() {
        // All 6 domain types should have corresponding TedaDocumentType values
        assertThat(TedaDocumentType.values()).hasSize(6);

        // Test round-trip mapping for all domain types
        for (DocumentType domainType : DocumentType.values()) {
            TedaDocumentType tedaType = TedaDocumentType.fromDomainType(domainType);

            assertThat(tedaType)
                .as("Domain type %s should map to TedaDocumentType", domainType)
                .isNotNull();

            assertThat(tedaType.getDomainType())
                .as("TedaDocumentType %s should map back to domain type", tedaType)
                .isEqualTo(domainType);
        }
    }

    @Test
    @DisplayName("Should return null for null domain type")
    void testNullDomainTypeReturnsNull() {
        assertThat(TedaDocumentType.fromDomainType(null)).isNull();
    }

    @Test
    @DisplayName("Should find by namespace URI")
    void testFindByNamespaceUri() {
        TedaDocumentType type = TedaDocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
        );

        assertThat(type).isNotNull();
        assertThat(type.getDomainType()).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Should return null for unknown namespace URI")
    void testUnknownNamespaceUriReturnsNull() {
        TedaDocumentType type = TedaDocumentType.fromNamespaceUri("urn:unknown:namespace");

        assertThat(type).isNull();
    }

    @Test
    @DisplayName("Should find by root element name")
    void testFindByRootElementName() {
        TedaDocumentType type = TedaDocumentType.fromRootElementName("TaxInvoice_CrossIndustryInvoice");

        assertThat(type).isNotNull();
        assertThat(type.getDomainType()).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Should return null for unknown root element name")
    void testUnknownRootElementNameReturnsNull() {
        TedaDocumentType type = TedaDocumentType.fromRootElementName("UnknownRootElement");

        assertThat(type).isNull();
    }

    @Test
    @DisplayName("Should find by JAXB class")
    void testFindByJaxbClass() {
        TedaDocumentType type = TedaDocumentType.fromJaxbClass(TaxInvoice_CrossIndustryInvoiceType.class);

        assertThat(type).isNotNull();
        assertThat(type.getDomainType()).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Should return null for unknown JAXB class")
    void testUnknownJaxbClassReturnsNull() {
        TedaDocumentType type = TedaDocumentType.fromJaxbClass(String.class);

        assertThat(type).isNull();
    }

    @Test
    @DisplayName("Should provide JAXB context path")
    void testGetContextPath() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.TAX_INVOICE);

        assertThat(type.getContextPath())
            .contains("com.wpanther.etax.generated.taxinvoice.rsm");
        assertThat(type.getContextPath())
            .contains("com.wpanther.etax.generated.common.qdt");
    }

    @Test
    @DisplayName("Should provide implementation context path with .impl suffix")
    void testGetImplementationContextPath() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.TAX_INVOICE);

        String implPath = type.getImplementationContextPath();

        // Implementation path should have .impl suffix on each package
        assertThat(implPath).contains(".impl");
        assertThat(implPath).contains("com.wpanther.etax.generated.taxinvoice.rsm.impl");
        assertThat(implPath).contains("com.wpanther.etax.generated.common.qdt.impl");
    }

    @Test
    @DisplayName("Should provide namespace URI")
    void testGetNamespaceUri() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.RECEIPT);

        assertThat(type.getNamespaceUri())
            .isEqualTo("urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2");
    }

    @Test
    @DisplayName("Should provide JAXB class")
    void testGetJaxbClass() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.TAX_INVOICE);

        assertThat(type.getJaxbClass()).isEqualTo(TaxInvoice_CrossIndustryInvoiceType.class);
    }

    @Test
    @DisplayName("Should provide root element name")
    void testGetRootElementName() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.INVOICE);

        assertThat(type.getRootElementName()).isEqualTo("Invoice_CrossIndustryInvoice");
    }

    @Test
    @DisplayName("Should map to teda DocumentSchematron enum")
    void testToDocumentSchematron() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.TAX_INVOICE);

        assertThat(type.toDocumentSchematron()).isEqualTo(DocumentSchematron.TAX_INVOICE);
    }

    @Test
    @DisplayName("Should provide invoice number extractor")
    void testGetInvoiceNumberExtractor() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.TAX_INVOICE);

        assertThat(type.getInvoiceNumberExtractor()).isNotNull();
        assertThat(type.getInvoiceNumberExtractor())
            .isInstanceOf(InvoiceNumberExtractor.class);
    }

    @Test
    @DisplayName("All document types should have complete configuration")
    void testAllDocumentTypesHaveCompleteConfiguration() {
        for (TedaDocumentType type : TedaDocumentType.values()) {
            assertThat(type.getContextPath())
                .as("Context path should not be null for %s", type)
                .isNotNull()
                .isNotEmpty();

            assertThat(type.getImplementationContextPath())
                .as("Implementation context path should not be null for %s", type)
                .isNotNull()
                .isNotEmpty();

            assertThat(type.getNamespaceUri())
                .as("Namespace URI should not be null for %s", type)
                .isNotNull()
                .isNotEmpty();

            assertThat(type.getJaxbClass())
                .as("JAXB class should not be null for %s", type)
                .isNotNull();

            assertThat(type.getRootElementName())
                .as("Root element name should not be null for %s", type)
                .isNotNull()
                .isNotEmpty();

            assertThat(type.getInvoiceNumberExtractor())
                .as("Invoice number extractor should not be null for %s", type)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("Invoice uses invoice.qdt not common.qdt")
    void testInvoiceUsesInvoiceQdt() {
        TedaDocumentType type = TedaDocumentType.fromDomainType(DocumentType.INVOICE);

        // INVOICE document type uses invoice.qdt instead of common.qdt
        assertThat(type.getContextPath()).contains("com.wpanther.etax.generated.invoice.qdt");
        assertThat(type.getContextPath()).doesNotContain("com.wpanther.etax.generated.common.qdt");
    }

    @Test
    @DisplayName("Other document types use common.qdt")
    void testOtherTypesUseCommonQdt() {
        // All other document types should use common.qdt
        for (TedaDocumentType type : TedaDocumentType.values()) {
            if (type.getDomainType() != DocumentType.INVOICE) {
                assertThat(type.getContextPath())
                    .as("%s should use common.qdt", type)
                    .contains("com.wpanther.etax.generated.common.qdt");
            }
        }
    }
}
