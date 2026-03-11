package com.wpanther.document.intake.infrastructure.config.validation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for XML schema paths.
 * Schema paths are externalized to application.yml for easier management.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.xml.schemas")
public class SchemaPathConfig {

    /**
     * XSD schema path for Tax Invoice documents.
     */
    private String taxInvoice;

    /**
     * XSD schema path for Receipt documents.
     */
    private String receipt;

    /**
     * XSD schema path for Invoice documents.
     */
    private String invoice;

    /**
     * XSD schema path for Debit/Credit Note documents.
     */
    private String debitCreditNote;

    /**
     * XSD schema path for Cancellation Note documents.
     */
    private String cancellationNote;

    /**
     * XSD schema path for Abbreviated Tax Invoice documents.
     */
    private String abbreviatedTaxInvoice;

    /**
     * Get schema path for a document type by name.
     *
     * @param documentTypeName document type name (e.g., "TAX_INVOICE")
     * @return XSD schema path
     */
    public String getSchemaPath(String documentTypeName) {
        return switch (documentTypeName) {
            case "TAX_INVOICE" -> taxInvoice;
            case "RECEIPT" -> receipt;
            case "INVOICE" -> invoice;
            case "DEBIT_CREDIT_NOTE" -> debitCreditNote;
            case "CANCELLATION_NOTE" -> cancellationNote;
            case "ABBREVIATED_TAX_INVOICE" -> abbreviatedTaxInvoice;
            default -> throw new IllegalArgumentException(
                "Unknown document type: " + documentTypeName + ". " +
                "Valid types are: TAX_INVOICE, RECEIPT, INVOICE, DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE"
            );
        };
    }
}
