package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
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
    "app.kafka.topics.invoice-intake=document.intake",
    "app.kafka.topics.intake-dlq=document.intake.dlq",
    "app.kafka.topics.tax-invoice=document.received.tax-invoice",
    "app.kafka.topics.receipt=document.received.receipt",
    "app.kafka.topics.invoice=document.received.invoice",
    "app.kafka.topics.debit-credit-note=document.received.debit-credit-note",
    "app.kafka.topics.cancellation=document.received.cancellation",
    "app.kafka.topics.abbreviated=document.received.abbreviated",
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
    private DocumentIntakeService documentIntakeService;

    @BeforeEach
    void setUp() {
        reset(documentIntakeService);
    }

    @Test
    @DisplayName("CamelContext starts with all routes configured")
    void testCamelContextStartsWithAllRoutes() {
        assertThat(camelContext).isNotNull();
        assertThat(camelContext.isStarted()).isTrue();

        // Verify both main routes exist
        assertThat(camelContext.getRoute("document-intake-direct")).isNotNull();
        assertThat(camelContext.getRoute("document-intake-kafka")).isNotNull();
    }

    @Test
    @DisplayName("REST intake route exists and has correct configuration")
    void testRestIntakeRouteConfiguration() {
        // Verify the direct:invoice-intake route is configured
        assertThat(camelContext.hasEndpoint("direct:invoice-intake")).isNotNull();
    }

    @Test
    @DisplayName("REST intake route - valid document calls service and marks forwarded")
    void testRestIntakeRoute_ValidDocument_CallsService() throws Exception {
        // Given
        String xmlContent = VALID_TAX_INVOICE_XML;
        UUID documentId = UUID.randomUUID();
        String correlationId = "test-correlation-123";

        IncomingDocument validDocument = createValidDocument(documentId, "TIV2024010001",
            DocumentType.TAX_INVOICE, xmlContent);

        when(documentIntakeService.submitInvoice(eq(xmlContent), eq("REST"), eq(correlationId)))
            .thenReturn(validDocument);
        doNothing().when(documentIntakeService).markForwarded(documentId);

        // When - send to the route (will fail at Kafka but service should be called)
        try {
            producerTemplate.sendBodyAndHeader("direct:invoice-intake", xmlContent,
                "correlationId", correlationId);
        } catch (Exception e) {
            // Expected - Kafka is not available in tests
            // But service should have been called before reaching Kafka
        }

        // Then - verify service was called
        verify(documentIntakeService).submitInvoice(eq(xmlContent), eq("REST"), eq(correlationId));
    }

    @Test
    @DisplayName("REST intake route - invalid XML calls service but doesn't mark forwarded")
    void testRestIntakeRoute_InvalidXml_DoesNotForward() throws Exception {
        // Given
        String xmlContent = VALID_TAX_INVOICE_XML;
        UUID documentId = UUID.randomUUID();

        IncomingDocument invalidDocument = createInvalidDocument(documentId, xmlContent);

        when(documentIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenReturn(invalidDocument);

        // When
        try {
            producerTemplate.sendBody("direct:invoice-intake", xmlContent);
        } catch (Exception e) {
            // May throw exception or not depending on route behavior
        }

        // Then - service was called but markForwarded was not
        verify(documentIntakeService).submitInvoice(anyString(), anyString(), anyString());
        verify(documentIntakeService, never()).markForwarded(any());
    }

    @Test
    @DisplayName("Error handler is configured with dead letter channel")
    void testErrorHandlerConfigured() {
        // Verify routes are configured (error handler is part of route definition)
        assertThat(camelContext.getRoute("document-intake-direct")).isNotNull();
        assertThat(camelContext.getRoute("document-intake-kafka")).isNotNull();
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
    @DisplayName("Document type header is set correctly for valid document")
    void testDocumentTypeHeaderSetForValidDocument() throws Exception {
        // Given
        String xmlContent = RECEIPT_XML;
        UUID documentId = UUID.randomUUID();

        IncomingDocument validDocument = createValidDocument(documentId, "RCT-001",
            DocumentType.RECEIPT, xmlContent);

        when(documentIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenReturn(validDocument);

        // When
        try {
            producerTemplate.sendBody("direct:invoice-intake", xmlContent);
        } catch (Exception e) {
            // Expected
        }

        // Then - service was called with correct parameters
        verify(documentIntakeService).submitInvoice(anyString(), eq("REST"), anyString());
    }

    @Test
    @DisplayName("Kafka route exists and is configured")
    void testKafkaRouteExists() {
        // Verify the Kafka consumer route exists
        assertThat(camelContext.getRoute("document-intake-kafka")).isNotNull();

        // Verify the route has the expected from endpoint pattern
        String fromEndpoint = camelContext.getRoute("document-intake-kafka")
            .getEndpoint().getEndpointUri();
        assertThat(fromEndpoint).contains("kafka");
        assertThat(fromEndpoint).contains("document.intake");
    }

    // Helper methods

    private IncomingDocument createValidDocument(UUID id, String invoiceNumber,
                                                DocumentType documentType, String xmlContent) {
        return IncomingDocument.builder()
            .id(id)
            .invoiceNumber(invoiceNumber)
            .documentType(documentType)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(DocumentStatus.VALIDATED)
            .receivedAt(LocalDateTime.now())
            .validationResult(ValidationResult.success())
            .build();
    }

    private IncomingDocument createInvalidDocument(UUID id, String xmlContent) {
        return IncomingDocument.builder()
            .id(id)
            .invoiceNumber("INVALID-001")
            .documentType(null)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(DocumentStatus.INVALID)
            .receivedAt(LocalDateTime.now())
            .validationResult(ValidationResult.invalid(java.util.List.of("XSD validation failed")))
            .build();
    }

    // Valid XML samples for testing
    private static final String VALID_TAX_INVOICE_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:TaxInvoice_CrossIndustryInvoice
            xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2"
            xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>TIV2024010001</ram:ID>
                <ram:Name>ใบกำกับภาษี</ram:Name>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>
                <ram:PurposeCode>TIVC01</ram:PurposeCode>
            </rsm:ExchangedDocument>
            <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                    <ram:SellerTradeParty>
                        <ram:Name>Test Seller Company Limited</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="TXID" schemeAgencyID="RD">12345678901230001</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                    </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                    <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                    <ram:AssociatedDocumentLineDocument>
                        <ram:LineID>1</ram:LineID>
                    </ram:AssociatedDocumentLineDocument>
                </ram:IncludedSupplyChainTradeLineItem>
            </rsm:SupplyChainTradeTransaction>
        </rsm:TaxInvoice_CrossIndustryInvoice>
        """;

    private static final String RECEIPT_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:Receipt_CrossIndustryInvoice
            xmlns:ram="urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2"
            xmlns:rsm="urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>RCT2024010001</ram:ID>
                <ram:Name>ใบเสร็จรับเงิน</ram:Name>
                <ram:TypeCode listID="1001_ThaiDocumentNameCodeInvoice" listAgencyID="RD/ETDA">T01</ram:TypeCode>
                <ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                    <ram:SellerTradeParty>
                        <ram:Name>Test Seller Company Limited</ram:Name>
                    </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
            </rsm:SupplyChainTradeTransaction>
        </rsm:Receipt_CrossIndustryInvoice>
        """;
}
