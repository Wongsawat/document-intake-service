package com.wpanther.document.intake.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.camel.ProducerTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DocumentIntakeController
 * Uses MockMvc for testing REST endpoints
 */
@WebMvcTest(DocumentIntakeController.class)
@DisplayName("Document Intake Controller Tests")
class DocumentIntakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIntakeService documentIntakeService;

    @MockBean
    private ProducerTemplate producerTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private IncomingDocument testDocument;

    @BeforeEach
    void setUp() {
        testDocument = IncomingDocument.builder()
            .id(UUID.randomUUID())
            .documentNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId("corr-123")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .receivedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/documents returns 202 Accepted for valid document")
    void testSubmitInvoiceReturns202Accepted() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), eq("corr-123")))
            .thenReturn(testDocument);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-123"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value("corr-123"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} returns 200 OK when document exists")
    void testGetInvoiceByIdReturns200() throws Exception {
        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenReturn(testDocument);

        mockMvc.perform(get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentNumber").value("INV-2024-001"))
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.documentType").value("TAX_INVOICE"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} returns 404 when document not found")
    void testGetInvoiceByIdReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(documentIntakeService.getDocument(unknownId))
            .thenThrow(new IllegalArgumentException("Document not found: " + unknownId));

        mockMvc.perform(get("/api/v1/documents/{id}", unknownId))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/documents generates correlation ID when not provided")
    void testSubmitInvoiceGeneratesCorrelationId() throws Exception {
        IncomingDocument document = IncomingDocument.builder()
            .id(testDocument.getId())
            .documentNumber("INV-2024-002")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId(null) // No correlation ID provided
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        when(documentIntakeService.submitDocument(any(), eq("REST"), eq(null)))
            .thenReturn(document);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/documents returns 400 for invalid XML")
    void testSubmitInvoiceReturns400ForInvalidXml() throws Exception {
        doThrow(new IllegalArgumentException("Could not extract document number"))
            .when(producerTemplate).sendBodyAndHeader(any(String.class), any(), any(String.class), any());

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("invalid xml"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid document"));
    }

    @Test
    @DisplayName("POST /api/v1/documents returns 409 for duplicate document number")
    void testSubmitInvoiceReturns409ForDuplicateDocument() throws Exception {
        doThrow(new IllegalStateException("Document number already exists: INV-2024-001"))
            .when(producerTemplate).sendBodyAndHeader(any(String.class), any(), any(String.class), any());

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-dup"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Document already exists"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} returns validation result")
    void testGetInvoiceIncludesValidationResult() throws Exception {
        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenReturn(testDocument);

        mockMvc.perform(get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validationResult.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/documents handles different sources")
    void testSubmitInvoiceHandlesDifferentSources() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("KAFKA"), eq("corr-456")))
            .thenReturn(testDocument);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-456")
                .header("X-Source", "KAFKA"))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /api/v1/documents accepts text/xml content type")
    void testSubmitInvoiceAcceptsTextXmlContentType() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), any()))
            .thenReturn(testDocument);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.TEXT_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-text-xml"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value("corr-text-xml"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} handles document with null documentType")
    void testGetInvoiceHandlesNullDocumentType() throws Exception {
        IncomingDocument documentWithNullType = IncomingDocument.builder()
            .id(testDocument.getId())
            .documentNumber("INV-2024-003")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(null) // Null document type
            .status(DocumentStatus.VALIDATING)
            .validationResult(ValidationResult.success())
            .receivedAt(Instant.now())
            .build();

        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenReturn(documentWithNullType);

        mockMvc.perform(get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentNumber").value("INV-2024-003"))
            .andExpect(jsonPath("$.status").value("VALIDATING"))
            .andExpect(jsonPath("$.documentType").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} handles document without processedAt")
    void testGetInvoiceHandlesNullProcessedAt() throws Exception {
        IncomingDocument documentWithoutProcessedAt = IncomingDocument.builder()
            .id(testDocument.getId())
            .documentNumber("INV-2024-004")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.RECEIVED)
            .validationResult(ValidationResult.success())
            .receivedAt(Instant.now())
            .processedAt(null) // Not yet processed
            .build();

        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenReturn(documentWithoutProcessedAt);

        mockMvc.perform(get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receivedAt").exists())
            .andExpect(jsonPath("$.processedAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} handles document without validation result")
    void testGetInvoiceHandlesNullValidationResult() throws Exception {
        IncomingDocument documentWithoutValidation = IncomingDocument.builder()
            .id(testDocument.getId())
            .documentNumber("INV-2024-005")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(DocumentStatus.RECEIVED)
            .validationResult(null) // Not yet validated
            .receivedAt(Instant.now())
            .build();

        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenReturn(documentWithoutValidation);

        mockMvc.perform(get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validationResult").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} returns 500 for unexpected errors")
    void testGetInvoiceReturns500ForUnexpectedErrors() throws Exception {
        UUID testId = UUID.randomUUID();
        when(documentIntakeService.getDocument(testId))
            .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/api/v1/documents/{id}", testId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Failed to retrieve document status"));
    }

    @Test
    @DisplayName("POST /api/v1/documents with null correlation ID returns generated UUID")
    void testSubmitInvoiceWithNullCorrelationIdGeneratesUuid() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), any()))
            .thenReturn(testDocument);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").isString())
            .andExpect(jsonPath("$.correlationId").value(
                org.hamcrest.Matchers.matchesPattern(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    @DisplayName("POST /api/v1/documents handles empty correlation ID header")
    void testSubmitInvoiceHandlesEmptyCorrelationId() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), any()))
            .thenReturn(testDocument);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", ""))
            .andExpect(status().isAccepted());
    }
}
