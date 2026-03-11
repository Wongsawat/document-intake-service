package com.wpanther.document.intake.application.usecase;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import java.util.UUID;

public interface GetDocumentUseCase {
    IncomingDocument getDocument(UUID id);
}
