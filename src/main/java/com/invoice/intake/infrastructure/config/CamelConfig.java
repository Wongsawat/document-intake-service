package com.invoice.intake.infrastructure.config;

import com.invoice.intake.application.service.InvoiceIntakeService;
import com.invoice.intake.domain.model.IncomingInvoice;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache Camel routes for invoice intake
 */
@Component
@Slf4j
public class CamelConfig extends RouteBuilder {

    private final InvoiceIntakeService intakeService;
    private final String invoiceIntakeTopic;
    private final String invoiceReceivedTopic;
    private final String intakeDlqTopic;

    public CamelConfig(
            InvoiceIntakeService intakeService,
            @Value("${app.kafka.topics.invoice-intake}") String invoiceIntakeTopic,
            @Value("${app.kafka.topics.invoice-received}") String invoiceReceivedTopic,
            @Value("${app.kafka.topics.intake-dlq}") String intakeDlqTopic) {
        this.intakeService = intakeService;
        this.invoiceIntakeTopic = invoiceIntakeTopic;
        this.invoiceReceivedTopic = invoiceReceivedTopic;
        this.intakeDlqTopic = intakeDlqTopic;
    }

    @Override
    public void configure() throws Exception {

        // Error handling - Dead Letter Channel
        errorHandler(deadLetterChannel("kafka:" + intakeDlqTopic)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .logExhausted(true));

        // REST intake route
        from("direct:invoice-intake")
            .routeId("invoice-intake-direct")
            .log("Received invoice via REST")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);

                // Submit and validate
                IncomingInvoice invoice = intakeService.submitInvoice(xmlContent, "REST", correlationId);

                // Prepare event if valid
                if (invoice.isValid()) {
                    Map<String, Object> event = createInvoiceReceivedEvent(invoice);
                    exchange.getIn().setBody(event);
                    exchange.getIn().setHeader("invoiceId", invoice.getId().toString());
                }
            })
            .choice()
                .when(simple("${body} != null"))
                    .marshal().json(JsonLibrary.Jackson)
                    .to("kafka:" + invoiceReceivedTopic)
                    .process(exchange -> {
                        String invoiceId = exchange.getIn().getHeader("invoiceId", String.class);
                        if (invoiceId != null) {
                            intakeService.markForwarded(java.util.UUID.fromString(invoiceId));
                        }
                    })
                    .log("Published invoice received event to Kafka")
                .otherwise()
                    .log("Invoice validation failed, not forwarding")
            .end();

        // Kafka intake route
        from("kafka:" + invoiceIntakeTopic + "?groupId=intake-service")
            .routeId("invoice-intake-kafka")
            .log("Received invoice from Kafka: ${header[kafka.KEY]}")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("kafka.KEY", String.class);

                // Submit and validate
                IncomingInvoice invoice = intakeService.submitInvoice(xmlContent, "KAFKA", correlationId);

                // Prepare event if valid
                if (invoice.isValid()) {
                    Map<String, Object> event = createInvoiceReceivedEvent(invoice);
                    exchange.getIn().setBody(event);
                    exchange.getIn().setHeader("invoiceId", invoice.getId().toString());
                }
            })
            .choice()
                .when(simple("${body} != null"))
                    .marshal().json(JsonLibrary.Jackson)
                    .to("kafka:" + invoiceReceivedTopic)
                    .process(exchange -> {
                        String invoiceId = exchange.getIn().getHeader("invoiceId", String.class);
                        if (invoiceId != null) {
                            intakeService.markForwarded(java.util.UUID.fromString(invoiceId));
                        }
                    })
                    .log("Published invoice received event to Kafka")
                .otherwise()
                    .log("Invoice validation failed, not forwarding")
            .end();
    }

    /**
     * Create InvoiceReceivedEvent payload
     */
    private Map<String, Object> createInvoiceReceivedEvent(IncomingInvoice invoice) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", java.util.UUID.randomUUID().toString());
        event.put("occurredAt", java.time.Instant.now().toString());
        event.put("eventType", "invoice.received");
        event.put("version", 1);
        event.put("invoiceId", invoice.getId().toString());
        event.put("invoiceNumber", invoice.getInvoiceNumber());
        event.put("xmlContent", invoice.getXmlContent());
        event.put("correlationId", invoice.getCorrelationId());
        return event;
    }
}
