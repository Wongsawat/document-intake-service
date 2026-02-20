package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm.AbbreviatedTaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.debitcreditnote.rsm.DebitCreditNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.receipt.rsm.Receipt_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;

import java.lang.reflect.Method;

/**
 * Enum providing InvoiceNumberExtractor implementations for each document type.
 * Maps DocumentType to its specific extraction strategy.
 * <p>
 * All Thai e-Tax document types share a common structure where:
 * - Each type has a getExchangedDocument() method
 * - The exchanged document has a getID() method
 * - The ID has a getValue() method returning the document number
 * <p>
 * This implementation uses reflection to avoid code duplication while maintaining type safety.
 */
public enum InvoiceNumberExtractorStrategies implements InvoiceNumberExtractor {

    TAX_INVOICE(TaxInvoice_CrossIndustryInvoiceType.class),
    RECEIPT(Receipt_CrossIndustryInvoiceType.class),
    INVOICE(Invoice_CrossIndustryInvoiceType.class),
    DEBIT_CREDIT_NOTE(DebitCreditNote_CrossIndustryInvoiceType.class),
    CANCELLATION_NOTE(CancellationNote_CrossIndustryInvoiceType.class),
    ABBREVIATED_TAX_INVOICE(AbbreviatedTaxInvoice_CrossIndustryInvoiceType.class);

    private final Class<?> expectedType;

    InvoiceNumberExtractorStrategies(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public String extractInvoiceNumber(Object jaxbObject) {
        if (!expectedType.isInstance(jaxbObject)) {
            return null;
        }

        try {
            Method getExchangedDocument = expectedType.getMethod("getExchangedDocument");
            Object document = getExchangedDocument.invoke(jaxbObject);

            if (document == null) {
                return null;
            }

            Method getID = document.getClass().getMethod("getID");
            Object id = getID.invoke(document);

            if (id == null) {
                return null;
            }

            Method getValue = id.getClass().getMethod("getValue");
            return (String) getValue.invoke(id);

        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
