package com.wpanther.document.intake.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.repository.IncomingDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Repository adapter that bridges between domain IncomingDocument aggregate
 * and JPA IncomingDocumentEntity.
 */
@Component
public class IncomingDocumentRepositoryImpl implements IncomingDocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(IncomingDocumentRepositoryImpl.class);

    private final JpaIncomingDocumentRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public IncomingDocumentRepositoryImpl(JpaIncomingDocumentRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public IncomingDocument save(IncomingDocument document) {
        log.debug("Saving document: {}", document.getId());

        IncomingDocumentEntity entity = toEntity(document);
        IncomingDocumentEntity savedEntity = jpaRepository.save(entity);

        return toDomain(savedEntity);
    }

    @Override
    public Optional<IncomingDocument> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(this::toDomain);
    }

    @Override
    public Optional<IncomingDocument> findByInvoiceNumber(String invoiceNumber) {
        return jpaRepository.findByInvoiceNumber(invoiceNumber)
            .map(this::toDomain);
    }

    @Override
    public List<IncomingDocument> findByStatus(DocumentStatus status) {
        return jpaRepository.findByStatus(status).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean existsByInvoiceNumber(String invoiceNumber) {
        return jpaRepository.existsByInvoiceNumber(invoiceNumber);
    }

    /**
     * Convert domain model to JPA entity
     */
    private IncomingDocumentEntity toEntity(IncomingDocument document) {
        ValidationResult validationResult = document.getValidationResult();

        return IncomingDocumentEntity.builder()
            .id(document.getId())
            .invoiceNumber(document.getInvoiceNumber())
            .xmlContent(document.getXmlContent())
            .source(document.getSource())
            .correlationId(document.getCorrelationId())
            .documentType(document.getDocumentType())
            .status(document.getStatus())
            .validationResult(validationResult != null ? mapValidationResult(validationResult) : null)
            .receivedAt(document.getReceivedAt())
            .processedAt(document.getProcessedAt())
            .build();
    }

    /**
     * Convert JPA entity to domain model
     */
    private IncomingDocument toDomain(IncomingDocumentEntity entity) {
        IncomingDocument.Builder builder = IncomingDocument.builder()
            .id(entity.getId())
            .invoiceNumber(entity.getInvoiceNumber())
            .xmlContent(entity.getXmlContent())
            .source(entity.getSource())
            .correlationId(entity.getCorrelationId())
            .documentType(entity.getDocumentType())
            .status(entity.getStatus())
            .receivedAt(entity.getReceivedAt())
            .processedAt(entity.getProcessedAt());

        // Map validation result if present
        if (entity.getValidationResult() != null) {
            builder.validationResult(mapValidationResultFromEntity(entity.getValidationResult()));
        }

        return builder.build();
    }

    /**
     * Convert ValidationResult to JSON string for storage
     */
    private String mapValidationResult(ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize ValidationResult to JSON", e);
            throw new RuntimeException("Failed to serialize ValidationResult", e);
        }
    }

    /**
     * Convert JSON string from storage to ValidationResult
     */
    private ValidationResult mapValidationResultFromEntity(String json) {
        try {
            return objectMapper.readValue(json, ValidationResult.class);
        } catch (Exception e) {
            log.error("Failed to deserialize ValidationResult from JSON: {}", json, e);
            throw new RuntimeException("Failed to deserialize ValidationResult", e);
        }
    }
}
