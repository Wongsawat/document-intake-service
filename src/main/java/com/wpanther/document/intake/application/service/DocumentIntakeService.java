package com.wpanther.document.intake.application.service;

import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.EventStatus;
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.port.in.GetDocumentUseCase;
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.domain.port.out.DocumentEventPublisher;
import com.wpanther.document.intake.domain.port.out.DocumentRepository;
import com.wpanther.document.intake.domain.port.out.XmlValidationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for document intake operations.
 * <p>
 * This service handles document submission, validation, and event publishing via the outbox pattern.
 * Events are written to the outbox table within the same transaction as domain state changes,
 * ensuring atomicity and guaranteed delivery.
 */
@Service
public class DocumentIntakeService implements SubmitDocumentUseCase, GetDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntakeService.class);

    private final DocumentRepository documentRepository;
    private final XmlValidationPort validationService;
    private final DocumentEventPublisher eventPublisher;

    public DocumentIntakeService(DocumentRepository documentRepository,
                                XmlValidationPort validationService,
                                DocumentEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.validationService = validationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Submit and validate document.
     * <p>
     * This method:
     * 1. Validates the XML content
     * 2. Creates and saves an IncomingDocument to the database
     * 3. Publishes a DocumentReceivedTraceEvent (for notification-service)
     * 4. Performs document validation
     * 5. If validated successfully, publishes a StartSagaCommand (for orchestrator-service)
     * <p>
     * All database changes and event writes happen within a single transaction.
     * Events are written to the outbox table and Debezium CDC publishes them to Kafka.
     *
     * @param xmlContent the XML content to validate
     * @param source the source of the document (API, KAFKA, etc.)
     * @param correlationId the correlation ID for tracing
     * @return the created IncomingDocument
     * @throws IllegalArgumentException if document number or document type cannot be extracted
     * @throws IllegalStateException if document number already exists
     */
    @Transactional
    @Override
    public IncomingDocument submitDocument(String xmlContent, String source, String correlationId) {
        log.info("Submitting document from source: {} with correlationId: {}", source, correlationId);

        // Extract document number
        String documentNumber = validationService.extractDocumentNumber(xmlContent);
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException(
                "Could not extract document number from XML. " +
                "Ensure XML has valid document number in the expected field location. " +
                "Document type may not be recognized or document number field may be missing."
            );
        }

        // Extract document type
        DocumentType documentType = validationService.extractDocumentType(xmlContent);
        if (documentType == null) {
            throw new IllegalArgumentException(
                "Could not detect document type from XML. " +
                "Ensure XML namespace URI matches one of the supported document types: " +
                "TAX_INVOICE, RECEIPT, INVOICE, DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE."
            );
        }
        log.debug("Detected document type: {}", documentType);

        // Check if already exists
        if (documentRepository.existsByDocumentNumber(documentNumber)) {
            log.warn("Document number {} already exists", documentNumber);
            throw new IllegalStateException(
                "Document number already exists: " + documentNumber + ". " +
                "A document with this number has already been submitted. " +
                "Please check existing documents or use a different document number."
            );
        }

        // Create incoming document
        IncomingDocument document = IncomingDocument.builder()
            .documentNumber(documentNumber)
            .xmlContent(xmlContent)
            .source(source)
            .correlationId(correlationId)
            .documentType(documentType)
            .build();

        // Save initial state — catch concurrent duplicate that slipped past the existence check
        try {
            document = documentRepository.save(document);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate document number detected on save: {}", documentNumber);
            throw new IllegalStateException(
                "Document number already exists: " + documentNumber + ". " +
                "A document with this number has already been submitted. " +
                "Please check existing documents or use a different document number."
            );
        }
        log.info("Created incoming document: {} with ID: {}", documentNumber, document.getId());

        // Publish trace event IMMEDIATELY (before validation)
        // This provides visibility into the document intake process
        DocumentReceivedTraceEvent traceEvent = DocumentReceivedTraceEvent.builder()
            .documentId(document.getId().toString())
            .documentType(document.getDocumentType().name())
            .documentNumber(document.getDocumentNumber())
            .correlationId(correlationId)
            .status(EventStatus.RECEIVED.getValue())
            .source(source)
            .build();
        eventPublisher.publishTraceEvent(traceEvent);

        // Start validation
        document.startValidation();
        document = documentRepository.save(document);

        // Perform validation
        ValidationResult validationResult = validationService.validate(xmlContent);

        // Mark validation result
        document.markValidated(validationResult);
        document = documentRepository.save(document);

        log.info("Document {} validation result: valid={}, errors={}, warnings={}",
            documentNumber, validationResult.valid(), validationResult.errorCount(), validationResult.warningCount());

        // Publish StartSagaCommand AFTER validation (only for valid documents)
        // This triggers the saga orchestrator to begin the multi-step processing pipeline
        if (document.isValid()) {
            StartSagaCommand sagaCommand = StartSagaCommand.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .documentNumber(document.getDocumentNumber())
                .xmlContent(xmlContent)
                .correlationId(correlationId)
                .source(source)
                .build();
            eventPublisher.publishStartSagaCommand(sagaCommand);

            // Publish VALIDATED trace event
            DocumentReceivedTraceEvent validatedEvent = DocumentReceivedTraceEvent.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .documentNumber(document.getDocumentNumber())
                .correlationId(correlationId)
                .status(EventStatus.VALIDATED.getValue())
                .source(source)
                .build();
            eventPublisher.publishTraceEvent(validatedEvent);

            // Mark document as FORWARDED after saga command is published
            document.markForwarded();
            document = documentRepository.save(document);

            // Publish FORWARDED trace event
            DocumentReceivedTraceEvent forwardedEvent = DocumentReceivedTraceEvent.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .documentNumber(document.getDocumentNumber())
                .correlationId(correlationId)
                .status(EventStatus.FORWARDED.getValue())
                .source(source)
                .build();
            eventPublisher.publishTraceEvent(forwardedEvent);
        } else {
            // Publish INVALID trace event so notification-service tracks rejected documents
            DocumentReceivedTraceEvent invalidEvent = DocumentReceivedTraceEvent.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .documentNumber(document.getDocumentNumber())
                .correlationId(correlationId)
                .status(EventStatus.INVALID.getValue())
                .source(source)
                .build();
            eventPublisher.publishTraceEvent(invalidEvent);

            log.warn("Document {} failed validation with {} error(s)",
                documentNumber, document.getValidationResult().errorCount());
        }

        return document;
    }

    /**
     * Mark document as forwarded.
     * Called after the saga command has been successfully published.
     *
     * @param documentId the document ID to mark as forwarded
     */
    @Transactional
    public void markForwarded(UUID documentId) {
        IncomingDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        document.markForwarded();
        documentRepository.save(document);

        log.info("Marked document {} as forwarded", document.getDocumentNumber());
    }

    /**
     * Get document by ID.
     *
     * @param id the document ID
     * @return the document
     * @throws IllegalArgumentException if document not found
     */
    @Transactional(readOnly = true)
    @Override
    public IncomingDocument getDocument(UUID id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }
}
