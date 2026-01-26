package com.invoice.intake.application.service;

import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.domain.model.ValidationResult;
import com.invoice.intake.domain.repository.IncomingInvoiceRepository;
import com.invoice.intake.domain.service.XmlValidationService;
import com.invoice.intake.infrastructure.validation.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for invoice intake operations
 */
@Service
public class InvoiceIntakeService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceIntakeService.class);

    private final IncomingInvoiceRepository invoiceRepository;
    private final XmlValidationService validationService;

    public InvoiceIntakeService(IncomingInvoiceRepository invoiceRepository,
                                XmlValidationService validationService) {
        this.invoiceRepository = invoiceRepository;
        this.validationService = validationService;
    }

    /**
     * Submit and validate invoice
     */
    @Transactional
    public IncomingInvoice submitInvoice(String xmlContent, String source, String correlationId) {
        log.info("Submitting invoice from source: {} with correlationId: {}", source, correlationId);

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
        if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            log.warn("Invoice number {} already exists", invoiceNumber);
            throw new IllegalStateException("Invoice number already exists: " + invoiceNumber);
        }

        // Create incoming invoice
        IncomingInvoice invoice = IncomingInvoice.builder()
            .invoiceNumber(invoiceNumber)
            .xmlContent(xmlContent)
            .source(source)
            .correlationId(correlationId)
            .documentType(documentType)
            .build();

        // Save initial state
        invoice = invoiceRepository.save(invoice);
        log.info("Created incoming invoice: {} with ID: {}", invoiceNumber, invoice.getId());

        // Start validation
        invoice.startValidation();
        invoice = invoiceRepository.save(invoice);

        // Perform validation
        ValidationResult validationResult = validationService.validate(xmlContent);

        // Mark validation result
        invoice.markValidated(validationResult);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice {} validation result: valid={}, errors={}, warnings={}",
            invoiceNumber, validationResult.valid(), validationResult.errorCount(), validationResult.warningCount());

        return invoice;
    }

    /**
     * Mark invoice as forwarded
     */
    @Transactional
    public void markForwarded(UUID invoiceId) {
        IncomingInvoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        invoice.markForwarded();
        invoiceRepository.save(invoice);

        log.info("Marked invoice {} as forwarded", invoice.getInvoiceNumber());
    }

    /**
     * Get invoice by ID
     */
    @Transactional(readOnly = true)
    public IncomingInvoice getInvoice(UUID id) {
        return invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));
    }
}
