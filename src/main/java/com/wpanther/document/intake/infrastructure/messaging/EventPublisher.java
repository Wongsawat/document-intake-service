package com.wpanther.document.intake.infrastructure.messaging;

import com.wpanther.document.intake.domain.event.DocumentReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for integration events using Apache Camel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    /**
     * Publish document received event
     */
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
