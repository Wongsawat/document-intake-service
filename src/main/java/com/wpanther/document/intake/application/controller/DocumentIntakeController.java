package com.wpanther.document.intake.application.controller;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
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
 * REST controller for document intake
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIntakeController {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntakeController.class);

    private final DocumentIntakeService intakeService;
    private final ProducerTemplate camelProducer;

    public DocumentIntakeController(DocumentIntakeService intakeService, ProducerTemplate camelProducer) {
        this.intakeService = intakeService;
        this.camelProducer = camelProducer;
    }

    /**
     * Submit XML document
     */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<Map<String, Object>> submitDocument(
        @RequestBody String xmlContent,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        log.info("Received document submission via REST API");

        try {
            // Send to Camel route for processing
            camelProducer.sendBodyAndHeader(
                "direct:document-intake",
                xmlContent,
                "correlationId",
                correlationId != null ? correlationId : UUID.randomUUID().toString()
            );

            return ResponseEntity.accepted().body(Map.of(
                "message", "Document submitted for processing",
                "correlationId", correlationId != null ? correlationId : "generated"
            ));

        } catch (Exception e) {
            log.error("Error submitting document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to submit document",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get document status by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable UUID id) {
        try {
            IncomingDocument document = intakeService.getDocument(id);

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", document.getId().toString());
            response.put("documentNumber", document.getDocumentNumber());
            response.put("status", document.getStatus().name());

            if (document.getDocumentType() != null) {
                response.put("documentType", document.getDocumentType().name());
            }
            if (document.getReceivedAt() != null) {
                response.put("receivedAt", document.getReceivedAt().toString());
            }
            if (document.getProcessedAt() != null) {
                response.put("processedAt", document.getProcessedAt().toString());
            }
            if (document.getValidationResult() != null) {
                response.put("validationResult", Map.of(
                    "valid", document.getValidationResult().valid(),
                    "errors", document.getValidationResult().errors(),
                    "warnings", document.getValidationResult().warnings()
                ));
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving document status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to retrieve document status"
            ));
        }
    }
}
