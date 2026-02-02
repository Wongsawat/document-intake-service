package com.wpanther.document.intake.application.service;

import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.repository.IncomingDocumentRepository;
import com.wpanther.document.intake.domain.service.XmlValidationService;
import com.wpanther.document.intake.infrastructure.messaging.EventPublisher;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DocumentIntakeService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntakeService.class);

    private final IncomingDocumentRepository documentRepository;
    private final XmlValidationService validationService;
    private final EventPublisher eventPublisher;

    public DocumentIntakeService(IncomingDocumentRepository documentRepository,
                                XmlValidationService validationService,
                                EventPublisher eventPublisher) {
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
     * @throws IllegalArgumentException if invoice number or document type cannot be extracted
     * @throws IllegalStateException if invoice number already exists
     */
    @Transactional
    public IncomingDocument submitInvoice(String xmlContent, String source, String correlationId) {
        log.info("Submitting document from source: {} with correlationId: {}", source, correlationId);

        // Extract invoice number
        String invoiceNumber = validationService.extractInvoiceNumber(xmlContent);
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Could not extract invoice number from XML");
        }

        // Extract document type
        DocumentType documentType = validationService.extractDocumentType(xmlContent);
        if (documentType == null) {
            throw new IllegalArgumentException("Could not detect document type from XML");
        }
        log.debug("Detected document type: {}", documentType);

        // Check if already exists
        if (documentRepository.existsByInvoiceNumber(invoiceNumber)) {
            log.warn("Invoice number {} already exists", invoiceNumber);
            throw new IllegalStateException("Invoice number already exists: " + invoiceNumber);
        }

        // Create incoming document
        IncomingDocument document = IncomingDocument.builder()
            .invoiceNumber(invoiceNumber)
            .xmlContent(xmlContent)
            .source(source)
            .correlationId(correlationId)
            .documentType(documentType)
            .build();

        // Save initial state
        document = documentRepository.save(document);
        log.info("Created incoming document: {} with ID: {}", invoiceNumber, document.getId());

        // Publish trace event IMMEDIATELY (before validation)
        // This provides visibility into the document intake process
        DocumentReceivedTraceEvent traceEvent = DocumentReceivedTraceEvent.builder()
            .documentId(document.getId().toString())
            .documentType(document.getDocumentType().name())
            .invoiceNumber(document.getInvoiceNumber())
            .correlationId(correlationId)
            .status("RECEIVED")
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
            invoiceNumber, validationResult.valid(), validationResult.errorCount(), validationResult.warningCount());

        // Publish StartSagaCommand AFTER validation (only for valid documents)
        // This triggers the saga orchestrator to begin the multi-step processing pipeline
        if (document.isValid()) {
            StartSagaCommand sagaCommand = StartSagaCommand.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .invoiceNumber(document.getInvoiceNumber())
                .xmlContent(xmlContent)
                .correlationId(correlationId)
                .source(source)
                .build();
            eventPublisher.publishStartSagaCommand(sagaCommand);

            // Update trace event status to VALIDATED
            DocumentReceivedTraceEvent validatedEvent = DocumentReceivedTraceEvent.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .invoiceNumber(document.getInvoiceNumber())
                .correlationId(correlationId)
                .status("VALIDATED")
                .source(source)
                .build();
            eventPublisher.publishTraceEvent(validatedEvent);

            // Mark document as FORWARDED after saga command is published
            document.markForwarded();
            document = documentRepository.save(document);

            // Publish trace event for FORWARDED status
            DocumentReceivedTraceEvent forwardedEvent = DocumentReceivedTraceEvent.builder()
                .documentId(document.getId().toString())
                .documentType(document.getDocumentType().name())
                .invoiceNumber(document.getInvoiceNumber())
                .correlationId(correlationId)
                .status("FORWARDED")
                .source(source)
                .build();
            eventPublisher.publishTraceEvent(forwardedEvent);
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

        log.info("Marked document {} as forwarded", document.getInvoiceNumber());
    }

    /**
     * Get document by ID.
     *
     * @param id the document ID
     * @return the document
     * @throws IllegalArgumentException if document not found
     */
    @Transactional(readOnly = true)
    public IncomingDocument getDocument(UUID id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }
}
