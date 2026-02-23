package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm.AbbreviatedTaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.debitcreditnote.rsm.DebitCreditNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.receipt.rsm.Receipt_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;

/**
 * Enum providing InvoiceNumberExtractor implementations for each document type.
 * Maps DocumentType to its specific extraction strategy using direct typed casts.
 * <p>
 * All Thai e-Tax document types share a common structure where each root type has
 * a getExchangedDocument() method, which has a getID() method, which has a getValue()
 * method returning the document number. Direct casts are used per constant because
 * each document type's root interface is in a separate package with no shared supertype.
 * The ID value type ({@code Max35IDType}) is shared from the common.qdt package.
 */
public enum InvoiceNumberExtractorStrategies implements InvoiceNumberExtractor {

    TAX_INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof TaxInvoice_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    },

    RECEIPT {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof Receipt_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    },

    INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof Invoice_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    },

    DEBIT_CREDIT_NOTE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof DebitCreditNote_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    },

    CANCELLATION_NOTE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof CancellationNote_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    },

    ABBREVIATED_TAX_INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (!(jaxbObject instanceof AbbreviatedTaxInvoice_CrossIndustryInvoiceType doc)) {
                return null;
            }
            var exchangedDocument = doc.getExchangedDocument();
            if (exchangedDocument == null) return null;
            var id = exchangedDocument.getID();
            return id != null ? id.getValue() : null;
        }
    };
}
