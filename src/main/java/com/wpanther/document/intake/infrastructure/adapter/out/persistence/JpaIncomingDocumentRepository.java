package com.wpanther.document.intake.infrastructure.adapter.out.persistence;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for IncomingDocumentEntity
 */
@Repository
public interface JpaIncomingDocumentRepository extends JpaRepository<IncomingDocumentEntity, UUID> {

    /**
     * Find by document number
     */
    Optional<IncomingDocumentEntity> findByDocumentNumber(String documentNumber);

    /**
     * Find by document type
     */
    List<IncomingDocumentEntity> findByDocumentType(DocumentType documentType);

    /**
     * Find by status
     */
    List<IncomingDocumentEntity> findByStatus(DocumentStatus status);

    /**
     * Count by status
     */
    long countByStatus(DocumentStatus status);

    /**
     * Check if document number exists
     */
    boolean existsByDocumentNumber(String documentNumber);
}
