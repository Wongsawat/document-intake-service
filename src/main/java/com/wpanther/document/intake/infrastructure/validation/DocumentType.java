package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm.AbbreviatedTaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.debitcreditnote.rsm.DebitCreditNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.receipt.rsm.Receipt_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.validation.DocumentSchematron;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Enum representing the 6 Thai e-Tax document types supported by the system.
 * Each document type has its own JAXB context, namespace, XSD schema, and Kafka topic.
 * Schematron validation is handled via teda library's DocumentSchematron enum.
 *
 * <p>The teda library uses an interface/implementation pattern where:
 * <ul>
 *   <li>Interfaces are in .rsm/.ram packages</li>
 *   <li>Implementations are in .impl subpackages</li>
 *   <li>JAXB must be initialized with .impl packages only</li>
 * </ul>
 */
public enum DocumentType {

    TAX_INVOICE(
        "com.wpanther.etax.generated.taxinvoice.rsm:" +
        "com.wpanther.etax.generated.taxinvoice.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2",
        TaxInvoice_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/TaxInvoice_CrossIndustryInvoice_2p1.xsd",
        "TaxInvoice_CrossIndustryInvoice"
    ),

    RECEIPT(
        "com.wpanther.etax.generated.receipt.rsm:" +
        "com.wpanther.etax.generated.receipt.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2",
        Receipt_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/Receipt_CrossIndustryInvoice_2p1.xsd",
        "Receipt_CrossIndustryInvoice"
    ),

    INVOICE(
        "com.wpanther.etax.generated.invoice.rsm:" +
        "com.wpanther.etax.generated.invoice.ram:" +
        "com.wpanther.etax.generated.invoice.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2",
        Invoice_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/Invoice_CrossIndustryInvoice_2p1.xsd",
        "Invoice_CrossIndustryInvoice"
    ),

    DEBIT_CREDIT_NOTE(
        "com.wpanther.etax.generated.debitcreditnote.rsm:" +
        "com.wpanther.etax.generated.debitcreditnote.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2",
        DebitCreditNote_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/DebitCreditNote_CrossIndustryInvoice_2p1.xsd",
        "DebitCreditNote_CrossIndustryInvoice"
    ),

    CANCELLATION_NOTE(
        "com.wpanther.etax.generated.cancellationnote.rsm:" +
        "com.wpanther.etax.generated.cancellationnote.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2",
        CancellationNote_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/CancellationNote_CrossIndustryInvoice_2p1.xsd",
        "CancellationNote_CrossIndustryInvoice"
    ),

    ABBREVIATED_TAX_INVOICE(
        "com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm:" +
        "com.wpanther.etax.generated.abbreviatedtaxinvoice.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2",
        AbbreviatedTaxInvoice_CrossIndustryInvoiceType.class,
        "e-tax-invoice-receipt-v2.1/ETDA/data/standard/AbbreviatedTaxInvoice_CrossIndustryInvoice_2p1.xsd",
        "AbbreviatedTaxInvoice_CrossIndustryInvoice"
    );

    private final String contextPath;
    private final String namespaceUri;
    private final Class<?> jaxbClass;
    private final String schemaPath;
    private final String rootElementName;

    DocumentType(String contextPath, String namespaceUri,
                 Class<?> jaxbClass, String schemaPath, String rootElementName) {
        this.contextPath = contextPath;
        this.namespaceUri = namespaceUri;
        this.jaxbClass = jaxbClass;
        this.schemaPath = schemaPath;
        this.rootElementName = rootElementName;
    }

    public String getContextPath() {
        return contextPath;
    }

    /**
     * Get the JAXB context path using implementation packages.
     * The teda library uses .impl packages for JAXB initialization.
     *
     * @return the implementation package context path for JAXB
     */
    public String getImplementationContextPath() {
        return Arrays.stream(contextPath.split(":"))
            .map(pkg -> pkg + ".impl")
            .collect(Collectors.joining(":"));
    }

    public String getNamespaceUri() {
        return namespaceUri;
    }

    /**
     * Get the JAXB root class for this document type.
     *
     * @return the JAXB class type
     */
    public Class<?> getJaxbClass() {
        return jaxbClass;
    }

    /**
     * @deprecated Use {@link #getJaxbClass()} instead.
     */
    @Deprecated
    public Class<?> getRootClass() {
        return jaxbClass;
    }

    public String getSchemaPath() {
        return schemaPath;
    }

    public String getRootElementName() {
        return rootElementName;
    }

    /**
     * Map to teda library's DocumentSchematron enum for Schematron validation.
     *
     * @return the corresponding DocumentSchematron value
     */
    public DocumentSchematron toDocumentSchematron() {
        return switch (this) {
            case TAX_INVOICE -> DocumentSchematron.TAX_INVOICE;
            case RECEIPT -> DocumentSchematron.RECEIPT;
            case INVOICE -> DocumentSchematron.INVOICE;
            case DEBIT_CREDIT_NOTE -> DocumentSchematron.DEBIT_CREDIT_NOTE;
            case CANCELLATION_NOTE -> DocumentSchematron.CANCELLATION_NOTE;
            case ABBREVIATED_TAX_INVOICE -> DocumentSchematron.ABBREVIATED_TAX_INVOICE;
        };
    }

    /**
     * Find document type by namespace URI.
     *
     * @param namespaceUri the XML namespace URI
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromNamespaceUri(String namespaceUri) {
        if (namespaceUri == null) {
            return null;
        }

        for (DocumentType type : values()) {
            if (type.namespaceUri.equals(namespaceUri)) {
                return type;
            }
        }

        return null;
    }

    /**
     * Find document type by root element name.
     *
     * @param rootElementName the root element local name
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromRootElementName(String rootElementName) {
        if (rootElementName == null) {
            return null;
        }

        for (DocumentType type : values()) {
            if (type.rootElementName.equals(rootElementName)) {
                return type;
            }
        }

        return null;
    }

    /**
     * Find document type by JAXB class.
     * Used when document type is detected from an unmarshaled JAXB object.
     *
     * @param clazz the JAXB class to match
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromJaxbClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        for (DocumentType type : values()) {
            if (type.jaxbClass.isAssignableFrom(clazz)) {
                return type;
            }
        }

        return null;
    }

    /**
     * Get the invoice number extractor strategy for this document type.
     *
     * @return the InvoiceNumberExtractor strategy for this document type
     */
    public InvoiceNumberExtractor getInvoiceNumberExtractor() {
        return switch (this) {
            case TAX_INVOICE -> InvoiceNumberExtractorStrategies.TAX_INVOICE;
            case RECEIPT -> InvoiceNumberExtractorStrategies.RECEIPT;
            case INVOICE -> InvoiceNumberExtractorStrategies.INVOICE;
            case DEBIT_CREDIT_NOTE -> InvoiceNumberExtractorStrategies.DEBIT_CREDIT_NOTE;
            case CANCELLATION_NOTE -> InvoiceNumberExtractorStrategies.CANCELLATION_NOTE;
            case ABBREVIATED_TAX_INVOICE -> InvoiceNumberExtractorStrategies.ABBREVIATED_TAX_INVOICE;
        };
    }
}
