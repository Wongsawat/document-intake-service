package com.wpanther.document.intake.application.port.in;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import java.util.UUID;

public interface GetDocumentUseCase {
    IncomingDocument getDocument(UUID id);
}
