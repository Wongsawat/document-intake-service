package com.wpanther.document.intake.infrastructure.messaging;

import com.wpanther.document.intake.domain.event.DocumentReceivedCountingEvent;
import com.wpanther.document.intake.domain.event.DocumentReceivedEvent;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publisher for integration events using Apache Camel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    // Document type to topic mapping (for statistics events)
    private final Map<DocumentType, String> documentTypeTopics;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    public EventPublisher(
            ProducerTemplate producerTemplate,
            @Value("${app.kafka.topics.tax-invoice}") String taxInvoiceTopic,
            @Value("${app.kafka.topics.receipt}") String receiptTopic,
            @Value("${app.kafka.topics.invoice}") String invoiceTopic,
            @Value("${app.kafka.topics.debit-credit-note}") String debitCreditNoteTopic,
            @Value("${app.kafka.topics.cancellation}") String cancellationTopic,
            @Value("${app.kafka.topics.abbreviated}") String abbreviatedTopic) {
        this.producerTemplate = producerTemplate;
        this.documentTypeTopics = Map.of(
            DocumentType.TAX_INVOICE, taxInvoiceTopic,
            DocumentType.RECEIPT, receiptTopic,
            DocumentType.INVOICE, invoiceTopic,
            DocumentType.DEBIT_CREDIT_NOTE, debitCreditNoteTopic,
            DocumentType.CANCELLATION_NOTE, cancellationTopic,
            DocumentType.ABBREVIATED_TAX_INVOICE, abbreviatedTopic
        );
    }

    /**
     * Publish document received counting event (before validation).
     * This lightweight event counts ALL received documents regardless of validation outcome.
     *
     * @param event the document received counting event
     */
    public void publishDocumentReceivedCounting(DocumentReceivedCountingEvent event) {
        log.info("Publishing document received counting event for document: {}", event.getDocumentId());
        try {
            producerTemplate.sendBody("direct:publish-document-counting", event);
            log.debug("Successfully published document counting event: {}", event.getDocumentId());
        } catch (Exception e) {
            log.error("Failed to publish document counting event: {}", event.getDocumentId(), e);
            // Don't throw - counting failures shouldn't block document processing
        }
    }

    /**
     * Publish document received event to document-type-specific topic for statistics (after validation).
     * This event is only published for validated documents and contains full document details.
     *
     * @param event the document received event
     * @param documentType the type of document for routing
     */
    public void publishDocumentReceivedForStatistics(DocumentReceivedEvent event, DocumentType documentType) {
        String targetTopic = documentTypeTopics.get(documentType);
        log.info("Publishing document received event for statistics: documentId={}, documentType={}, targetTopic={}",
            event.getDocumentId(), documentType, targetTopic);
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-document-received-stats",
                event,
                "targetTopic",
                "kafka:" + targetTopic + "?brokers=" + kafkaBrokers
            );
            log.debug("Successfully published document statistics event: {}", event.getDocumentId());
        } catch (Exception e) {
            log.error("Failed to publish document statistics event: {}", event.getDocumentId(), e);
            // Don't throw - statistics failures shouldn't block document processing
        }
    }

    /**
     * Publish document received event (legacy method for backward compatibility).
     * This routes the event to the specified target topic.
     *
     * @param event the document received event
     * @param targetTopic the target Kafka topic
     * @deprecated Use publishDocumentReceivedForStatistics instead
     */
    @Deprecated
    public void publishDocumentReceived(DocumentReceivedEvent event, String targetTopic) {
        log.info("Publishing document received event for document: {} to topic: {}",
            event.getDocumentId(), targetTopic);
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-document-received",
                event,
                "targetTopic",
                targetTopic
            );
            log.info("Successfully published document received event: {}", event.getDocumentId());
        } catch (Exception e) {
            log.error("Failed to publish document received event: {}", event.getDocumentId(), e);
            throw e;
        }
    }
}
