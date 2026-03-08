package com.wpanther.document.intake.infrastructure.adapter.in.web;

import com.wpanther.document.intake.application.port.in.GetDocumentUseCase;
import com.wpanther.document.intake.application.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.infrastructure.config.ValidationProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Document Intake", description = "API for submitting and retrieving Thai e-Tax XML documents")
public class DocumentIntakeController {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntakeController.class);

    private final SubmitDocumentUseCase submitDocumentUseCase;
    private final ProducerTemplate camelProducer;
    private final ValidationProperties validationProperties;
    private final GetDocumentUseCase getDocumentUseCase;

    public DocumentIntakeController(
            SubmitDocumentUseCase submitDocumentUseCase,
            ProducerTemplate camelProducer,
            ValidationProperties validationProperties,
            GetDocumentUseCase getDocumentUseCase) {
        this.submitDocumentUseCase = submitDocumentUseCase;
        this.camelProducer = camelProducer;
        this.validationProperties = validationProperties;
        this.getDocumentUseCase = getDocumentUseCase;
    }

    /**
     * Submit XML document.
     * Returns 202 Accepted on success, 400 for invalid/unrecognised documents,
     * 409 for duplicate document numbers, 413 for payload too large, 500 for unexpected errors.
     */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    @Operation(
        summary = "Submit a Thai e-Tax XML document",
        description = "Submit an XML document (Tax Invoice, Invoice, Receipt, etc.) for validation and processing. " +
                      "The document undergoes three-layer validation: well-formedness, XSD schema, and Schematron business rules. " +
                      "Valid documents trigger a saga orchestration workflow."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Document accepted for processing",
            content = @Content(schema = @Schema(implementation = SubmitDocumentResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid document content (validation failed)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Document number already exists (duplicate)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "413",
            description = "Payload too large (exceeds maximum XML size)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Map<String, Object>> submitDocument(
        @Parameter(
            description = "Thai e-Tax XML document content",
            required = true,
            schema = @Schema(type = "string", format = "xml", example = "<TaxInvoice_CrossIndustryInvoice>...</TaxInvoice_CrossIndustryInvoice>")
        )
        @RequestBody @NotBlank String xmlContent,
        @Parameter(
            description = "Optional correlation ID for distributed tracing",
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
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
    @Operation(
        summary = "Get document status",
        description = "Retrieve the current status and details of a submitted document by its UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document found",
            content = @Content(schema = @Schema(implementation = DocumentStatusResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Map<String, Object>> getDocumentStatus(
        @Parameter(description = "Document UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID id) {
        try {
            IncomingDocument document = getDocumentUseCase.getDocument(id);

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

    // ==================== OpenAPI Schema Classes ====================

    /**
     * Schema for successful document submission response.
     */
    @Schema(description = "Response returned when document is accepted for processing")
    private static class SubmitDocumentResponse {
        @Schema(description = "Success message", example = "Document submitted for processing")
        private String message;

        @Schema(description = "Correlation ID for tracking", example = "550e8400-e29b-41d4-a716-446655440000")
        private String correlationId;
    }

    /**
     * Schema for document status response.
     */
    @Schema(description = "Document status and details")
    private static class DocumentStatusResponse {
        @Schema(description = "Document UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String id;

        @Schema(description = "Document number from the XML", example = "TAX-2025-001")
        private String documentNumber;

        @Schema(description = "Current document status", example = "VALIDATED")
        private String status;

        @Schema(description = "Document type", example = "TAX_INVOICE")
        private String documentType;

        @Schema(description = "Timestamp when document was received", example = "2025-01-15T10:30:00Z")
        private String receivedAt;

        @Schema(description = "Timestamp when document was processed", example = "2025-01-15T10:30:05Z")
        private String processedAt;

        @Schema(description = "Validation result details")
        private ValidationResult validationResult;
    }

    /**
     * Schema for validation result.
     */
    @Schema(description = "Validation result details")
    private static class ValidationResult {
        @Schema(description = "Whether document is valid", example = "true")
        private boolean valid;

        @Schema(description = "Validation error messages", example = "[]")
        private java.util.List<String> errors;

        @Schema(description = "Validation warning messages", example = "[]")
        private java.util.List<String> warnings;
    }

    /**
     * Schema for error responses.
     */
    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Error type", example = "Invalid document")
        private String error;

        @Schema(description = "Detailed error message", example = "Document type could not be determined from XML namespace")
        private String message;
    }
}
