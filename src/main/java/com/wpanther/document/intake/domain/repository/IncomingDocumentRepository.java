package com.wpanther.document.intake.domain.repository;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for IncomingDocument aggregate
 */
public interface IncomingDocumentRepository {

    /**
     * Save an incoming document
     */
    IncomingDocument save(IncomingDocument document);

    /**
     * Find document by ID
     */
    Optional<IncomingDocument> findById(UUID id);

    /**
     * Find document by document number
     */
    Optional<IncomingDocument> findByDocumentNumber(String documentNumber);

    /**
     * Find documents by status
     */
    List<IncomingDocument> findByStatus(DocumentStatus status);

    /**
     * Check if document number exists
     */
    boolean existsByDocumentNumber(String documentNumber);
}
