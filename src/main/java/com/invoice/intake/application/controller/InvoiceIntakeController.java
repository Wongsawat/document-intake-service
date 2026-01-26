package com.invoice.intake.application.controller;

import com.invoice.intake.application.service.InvoiceIntakeService;
import com.invoice.intake.domain.model.IncomingInvoice;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for invoice intake
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceIntakeController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceIntakeController.class);

    private final InvoiceIntakeService intakeService;
    private final ProducerTemplate camelProducer;

    public InvoiceIntakeController(InvoiceIntakeService intakeService, ProducerTemplate camelProducer) {
        this.intakeService = intakeService;
        this.camelProducer = camelProducer;
    }

    /**
     * Submit XML invoice
     */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<Map<String, Object>> submitInvoice(
        @RequestBody String xmlContent,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        log.info("Received invoice submission via REST API");

        try {
            // Send to Camel route for processing
            camelProducer.sendBodyAndHeader(
                "direct:invoice-intake",
                xmlContent,
                "correlationId",
                correlationId != null ? correlationId : UUID.randomUUID().toString()
            );

            return ResponseEntity.accepted().body(Map.of(
                "message", "Invoice submitted for processing",
                "correlationId", correlationId != null ? correlationId : "generated"
            ));

        } catch (Exception e) {
            log.error("Error submitting invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to submit invoice",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get invoice status by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getInvoiceStatus(@PathVariable UUID id) {
        try {
            IncomingInvoice invoice = intakeService.getInvoice(id);

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", invoice.getId().toString());
            response.put("invoiceNumber", invoice.getInvoiceNumber());
            response.put("status", invoice.getStatus().name());

            if (invoice.getDocumentType() != null) {
                response.put("documentType", invoice.getDocumentType().name());
            }
            if (invoice.getReceivedAt() != null) {
                response.put("receivedAt", invoice.getReceivedAt().toString());
            }
            if (invoice.getProcessedAt() != null) {
                response.put("processedAt", invoice.getProcessedAt().toString());
            }
            if (invoice.getValidationResult() != null) {
                response.put("validationResult", Map.of(
                    "valid", invoice.getValidationResult().valid(),
                    "errors", invoice.getValidationResult().errors(),
                    "warnings", invoice.getValidationResult().warnings()
                ));
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving invoice status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to retrieve invoice status"
            ));
        }
    }
}
