package com.wpanther.document.intake.adapter.out.validation;

import com.wpanther.document.intake.domain.model.DocumentType;
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
 * Adapter class that holds teda/JAXB-specific metadata for each document type.
 * Maps between the pure domain {@link DocumentType} enum and teda library types.
 *
 * <p>The teda library uses an interface/implementation pattern where:
 * <ul>
 *   <li>Interfaces are in .rsm/.ram packages</li>
 *   <li>Implementations are in .impl subpackages</li>
 *   <li>JAXB must be initialized with .impl packages only</li>
 * </ul>
 *
 * <p>This class lives in the adapter layer because it contains framework-specific
 * dependencies (teda library, JAXB classes) that must not leak into the domain.
 */
public enum TedaDocumentType {

    TAX_INVOICE(
        DocumentType.TAX_INVOICE,
        "com.wpanther.etax.generated.taxinvoice.rsm:" +
        "com.wpanther.etax.generated.taxinvoice.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2",
        TaxInvoice_CrossIndustryInvoiceType.class,
        "TaxInvoice_CrossIndustryInvoice"
    ),

    RECEIPT(
        DocumentType.RECEIPT,
        "com.wpanther.etax.generated.receipt.rsm:" +
        "com.wpanther.etax.generated.receipt.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2",
        Receipt_CrossIndustryInvoiceType.class,
        "Receipt_CrossIndustryInvoice"
    ),

    INVOICE(
        DocumentType.INVOICE,
        "com.wpanther.etax.generated.invoice.rsm:" +
        "com.wpanther.etax.generated.invoice.ram:" +
        "com.wpanther.etax.generated.invoice.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2",
        Invoice_CrossIndustryInvoiceType.class,
        "Invoice_CrossIndustryInvoice"
    ),

    DEBIT_CREDIT_NOTE(
        DocumentType.DEBIT_CREDIT_NOTE,
        "com.wpanther.etax.generated.debitcreditnote.rsm:" +
        "com.wpanther.etax.generated.debitcreditnote.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2",
        DebitCreditNote_CrossIndustryInvoiceType.class,
        "DebitCreditNote_CrossIndustryInvoice"
    ),

    CANCELLATION_NOTE(
        DocumentType.CANCELLATION_NOTE,
        "com.wpanther.etax.generated.cancellationnote.rsm:" +
        "com.wpanther.etax.generated.cancellationnote.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2",
        CancellationNote_CrossIndustryInvoiceType.class,
        "CancellationNote_CrossIndustryInvoice"
    ),

    ABBREVIATED_TAX_INVOICE(
        DocumentType.ABBREVIATED_TAX_INVOICE,
        "com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm:" +
        "com.wpanther.etax.generated.abbreviatedtaxinvoice.ram:" +
        "com.wpanther.etax.generated.common.qdt:" +
        "com.wpanther.etax.generated.common.udt",
        "urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2",
        AbbreviatedTaxInvoice_CrossIndustryInvoiceType.class,
        "AbbreviatedTaxInvoice_CrossIndustryInvoice"
    );

    private final DocumentType domainType;
    private final String contextPath;
    private final String namespaceUri;
    private final Class<?> jaxbClass;
    private final String rootElementName;

    TedaDocumentType(DocumentType domainType, String contextPath, String namespaceUri,
                     Class<?> jaxbClass, String rootElementName) {
        this.domainType = domainType;
        this.contextPath = contextPath;
        this.namespaceUri = namespaceUri;
        this.jaxbClass = jaxbClass;
        this.rootElementName = rootElementName;
    }

    /**
     * Get the domain DocumentType enum value.
     *
     * @return the domain DocumentType
     */
    public DocumentType getDomainType() {
        return domainType;
    }

    /**
     * Get the JAXB context path.
     *
     * @return the context path for JAXB
     */
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

    /**
     * Get the XML namespace URI for this document type.
     *
     * @return the namespace URI
     */
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
     * Get the root element name for this document type.
     *
     * @return the root element name
     */
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
     * Find TedaDocumentType by domain DocumentType.
     *
     * @param domainType the domain DocumentType enum
     * @return the matching TedaDocumentType, or null if not found
     */
    public static TedaDocumentType fromDomainType(DocumentType domainType) {
        if (domainType == null) {
            return null;
        }

        for (TedaDocumentType type : values()) {
            if (type.domainType == domainType) {
                return type;
            }
        }

        return null;
    }

    /**
     * Find document type by namespace URI.
     *
     * @param namespaceUri the XML namespace URI
     * @return the matching TedaDocumentType, or null if not found
     */
    public static TedaDocumentType fromNamespaceUri(String namespaceUri) {
        if (namespaceUri == null) {
            return null;
        }

        for (TedaDocumentType type : values()) {
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
     * @return the matching TedaDocumentType, or null if not found
     */
    public static TedaDocumentType fromRootElementName(String rootElementName) {
        if (rootElementName == null) {
            return null;
        }

        for (TedaDocumentType type : values()) {
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
     * @return the matching TedaDocumentType, or null if not found
     */
    public static TedaDocumentType fromJaxbClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        for (TedaDocumentType type : values()) {
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
