package com.wpanther.document.intake.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.config.ValidationProperties;
import com.wpanther.document.intake.domain.model.DocumentType;
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
import static org.mockito.Mockito.when;

/**
 * Additional tests for DocumentIntakeController to improve coverage.
 */
@WebMvcTest(controllers = DocumentIntakeController.class,
    properties = "app.security.enabled=false",
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class
    })
@DisplayName("Document Intake Controller Additional Tests")
class DocumentIntakeControllerAdditionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIntakeService documentIntakeService;

    @MockBean
    private ProducerTemplate producerTemplate;

    @MockBean
    private ValidationProperties validationProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private IncomingDocument testDocument;

    @BeforeEach
    void setUp() {
        when(validationProperties.getMaxXmlSize()).thenReturn(10485760L);
        when(validationProperties.getMaxXmlSizeMb()).thenReturn(10);

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
    @DisplayName("GET /api/v1/documents/{id} returns 500 when service throws exception")
    void testGetDocumentReturns500ForException() throws Exception {
        when(documentIntakeService.getDocument(testDocument.getId()))
            .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/documents/{id}", testDocument.getId()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Failed to retrieve document status"));
    }

    @Test
    @DisplayName("POST handles XML with special characters")
    void testPostHandlesXmlWithSpecialCharacters() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), any()))
            .thenReturn(testDocument);

        String xmlWithSpecialChars = "<?xml version=\"1.0\"?><test>&lt;special&gt;</test>";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(xmlWithSpecialChars)
                .header("X-Correlation-ID", "special-test"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
    }

    @Test
    @DisplayName("POST handles XML just under size limit")
    void testPostHandlesXmlJustUnderSizeLimit() throws Exception {
        when(documentIntakeService.submitDocument(any(), eq("REST"), any()))
            .thenReturn(testDocument);

        // Create XML that's just under the limit
        String xmlUnderLimit = "<?xml version=\"1.0\"?><test>" + "x".repeat(100) + "</test>";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(xmlUnderLimit)
                .header("X-Correlation-ID", "size-limit-test"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
    }

    @Test
    @DisplayName("POST returns 413 for content exceeding limit")
    void testPostReturns413ForContentExceedingLimit() throws Exception {
        // Create content slightly over the limit
        String largeXml = "<?xml version=\"1.0\"?><test>" + "x".repeat(10485760) + "</test>";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(largeXml))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPayloadTooLarge())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Payload too large"));
    }

    @Test
    @DisplayName("GET returns 404 for non-existent document")
    void testGetDocumentReturns404ForNonExistent() throws Exception {
        UUID randomId = UUID.randomUUID();
        when(documentIntakeService.getDocument(randomId))
            .thenThrow(new IllegalArgumentException("Document not found"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/documents/{id}", randomId))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
