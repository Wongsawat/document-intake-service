package com.wpanther.document.intake.application.usecase;

import com.wpanther.document.intake.domain.model.IncomingDocument;

public interface SubmitDocumentUseCase {
    IncomingDocument submitDocument(String xmlContent, String source, String correlationId);
}
