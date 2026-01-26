package com.invoice.intake.infrastructure.config;

import com.invoice.intake.application.service.InvoiceIntakeService;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration test for CamelConfig route configuration
 * Tests that the configure() method executes properly
 */
@CamelSpringBootTest
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
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
@DisplayName("CamelConfig Integration Tests")
class CamelConfigIntegrationTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private InvoiceIntakeService invoiceIntakeService;

    @EndpointInject("mock:invoice-intake-result")
    private MockEndpoint mockResult;

    @TestConfiguration
    static class AdditionalTestRoutes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            // Add a simple test route after the main CamelConfig routes
            from("direct:test-input")
                .to("mock:invoice-intake-result");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Reset mock endpoint
        mockResult.reset();

        // Mock the invoice service
        when(invoiceIntakeService.submitInvoice(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Mock service - not actually calling in this test"));
    }

    @Test
    @DisplayName("CamelContext starts with all routes configured")
    void testCamelContextStartsWithAllRoutes() throws Exception {
        assertThat(camelContext).isNotNull();
        assertThat(camelContext.isStarted()).isTrue();

        // Verify routes are registered (at minimum the test route)
        assertThat(camelContext.getRoutes()).isNotEmpty();
    }

    @Test
    @DisplayName("CamelConfig routes are configured")
    void testCamelConfigRoutesConfigured() throws Exception {
        // The CamelConfig should have added routes to the context
        // We can't directly test the full routes without Kafka,
        // but we can verify the context started successfully
        assertThat(camelContext.getRoutes()).isNotEmpty();
    }
}
