package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.event.DocumentReceivedEvent;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.infrastructure.messaging.EventPublisher;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Apache Camel routes for document intake
 */
@Component
@Slf4j
public class CamelConfig extends RouteBuilder {

    private final DocumentIntakeService intakeService;
    private final EventPublisher eventPublisher;
    private final String documentIntakeTopic;
    private final String intakeDlqTopic;
    private final String kafkaBrokers;
    private final String documentReceivedTopic;

    // Document-type-specific Kafka topics
    private final Map<DocumentType, String> documentTypeTopics;

    public CamelConfig(
            DocumentIntakeService intakeService,
            EventPublisher eventPublisher,
            @Value("${app.kafka.topics.invoice-intake}") String documentIntakeTopic,
            @Value("${app.kafka.topics.intake-dlq}") String intakeDlqTopic,
            @Value("${app.kafka.topics.tax-invoice}") String taxInvoiceTopic,
            @Value("${app.kafka.topics.receipt}") String receiptTopic,
            @Value("${app.kafka.topics.invoice}") String invoiceTopic,
            @Value("${app.kafka.topics.debit-credit-note}") String debitCreditNoteTopic,
            @Value("${app.kafka.topics.cancellation}") String cancellationTopic,
            @Value("${app.kafka.topics.abbreviated}") String abbreviatedTopic,
            @Value("${app.kafka.topics.document-received:document.received}") String documentReceivedTopic,
            @Value("${app.kafka.bootstrap-servers}") String kafkaBrokers) {
        this.intakeService = intakeService;
        this.eventPublisher = eventPublisher;
        this.documentIntakeTopic = documentIntakeTopic;
        this.intakeDlqTopic = intakeDlqTopic;
        this.documentReceivedTopic = documentReceivedTopic;
        this.kafkaBrokers = kafkaBrokers;

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

        // NEW: Document received counting event producer route (before validation)
        // This lightweight event counts ALL received documents regardless of validation outcome
        from("direct:publish-document-counting")
            .routeId("document-counting-producer")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + documentReceivedTopic + "?brokers=" + kafkaBrokers)
            .log("Published document counting event to " + documentReceivedTopic + " topic");

        // NEW: Document received statistics event producer route (after validation)
        // This event contains full document details and is routed to document-type-specific topics
        from("direct:publish-document-received-stats")
            .routeId("document-stats-producer")
            .marshal().json(JsonLibrary.Jackson)
            .routingSlip().simple("${header.targetTopic}")
            .log("Published document statistics event to document-type-specific topic");

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
                    DocumentReceivedEvent event = new DocumentReceivedEvent(
                        document.getId().toString(),
                        document.getInvoiceNumber(),
                        document.getXmlContent(),
                        document.getCorrelationId(),
                        document.getDocumentType().name()
                    );
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
            .process(exchange -> {
                DocumentReceivedEvent event = exchange.getIn().getBody(DocumentReceivedEvent.class);
                String documentType = exchange.getIn().getHeader("documentType", String.class);
                String targetTopic = documentTypeTopics.get(DocumentType.valueOf(documentType));
                eventPublisher.publishDocumentReceived(event, targetTopic);
            })
            .log("Published document to topic")
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
                    DocumentReceivedEvent event = new DocumentReceivedEvent(
                        document.getId().toString(),
                        document.getInvoiceNumber(),
                        document.getXmlContent(),
                        document.getCorrelationId(),
                        document.getDocumentType().name()
                    );
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
            .process(exchange -> {
                DocumentReceivedEvent event = exchange.getIn().getBody(DocumentReceivedEvent.class);
                String documentType = exchange.getIn().getHeader("documentType", String.class);
                String targetTopic = documentTypeTopics.get(DocumentType.valueOf(documentType));
                eventPublisher.publishDocumentReceived(event, targetTopic);
            })
            .log("Published document to topic")
            // Mark as forwarded
            .process(exchange -> {
                String documentId = exchange.getIn().getHeader("documentId", String.class);
                if (documentId != null) {
                    intakeService.markForwarded(java.util.UUID.fromString(documentId));
                }
            });

        // Document received event producer route (legacy - for backward compatibility)
        from("direct:publish-document-received")
            .routeId("document-received-producer")
            .marshal().json(JsonLibrary.Jackson)
            .routingSlip().simple("${header.targetTopic}");
    }
}
