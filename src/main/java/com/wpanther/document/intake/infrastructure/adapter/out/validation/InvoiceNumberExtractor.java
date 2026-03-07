package com.wpanther.document.intake.infrastructure.adapter.out.validation;

/**
 * Strategy interface for extracting invoice numbers from different document types.
 * Allows for Open/Closed Principle - new document types can be added without modifying existing code.
 */
@FunctionalInterface
public interface InvoiceNumberExtractor {

    /**
     * Extract the invoice number from the given JAXB object.
     *
     * @param jaxbObject the unmarshaled JAXB object
     * @return the invoice/document number, or null if not found
     */
    String extractInvoiceNumber(Object jaxbObject);
}
