package com.wpanther.document.intake.application.controller;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.infrastructure.config.ValidationProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.ConstraintViolationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for document intake
 */
@RestController
@RequestMapping("/api/v1/documents")
@Validated
public class DocumentIntakeController {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntakeController.class);

    private final DocumentIntakeService intakeService;
    private final ProducerTemplate camelProducer;
    private final ValidationProperties validationProperties;

    public DocumentIntakeController(
            DocumentIntakeService intakeService,
            ProducerTemplate camelProducer,
            ValidationProperties validationProperties) {
        this.intakeService = intakeService;
        this.camelProducer = camelProducer;
        this.validationProperties = validationProperties;
    }

    /**
     * Submit XML document.
     * Returns 202 Accepted on success, 400 for invalid/unrecognised documents,
     * 409 for duplicate document numbers, 413 for payload too large, 500 for unexpected errors.
     */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<Map<String, Object>> submitDocument(
        @RequestBody @NotBlank String xmlContent,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        log.info("Received document submission via REST API");

        // Validate content size
        if (xmlContent.length() > validationProperties.getMaxXmlSize()) {
            log.warn("Document rejected - size exceeds maximum: {} > {}",
                xmlContent.length(), validationProperties.getMaxXmlSize());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "Payload too large",
                "message", "XML content exceeds maximum size of " +
                    validationProperties.getMaxXmlSizeMb() + "MB"
            ));
        }

        String effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        try {
            camelProducer.sendBodyAndHeader(
                "direct:document-intake",
                xmlContent,
                "correlationId",
                effectiveCorrelationId
            );

            return ResponseEntity.accepted().body(Map.of(
                "message", "Document submitted for processing",
                "correlationId", effectiveCorrelationId
            ));

        } catch (Exception e) {
            // Unwrap CamelExecutionException to reach the original business exception
            Throwable cause = (e instanceof CamelExecutionException && e.getCause() != null)
                ? e.getCause() : e;

            if (cause instanceof IllegalArgumentException) {
                log.warn("Document rejected — invalid content: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid document",
                    "message", cause.getMessage()
                ));
            }

            if (cause instanceof IllegalStateException) {
                log.warn("Document rejected — duplicate: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Document already exists",
                    "message", cause.getMessage()
                ));
            }

            log.error("Unexpected error submitting document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to submit document",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get document status by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable UUID id) {
        try {
            IncomingDocument document = intakeService.getDocument(id);

            java.util.Map<String, Object> response = new java.util.HashMap<>();
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

    /**
     * Handles Bean Validation constraint violations raised by Spring's AOP-based
     * method validation (e.g. @NotBlank, @Size on method parameters).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
        log.warn("Request constraint violation: {}", message);
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Invalid request",
            "message", message
        ));
    }

    /**
     * Handles validation failures raised by Spring MVC 6.1's built-in method validation
     * (HandlerMethodValidationException replaces ConstraintViolationException for
     * @Validated controller methods in Spring Framework 6.1+).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
            .map(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Validation failed")
            .collect(Collectors.joining("; "));
        log.warn("Request validation failed: {}", message);
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Invalid request",
            "message", message
        ));
    }
}
