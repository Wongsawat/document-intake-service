package com.wpanther.document.intake.application.service;

import com.wpanther.document.intake.domain.event.DocumentReceivedCountingEvent;
import com.wpanther.document.intake.domain.event.DocumentReceivedEvent;
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

import java.time.ZoneId;
import java.util.UUID;

/**
 * Application service for document intake operations
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
     * Submit and validate document
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

        // Publish counting event IMMEDIATELY (before validation)
        // This ensures ALL received documents are counted, regardless of validation outcome
        DocumentReceivedCountingEvent countingEvent = new DocumentReceivedCountingEvent(
            document.getId().toString(),
            document.getCorrelationId(),
            document.getReceivedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
        eventPublisher.publishDocumentReceivedCounting(countingEvent);

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

        // Publish statistics event AFTER validation (only for valid documents)
        // This event contains full document details and is routed to document-type-specific topics
        if (document.isValid()) {
            DocumentReceivedEvent statsEvent = new DocumentReceivedEvent(
                document.getId().toString(),
                document.getInvoiceNumber(),
                document.getXmlContent(),
                document.getCorrelationId(),
                document.getDocumentType().name()
            );
            eventPublisher.publishDocumentReceivedForStatistics(statsEvent, document.getDocumentType());
        }

        return document;
    }

    /**
     * Mark document as forwarded
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
     * Get document by ID
     */
    @Transactional(readOnly = true)
    public IncomingDocument getDocument(UUID id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }
}
