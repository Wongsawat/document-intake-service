package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for document intake.
 * <p>
 * Consumer routes receive documents from REST API and Kafka.
 * Producer routes have been removed - events are now published via the outbox pattern
 * and Debezium CDC handles Kafka publishing.
 */
@Component
@Slf4j
public class CamelConfig extends RouteBuilder {

    private final DocumentIntakeService intakeService;
    private final String documentIntakeTopic;
    private final String intakeDlqTopic;

    public CamelConfig(
            DocumentIntakeService intakeService,
            @Value("${app.kafka.topics.invoice-intake}") String documentIntakeTopic,
            @Value("${app.kafka.topics.intake-dlq}") String intakeDlqTopic) {
        this.intakeService = intakeService;
        this.documentIntakeTopic = documentIntakeTopic;
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
        // Documents submitted via REST API are processed and events are published via outbox
        from("direct:invoice-intake")
            .routeId("document-intake-direct")
            .log("Received document via REST")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);

                // Submit and validate - this now publishes events via outbox
                IncomingDocument document = intakeService.submitInvoice(xmlContent, "REST", correlationId);

                // Set response headers
                exchange.getIn().setHeader("documentId", document.getId().toString());
                exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                exchange.getIn().setHeader("isValid", document.isValid());
            })
            .log("Document processed: documentId=${header.documentId}, isValid=${header.isValid}");

        // Kafka intake route
        // Documents consumed from Kafka are processed and events are published via outbox
        from("kafka:" + documentIntakeTopic + "?groupId=intake-service")
            .routeId("document-intake-kafka")
            .log("Received document from Kafka: ${header[kafka.KEY]}")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("kafka.KEY", String.class);

                // Submit and validate - this now publishes events via outbox
                IncomingDocument document = intakeService.submitInvoice(xmlContent, "KAFKA", correlationId);

                exchange.getIn().setHeader("documentId", document.getId().toString());
                exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                exchange.getIn().setHeader("isValid", document.isValid());
            })
            .log("Document processed: documentId=${header.documentId}, isValid=${header.isValid}");
    }
}
