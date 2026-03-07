package com.wpanther.document.intake.domain.repository;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    IncomingDocument save(IncomingDocument document);
    Optional<IncomingDocument> findById(UUID id);
    Optional<IncomingDocument> findByDocumentNumber(String documentNumber);
    List<IncomingDocument> findByStatus(DocumentStatus status);
    boolean existsByDocumentNumber(String documentNumber);
}
