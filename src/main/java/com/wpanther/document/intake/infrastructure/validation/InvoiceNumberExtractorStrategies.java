package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm.AbbreviatedTaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.debitcreditnote.rsm.DebitCreditNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.receipt.rsm.Receipt_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;

/**
 * Enum providing InvoiceNumberExtractor implementations for each document type.
 * Maps DocumentType to its specific extraction strategy.
 */
public enum InvoiceNumberExtractorStrategies implements InvoiceNumberExtractor {

    TAX_INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof TaxInvoice_CrossIndustryInvoiceType) {
                var document = ((TaxInvoice_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    },

    RECEIPT {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof Receipt_CrossIndustryInvoiceType) {
                var document = ((Receipt_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    },

    INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof Invoice_CrossIndustryInvoiceType) {
                var document = ((Invoice_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    },

    DEBIT_CREDIT_NOTE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof DebitCreditNote_CrossIndustryInvoiceType) {
                var document = ((DebitCreditNote_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    },

    CANCELLATION_NOTE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof CancellationNote_CrossIndustryInvoiceType) {
                var document = ((CancellationNote_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    },

    ABBREVIATED_TAX_INVOICE {
        @Override
        public String extractInvoiceNumber(Object jaxbObject) {
            if (jaxbObject instanceof AbbreviatedTaxInvoice_CrossIndustryInvoiceType) {
                var document = ((AbbreviatedTaxInvoice_CrossIndustryInvoiceType) jaxbObject).getExchangedDocument();
                return document != null && document.getID() != null ? document.getID().getValue() : null;
            }
            return null;
        }
    }
}
