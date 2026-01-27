package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache Camel routes for document intake
 */
@Component
@Slf4j
public class CamelConfig extends RouteBuilder {

    private final DocumentIntakeService intakeService;
    private final String documentIntakeTopic;
    private final String intakeDlqTopic;

    // Document-type-specific Kafka topics
    private final Map<DocumentType, String> documentTypeTopics;

    public CamelConfig(
            DocumentIntakeService intakeService,
            @Value("${app.kafka.topics.invoice-intake}") String documentIntakeTopic,
            @Value("${app.kafka.topics.intake-dlq}") String intakeDlqTopic,
            @Value("${app.kafka.topics.tax-invoice}") String taxInvoiceTopic,
            @Value("${app.kafka.topics.receipt}") String receiptTopic,
            @Value("${app.kafka.topics.invoice}") String invoiceTopic,
            @Value("${app.kafka.topics.debit-credit-note}") String debitCreditNoteTopic,
            @Value("${app.kafka.topics.cancellation}") String cancellationTopic,
            @Value("${app.kafka.topics.abbreviated}") String abbreviatedTopic) {
        this.intakeService = intakeService;
        this.documentIntakeTopic = documentIntakeTopic;
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
            .routeId("document-intake-direct")
            .log("Received document via REST")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);

                // Submit and validate
                IncomingDocument document = intakeService.submitInvoice(xmlContent, "REST", correlationId);

                // Prepare event if valid
                if (document.isValid()) {
                    Map<String, Object> event = createDocumentReceivedEvent(document);
                    exchange.getIn().setBody(event);
                    exchange.getIn().setHeader("documentId", document.getId().toString());
                    exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                } else {
                    // Null body signals validation failed
                    exchange.getIn().setBody(null);
                }
            })
            .choice()
                .when(simple("${body} == null"))
                    .log("Document validation failed, not forwarding")
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
                String documentId = exchange.getIn().getHeader("documentId", String.class);
                if (documentId != null) {
                    intakeService.markForwarded(java.util.UUID.fromString(documentId));
                }
            });

        // Kafka intake route
        from("kafka:" + documentIntakeTopic + "?groupId=intake-service")
            .routeId("document-intake-kafka")
            .log("Received document from Kafka: ${header[kafka.KEY]}")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("kafka.KEY", String.class);

                // Submit and validate
                IncomingDocument document = intakeService.submitInvoice(xmlContent, "KAFKA", correlationId);

                // Prepare event if valid
                if (document.isValid()) {
                    Map<String, Object> event = createDocumentReceivedEvent(document);
                    exchange.getIn().setBody(event);
                    exchange.getIn().setHeader("documentId", document.getId().toString());
                    exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                } else {
                    // Null body signals validation failed
                    exchange.getIn().setBody(null);
                }
            })
            .choice()
                .when(simple("${body} == null"))
                    .log("Document validation failed, not forwarding")
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
                String documentId = exchange.getIn().getHeader("documentId", String.class);
                if (documentId != null) {
                    intakeService.markForwarded(java.util.UUID.fromString(documentId));
                }
            });
    }

    /**
     * Create DocumentReceivedEvent payload
     */
    private Map<String, Object> createDocumentReceivedEvent(IncomingDocument document) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", java.util.UUID.randomUUID().toString());
        event.put("occurredAt", java.time.Instant.now().toString());
        event.put("eventType", "document.received");
        event.put("version", 1);
        event.put("documentId", document.getId().toString());
        event.put("invoiceNumber", document.getInvoiceNumber());
        event.put("xmlContent", document.getXmlContent());
        event.put("correlationId", document.getCorrelationId());
        event.put("documentType", document.getDocumentType().name());
        return event;
    }
}
