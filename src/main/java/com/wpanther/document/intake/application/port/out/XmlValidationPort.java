package com.wpanther.document.intake.application.port.out;

import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.domain.model.ValidationResult;

public interface XmlValidationPort {
    ValidationResult validate(String xmlContent);
    String extractDocumentNumber(String xmlContent);
    DocumentType extractDocumentType(String xmlContent);
}
