package com.invoice.intake.infrastructure.config;

import com.invoice.intake.application.service.InvoiceIntakeService;
import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.infrastructure.validation.DocumentType;
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
    private final String intakeDlqTopic;

    // Document-type-specific Kafka topics
    private final Map<DocumentType, String> documentTypeTopics;

    public CamelConfig(
            InvoiceIntakeService intakeService,
            @Value("${app.kafka.topics.invoice-intake}") String invoiceIntakeTopic,
            @Value("${app.kafka.topics.intake-dlq}") String intakeDlqTopic,
            @Value("${app.kafka.topics.tax-invoice}") String taxInvoiceTopic,
            @Value("${app.kafka.topics.receipt}") String receiptTopic,
            @Value("${app.kafka.topics.invoice}") String invoiceTopic,
            @Value("${app.kafka.topics.debit-credit-note}") String debitCreditNoteTopic,
            @Value("${app.kafka.topics.cancellation}") String cancellationTopic,
            @Value("${app.kafka.topics.abbreviated}") String abbreviatedTopic) {
        this.intakeService = intakeService;
        this.invoiceIntakeTopic = invoiceIntakeTopic;
        this.intakeDlqTopic = intakeDlqTopic;

        // Initialize document type to topic mapping
        this.documentTypeTopics = Map.of(
            DocumentType.TAX_INVOICE, taxInvoiceTopic,
            DocumentType.RECEIPT, receiptTopic,
            DocumentType.INVOICE, invoiceTopic,
            DocumentType.DEBIT_CREDIT_NOTE, debitCreditNoteTopic,
            DocumentType.CANCELLATION_NOTE, cancellationTopic,
            DocumentType.ABBREVIATED_TAX_INVOICE, abbreviatedTopic
        );
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
                    exchange.getIn().setHeader("documentType", invoice.getDocumentType().name());
                } else {
                    // Null body signals validation failed
                    exchange.getIn().setBody(null);
                }
            })
            .choice()
                .when(simple("${body} == null"))
                    .log("Invoice validation failed, not forwarding")
                    .stop()
            .end()
            .marshal().json(JsonLibrary.Jackson)
            // Content-based routing by document type
            .choice()
                .when(header("documentType").isEqualTo(DocumentType.TAX_INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.TAX_INVOICE))
                    .log("Published TaxInvoice to topic: " + documentTypeTopics.get(DocumentType.TAX_INVOICE))
                .when(header("documentType").isEqualTo(DocumentType.RECEIPT.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.RECEIPT))
                    .log("Published Receipt to topic: " + documentTypeTopics.get(DocumentType.RECEIPT))
                .when(header("documentType").isEqualTo(DocumentType.INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.INVOICE))
                    .log("Published Invoice to topic: " + documentTypeTopics.get(DocumentType.INVOICE))
                .when(header("documentType").isEqualTo(DocumentType.DEBIT_CREDIT_NOTE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.DEBIT_CREDIT_NOTE))
                    .log("Published DebitCreditNote to topic: " + documentTypeTopics.get(DocumentType.DEBIT_CREDIT_NOTE))
                .when(header("documentType").isEqualTo(DocumentType.CANCELLATION_NOTE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.CANCELLATION_NOTE))
                    .log("Published CancellationNote to topic: " + documentTypeTopics.get(DocumentType.CANCELLATION_NOTE))
                .when(header("documentType").isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.ABBREVIATED_TAX_INVOICE))
                    .log("Published AbbreviatedTaxInvoice to topic: " + documentTypeTopics.get(DocumentType.ABBREVIATED_TAX_INVOICE))
                .otherwise()
                    .log("Unknown document type: ${header.documentType}, sending to DLQ")
                    .to("kafka:" + intakeDlqTopic)
            .end()
            // Mark as forwarded
            .process(exchange -> {
                String invoiceId = exchange.getIn().getHeader("invoiceId", String.class);
                if (invoiceId != null) {
                    intakeService.markForwarded(java.util.UUID.fromString(invoiceId));
                }
            });

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
                    exchange.getIn().setHeader("documentType", invoice.getDocumentType().name());
                } else {
                    // Null body signals validation failed
                    exchange.getIn().setBody(null);
                }
            })
            .choice()
                .when(simple("${body} == null"))
                    .log("Invoice validation failed, not forwarding")
                    .stop()
            .end()
            .marshal().json(JsonLibrary.Jackson)
            // Content-based routing by document type
            .choice()
                .when(header("documentType").isEqualTo(DocumentType.TAX_INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.TAX_INVOICE))
                    .log("Published TaxInvoice to topic: " + documentTypeTopics.get(DocumentType.TAX_INVOICE))
                .when(header("documentType").isEqualTo(DocumentType.RECEIPT.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.RECEIPT))
                    .log("Published Receipt to topic: " + documentTypeTopics.get(DocumentType.RECEIPT))
                .when(header("documentType").isEqualTo(DocumentType.INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.INVOICE))
                    .log("Published Invoice to topic: " + documentTypeTopics.get(DocumentType.INVOICE))
                .when(header("documentType").isEqualTo(DocumentType.DEBIT_CREDIT_NOTE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.DEBIT_CREDIT_NOTE))
                    .log("Published DebitCreditNote to topic: " + documentTypeTopics.get(DocumentType.DEBIT_CREDIT_NOTE))
                .when(header("documentType").isEqualTo(DocumentType.CANCELLATION_NOTE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.CANCELLATION_NOTE))
                    .log("Published CancellationNote to topic: " + documentTypeTopics.get(DocumentType.CANCELLATION_NOTE))
                .when(header("documentType").isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE.name()))
                    .to("kafka:" + documentTypeTopics.get(DocumentType.ABBREVIATED_TAX_INVOICE))
                    .log("Published AbbreviatedTaxInvoice to topic: " + documentTypeTopics.get(DocumentType.ABBREVIATED_TAX_INVOICE))
                .otherwise()
                    .log("Unknown document type: ${header.documentType}, sending to DLQ")
                    .to("kafka:" + intakeDlqTopic)
            .end()
            // Mark as forwarded
            .process(exchange -> {
                String invoiceId = exchange.getIn().getHeader("invoiceId", String.class);
                if (invoiceId != null) {
                    intakeService.markForwarded(java.util.UUID.fromString(invoiceId));
                }
            });
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
        event.put("documentType", invoice.getDocumentType().name());
        return event;
    }
}
