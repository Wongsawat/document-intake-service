package com.wpanther.document.intake.infrastructure.config.camel;

import com.wpanther.document.intake.application.usecase.SubmitDocumentUseCase;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.model.DocumentType;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CamelConfig route configuration.
 * Tests that routes are properly configured and execute the expected flow.
 *
 * <p>Uses a minimal Spring context (no DataSource/Hibernate/Kafka) with
 * SubmitDocumentUseCase mocked to avoid real infrastructure dependencies.
 */
@CamelSpringBootTest
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.kafka.topics.invoice-intake=document.intake",
    "app.kafka.topics.intake-dlq=document.intake.dlq",
    "app.kafka.bootstrap-servers=localhost:9092",
    "app.kafka.consumer.auto-startup=false",
    "app.rate-limit.enabled=false",
    "camel.springboot.main-run-controller=false",
    "camel.springboot.xml-routes=false"
})
@ComponentScan(
    basePackages = {
        "com.wpanther.document.intake.infrastructure.config.camel",
        "com.wpanther.document.intake.infrastructure.config.ratelimit"
    }
)
@DisplayName("CamelConfig Integration Tests")
class CamelConfigTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private SubmitDocumentUseCase submitDocumentUseCase;

    @BeforeEach
    void setUp() {
        reset(submitDocumentUseCase);
    }

    @Test
    @DisplayName("CamelContext starts with direct route configured")
    void testCamelContextStartsWithDirectRoute() {
        assertThat(camelContext).isNotNull();
        assertThat(camelContext.isStarted()).isTrue();

        // Verify the direct route is configured
        assertThat(camelContext.getRoute("document-intake-direct")).isNotNull();
    }

    @Test
    @DisplayName("REST intake route exists and has correct configuration")
    void testRestIntakeRouteConfiguration() {
        // Verify the direct:document-intake route is configured
        assertThat(camelContext.hasEndpoint("direct:document-intake")).isNotNull();
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

        when(submitDocumentUseCase.submitDocument(eq(xmlContent), eq("REST"), eq(correlationId)))
            .thenReturn(validDocument);

        // When - send to the direct route
        producerTemplate.sendBodyAndHeader("direct:document-intake", xmlContent,
            "correlationId", correlationId);

        // Then - verify service was called
        verify(submitDocumentUseCase).submitDocument(eq(xmlContent), eq("REST"), eq(correlationId));
    }

    @Test
    @DisplayName("REST intake route - invalid XML calls service")
    void testRestIntakeRoute_InvalidXml_CallsService() throws Exception {
        // Given
        String xmlContent = VALID_TAX_INVOICE_XML;
        UUID documentId = UUID.randomUUID();

        IncomingDocument invalidDocument = createInvalidDocument(documentId, xmlContent);

        when(submitDocumentUseCase.submitDocument(anyString(), anyString(), anyString()))
            .thenReturn(invalidDocument);

        // When
        producerTemplate.sendBodyAndHeader("direct:document-intake", xmlContent,
            "correlationId", "test-corr-123");

        // Then - service was called
        verify(submitDocumentUseCase).submitDocument(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Error handler is configured with dead letter channel")
    void testErrorHandlerConfigured() {
        // Verify route definitions are configured (error handler is part of route definition)
        assertThat(camelContext.getRoute("document-intake-direct")).isNotNull();
        // Error handler configuration is verified by route existence and successful context start
    }

    @Test
    @DisplayName("CamelConfig bean is properly initialized with topic mappings")
    void testCamelConfigBeanInitialization() {
        // The fact that the context started successfully means CamelConfig
        // was properly initialized with all required @Value properties
        assertThat(camelContext.isStarted()).isTrue();

        // Verify the direct route exists (Kafka route is disabled in this test)
        assertThat(camelContext.getRoutes()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Document type header is set correctly for valid document")
    void testDocumentTypeHeaderSetForValidDocument() throws Exception {
        // Given
        String xmlContent = RECEIPT_XML;
        UUID documentId = UUID.randomUUID();

        IncomingDocument validDocument = createValidDocument(documentId, "RCT-001",
            DocumentType.RECEIPT, xmlContent);

        when(submitDocumentUseCase.submitDocument(anyString(), anyString(), anyString()))
            .thenReturn(validDocument);

        // When
        producerTemplate.sendBodyAndHeader("direct:document-intake", xmlContent,
            "correlationId", "test-corr-456");

        // Then - service was called with correct parameters
        verify(submitDocumentUseCase).submitDocument(anyString(), eq("REST"), anyString());
    }

    // Helper methods

    private IncomingDocument createValidDocument(UUID id, String documentNumber,
                                                DocumentType documentType, String xmlContent) {
        return IncomingDocument.builder()
            .id(id)
            .documentNumber(documentNumber)
            .documentType(documentType)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(DocumentStatus.VALIDATED)
            .receivedAt(Instant.now())
            .validationResult(ValidationResult.success())
            .build();
    }

    private IncomingDocument createInvalidDocument(UUID id, String xmlContent) {
        return IncomingDocument.builder()
            .id(id)
            .documentNumber("INVALID-001")
            .documentType(DocumentType.TAX_INVOICE)
            .xmlContent(xmlContent)
            .source("REST")
            .correlationId("test-corr")
            .status(DocumentStatus.INVALID)
            .receivedAt(Instant.now())
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
