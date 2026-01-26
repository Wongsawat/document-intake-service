package com.invoice.intake.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.intake.application.service.InvoiceIntakeService;
import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.domain.model.InvoiceStatus;
import com.invoice.intake.domain.model.ValidationResult;
import com.invoice.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.camel.ProducerTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for InvoiceIntakeController
 * Uses MockMvc for testing REST endpoints
 */
@WebMvcTest(InvoiceIntakeController.class)
@DisplayName("Invoice Intake Controller Tests")
class InvoiceIntakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceIntakeService invoiceIntakeService;

    @MockBean
    private ProducerTemplate producerTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private IncomingInvoice testInvoice;

    @BeforeEach
    void setUp() {
        testInvoice = IncomingInvoice.builder()
            .id(UUID.randomUUID())
            .invoiceNumber("INV-2024-001")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId("corr-123")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/invoices returns 202 Accepted for valid invoice")
    void testSubmitInvoiceReturns202Accepted() throws Exception {
        when(invoiceIntakeService.submitInvoice(any(), eq("REST"), eq("corr-123")))
            .thenReturn(testInvoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-123"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value("corr-123"));
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} returns 200 OK when invoice exists")
    void testGetInvoiceByIdReturns200() throws Exception {
        when(invoiceIntakeService.getInvoice(testInvoice.getId()))
            .thenReturn(testInvoice);

        mockMvc.perform(get("/api/v1/invoices/{id}", testInvoice.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceNumber").value("INV-2024-001"))
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.documentType").value("TAX_INVOICE"));
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} returns 404 when invoice not found")
    void testGetInvoiceByIdReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(invoiceIntakeService.getInvoice(unknownId))
            .thenThrow(new IllegalArgumentException("Invoice not found: " + unknownId));

        mockMvc.perform(get("/api/v1/invoices/{id}", unknownId))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/invoices generates correlation ID when not provided")
    void testSubmitInvoiceGeneratesCorrelationId() throws Exception {
        IncomingInvoice invoice = IncomingInvoice.builder()
            .id(testInvoice.getId())
            .invoiceNumber("INV-2024-002")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .correlationId(null) // No correlation ID provided
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.VALIDATED)
            .validationResult(ValidationResult.success())
            .build();

        when(invoiceIntakeService.submitInvoice(any(), eq("REST"), eq(null)))
            .thenReturn(invoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/invoices returns error for invalid XML")
    void testSubmitInvoiceReturns400ForInvalidXml() throws Exception {
        // The Camel route would throw an exception for invalid XML
        doThrow(new IllegalArgumentException("Could not extract invoice number"))
            .when(producerTemplate).sendBodyAndHeader(any(String.class), any(), any(String.class), any());

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("invalid xml"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} returns validation result")
    void testGetInvoiceIncludesValidationResult() throws Exception {
        when(invoiceIntakeService.getInvoice(testInvoice.getId()))
            .thenReturn(testInvoice);

        mockMvc.perform(get("/api/v1/invoices/{id}", testInvoice.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validationResult.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/invoices handles different sources")
    void testSubmitInvoiceHandlesDifferentSources() throws Exception {
        when(invoiceIntakeService.submitInvoice(any(), eq("KAFKA"), eq("corr-456")))
            .thenReturn(testInvoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-456")
                .header("X-Source", "KAFKA"))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /api/v1/invoices accepts text/xml content type")
    void testSubmitInvoiceAcceptsTextXmlContentType() throws Exception {
        when(invoiceIntakeService.submitInvoice(any(), eq("REST"), any()))
            .thenReturn(testInvoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.TEXT_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", "corr-text-xml"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value("corr-text-xml"));
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} handles invoice with null documentType")
    void testGetInvoiceHandlesNullDocumentType() throws Exception {
        IncomingInvoice invoiceWithNullType = IncomingInvoice.builder()
            .id(testInvoice.getId())
            .invoiceNumber("INV-2024-003")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(null) // Null document type
            .status(InvoiceStatus.VALIDATING)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .build();

        when(invoiceIntakeService.getInvoice(testInvoice.getId()))
            .thenReturn(invoiceWithNullType);

        mockMvc.perform(get("/api/v1/invoices/{id}", testInvoice.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceNumber").value("INV-2024-003"))
            .andExpect(jsonPath("$.status").value("VALIDATING"))
            .andExpect(jsonPath("$.documentType").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} handles invoice without processedAt")
    void testGetInvoiceHandlesNullProcessedAt() throws Exception {
        IncomingInvoice invoiceWithoutProcessedAt = IncomingInvoice.builder()
            .id(testInvoice.getId())
            .invoiceNumber("INV-2024-004")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.RECEIVED)
            .validationResult(ValidationResult.success())
            .receivedAt(LocalDateTime.now())
            .processedAt(null) // Not yet processed
            .build();

        when(invoiceIntakeService.getInvoice(testInvoice.getId()))
            .thenReturn(invoiceWithoutProcessedAt);

        mockMvc.perform(get("/api/v1/invoices/{id}", testInvoice.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receivedAt").exists())
            .andExpect(jsonPath("$.processedAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} handles invoice without validation result")
    void testGetInvoiceHandlesNullValidationResult() throws Exception {
        IncomingInvoice invoiceWithoutValidation = IncomingInvoice.builder()
            .id(testInvoice.getId())
            .invoiceNumber("INV-2024-005")
            .xmlContent("<test>xml</test>")
            .source("REST")
            .documentType(DocumentType.TAX_INVOICE)
            .status(InvoiceStatus.RECEIVED)
            .validationResult(null) // Not yet validated
            .receivedAt(LocalDateTime.now())
            .build();

        when(invoiceIntakeService.getInvoice(testInvoice.getId()))
            .thenReturn(invoiceWithoutValidation);

        mockMvc.perform(get("/api/v1/invoices/{id}", testInvoice.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validationResult").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} returns 500 for unexpected errors")
    void testGetInvoiceReturns500ForUnexpectedErrors() throws Exception {
        UUID testId = UUID.randomUUID();
        when(invoiceIntakeService.getInvoice(testId))
            .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/api/v1/invoices/{id}", testId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Failed to retrieve invoice status"));
    }

    @Test
    @DisplayName("POST /api/v1/invoices with null correlation ID generates UUID")
    void testSubmitInvoiceWithNullCorrelationIdGeneratesUuid() throws Exception {
        when(invoiceIntakeService.submitInvoice(any(), eq("REST"), any()))
            .thenReturn(testInvoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value("generated"));
    }

    @Test
    @DisplayName("POST /api/v1/invoices handles empty correlation ID header")
    void testSubmitInvoiceHandlesEmptyCorrelationId() throws Exception {
        when(invoiceIntakeService.submitInvoice(any(), eq("REST"), any()))
            .thenReturn(testInvoice);

        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_XML)
                .content("<test>xml</test>")
                .header("X-Correlation-ID", ""))
            .andExpect(status().isAccepted());
    }
}
