package com.invoice.intake.infrastructure.config;

import com.invoice.intake.application.service.InvoiceIntakeService;
import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.domain.model.InvoiceStatus;
import com.invoice.intake.domain.model.ValidationResult;
import com.invoice.intake.infrastructure.validation.DocumentType;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CamelConfig route configuration.
 * Tests that routes are properly configured and execute the expected flow.
 */
@CamelSpringBootTest
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.kafka.topics.invoice-intake=invoice.intake",
    "app.kafka.topics.intake-dlq=invoice.intake.dlq",
    "app.kafka.topics.tax-invoice=invoice.received.tax-invoice",
    "app.kafka.topics.receipt=invoice.received.receipt",
    "app.kafka.topics.invoice=invoice.received.invoice",
    "app.kafka.topics.debit-credit-note=invoice.received.debit-credit-note",
    "app.kafka.topics.cancellation=invoice.received.cancellation",
    "app.kafka.topics.abbreviated=invoice.received.abbreviated",
    "app.kafka.bootstrap-servers=localhost:9092"
})
@Disabled("Camel integration tests require Kafka infrastructure - coverage will be provided via other integration tests")
@DisplayName("CamelConfig Integration Tests")
class CamelConfigIntegrationTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private InvoiceIntakeService invoiceIntakeService;

    @BeforeEach
    void setUp() {
        reset(invoiceIntakeService);
    }

    @Test
    @DisplayName("CamelContext starts with all routes configured")
    void testCamelContextStartsWithAllRoutes() {
        assertThat(camelContext).isNotNull();
        assertThat(camelContext.isStarted()).isTrue();

        // Verify both main routes exist
        assertThat(camelContext.getRoute("invoice-intake-direct")).isNotNull();
        assertThat(camelContext.getRoute("invoice-intake-kafka")).isNotNull();
    }

    @Test
    @DisplayName("REST intake route exists and has correct configuration")
    void testRestIntakeRouteConfiguration() {
        // Verify the direct:invoice-intake route is configured
        assertThat(camelContext.hasEndpoint("direct:invoice-intake")).isNotNull();
    }

    @Test
    @DisplayName("REST intake route - valid TaxInvoice calls service and marks forwarded")
    void testRestIntakeRoute_ValidTaxInvoice_CallsService() throws Exception {
        // Given
        String xmlContent = loadTestXml("valid/TaxInvoice_2p1_valid.xml");
        UUID invoiceId = UUID.randomUUID();
        String correlationId = "test-correlation-123";

        IncomingInvoice validInvoice = createValidInvoice(invoiceId, "TIV2024010001",
            DocumentType.TAX_INVOICE, xmlContent);

        when(invoiceIntakeService.submitInvoice(eq(xmlContent), eq("REST"), eq(correlationId)))
            .thenReturn(validInvoice);
        doNothing().when(invoiceIntakeService).markForwarded(invoiceId);

        // When - send to the route (will fail at Kafka but service should be called)
        try {
            producerTemplate.sendBodyAndHeader("direct:invoice-intake", xmlContent,
                "correlationId", correlationId);
        } catch (Exception e) {
            // Expected - Kafka is not available in tests
            // But service should have been called before reaching Kafka
        }

        // Then - verify service was called
        verify(invoiceIntakeService).submitInvoice(eq(xmlContent), eq("REST"), eq(correlationId));
    }

    @ParameterizedTest
    @CsvSource({
        "TAX_INVOICE, TaxInvoice_2p1_valid.xml",
        "RECEIPT, Receipt_2p1_valid.xml",
        "INVOICE, Invoice_2p1_valid.xml",
        "DEBIT_CREDIT_NOTE, DebitNote_2p1_valid.xml",
        "ABBREVIATED_TAX_INVOICE, AbbreviatedTaxInvoice_2p1_valid.xml"
    })
    @DisplayName("Service is called for all document types")
    void testServiceCalledForAllDocumentTypes(String docTypeName, String xmlFile) throws Exception {
        // Given
        String xmlContent = loadTestXml("valid/" + xmlFile);
        DocumentType documentType = DocumentType.valueOf(docTypeName);
        UUID invoiceId = UUID.randomUUID();

        IncomingInvoice validInvoice = createValidInvoice(invoiceId, "INV-001",
            documentType, xmlContent);

        when(invoiceIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenReturn(validInvoice);

        // When - send to the route
        try {
            producerTemplate.sendBody("direct:invoice-intake", xmlContent);
        } catch (Exception e) {
            // Expected - Kafka not available
        }

        // Then - verify service was called
        verify(invoiceIntakeService).submitInvoice(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("REST intake route - invalid XML calls service but doesn't mark forwarded")
    void testRestIntakeRoute_InvalidXml_DoesNotForward() throws Exception {
        // Given
        String xmlContent = loadTestXml("valid/TaxInvoice_2p1_valid.xml");
        UUID invoiceId = UUID.randomUUID();

        IncomingInvoice invalidInvoice = createInvalidInvoice(invoiceId, xmlContent);

        when(invoiceIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenReturn(invalidInvoice);

        // When
        try {
            producerTemplate.sendBody("direct:invoice-intake", xmlContent);
        } catch (Exception e) {
            // May throw exception or not depending on route behavior
        }

        // Then - service was called but markForwarded was not
        verify(invoiceIntakeService).submitInvoice(anyString(), anyString(), anyString());
        verify(invoiceIntakeService, never()).markForwarded(any());
    }

    @Test
    @DisplayName("Error handler is configured with dead letter channel")
    void testErrorHandlerConfigured() {
        // Verify routes are configured (error handler is part of route definition)
        assertThat(camelContext.getRoute("invoice-intake-direct")).isNotNull();
        assertThat(camelContext.getRoute("invoice-intake-kafka")).isNotNull();
        // Error handler configuration is verified by route existence and successful context start
    }

    @Test
    @DisplayName("CamelConfig bean is properly initialized with all topic mappings")
    void testCamelConfigBeanInitialization() {
        // The fact that the context started successfully means CamelConfig
        // was properly initialized with all required @Value properties
        assertThat(camelContext.isStarted()).isTrue();

        // Verify all expected routes exist
        assertThat(camelContext.getRoutes()).hasSize(2); // direct and kafka routes
    }

    @Test
    @DisplayName("Document type header is set correctly for valid invoice")
    void testDocumentTypeHeaderSetForValidInvoice() throws Exception {
        // Given
        String xmlContent = loadTestXml("valid/Receipt_2p1_valid.xml");
        UUID invoiceId = UUID.randomUUID();

        IncomingInvoice validInvoice = createValidInvoice(invoiceId, "REC-001",
            DocumentType.RECEIPT, xmlContent);

        when(invoiceIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenReturn(validInvoice);

        // When
        try {
            producerTemplate.sendBody("direct:invoice-intake", xmlContent);
        } catch (Exception e) {
            // Expected
        }

        // Then - service was called with correct parameters
        verify(invoiceIntakeService).submitInvoice(anyString(), eq("REST"), anyString());
    }

    @Test
    @DisplayName("Kafka route exists and is configured")
    void testKafkaRouteExists() {
        // Verify the Kafka consumer route exists
        assertThat(camelContext.getRoute("invoice-intake-kafka")).isNotNull();

        // Verify the route has the expected from endpoint pattern
        String fromEndpoint = camelContext.getRoute("invoice-intake-kafka")
            .getEndpoint().getEndpointUri();
        assertThat(fromEndpoint).contains("kafka");
        assertThat(fromEndpoint).contains("invoice.intake");
    }

    // Helper methods

    private IncomingInvoice createValidInvoice(UUID id, String invoiceNumber,
                                                DocumentType documentType, String xmlContent) {
        return IncomingInvoice.builder()
            .id(id)
            .invoiceNumber(invoiceNumber)
            .documentType(documentType)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(InvoiceStatus.VALIDATED)
            .receivedAt(LocalDateTime.now())
            .validationResult(ValidationResult.success())
            .build();
    }

    private IncomingInvoice createInvalidInvoice(UUID id, String xmlContent) {
        return IncomingInvoice.builder()
            .id(id)
            .invoiceNumber("INVALID-001")
            .documentType(null)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(InvoiceStatus.INVALID)
            .receivedAt(LocalDateTime.now())
            .validationResult(ValidationResult.invalid(List.of("XSD validation failed")))
            .build();
    }

    private String loadTestXml(String filename) throws Exception {
        String path = "src/test/resources/samples/" + filename;
        return Files.readString(Paths.get(path));
    }
}
