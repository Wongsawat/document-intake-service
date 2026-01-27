package com.wpanther.document.intake.domain.service;

import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;

/**
 * Domain service for XML validation
 */
public interface XmlValidationService {

    /**
     * Validate XML content against XSD schema and Schematron business rules
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

    /**
     * Extract document type from XML namespace
     *
     * @param xmlContent The XML content
     * @return Document type or null if unable to detect
     */
    DocumentType extractDocumentType(String xmlContent);
}
