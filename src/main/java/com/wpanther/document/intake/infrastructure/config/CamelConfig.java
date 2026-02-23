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

        // REST intake route — no error handler so exceptions propagate back to
        // ProducerTemplate.sendBodyAndHeader() and are caught by the REST controller.
        // The controller maps IllegalArgumentException → 400, IllegalStateException → 409,
        // and any other exception → 500. A global DLQ here would silently swallow those
        // exceptions and always return 202 regardless of validation outcome.
        from("direct:document-intake")
            .errorHandler(noErrorHandler())
            .routeId("document-intake-direct")
            .log("Received document via REST")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);

                IncomingDocument document = intakeService.submitDocument(xmlContent, "REST", correlationId);

                exchange.getIn().setHeader("documentId", document.getId().toString());
                exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                exchange.getIn().setHeader("isValid", document.isValid());
            })
            .log("Document processed: documentId=${header.documentId}, isValid=${header.isValid}");

        // Kafka intake route — dead letter channel for messages that cannot be processed
        // after retries. autoCommitEnable=false ensures offsets are only committed after
        // the exchange completes successfully (at-least-once delivery semantics).
        from("kafka:" + documentIntakeTopic + "?groupId=intake-service&autoCommitEnable=false")
            .errorHandler(deadLetterChannel("kafka:" + intakeDlqTopic)
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .logExhausted(true))
            .routeId("document-intake-kafka")
            .log("Received document from Kafka: ${header[kafka.KEY]}")
            .process(exchange -> {
                String xmlContent = exchange.getIn().getBody(String.class);
                String correlationId = exchange.getIn().getHeader("kafka.KEY", String.class);

                IncomingDocument document = intakeService.submitDocument(xmlContent, "KAFKA", correlationId);

                exchange.getIn().setHeader("documentId", document.getId().toString());
                exchange.getIn().setHeader("documentType", document.getDocumentType().name());
                exchange.getIn().setHeader("isValid", document.isValid());
            })
            .log("Document processed: documentId=${header.documentId}, isValid=${header.isValid}");
    }
}
