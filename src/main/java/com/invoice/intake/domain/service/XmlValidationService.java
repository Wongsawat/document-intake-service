package com.invoice.intake.domain.service;

import com.invoice.intake.domain.model.ValidationResult;

/**
 * Domain service for XML validation
 */
public interface XmlValidationService {

    /**
     * Validate XML content against XSD schema
     *
     * @param xmlContent The XML content to validate
     * @return Validation result with errors and warnings
     */
    ValidationResult validate(String xmlContent);

    /**
     * Extract invoice number from XML
     *
     * @param xmlContent The XML content
     * @return Invoice number or null if not found
     */
    String extractInvoiceNumber(String xmlContent);
}
