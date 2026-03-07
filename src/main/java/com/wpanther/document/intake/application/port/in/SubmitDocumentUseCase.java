package com.wpanther.document.intake.application.port.in;

import com.wpanther.document.intake.domain.model.IncomingDocument;

public interface SubmitDocumentUseCase {
    IncomingDocument submitDocument(String xmlContent, String source, String correlationId);
}
