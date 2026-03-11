package com.wpanther.document.intake.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
import com.wpanther.document.intake.application.port.out.DocumentEventPublisher;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for integration events using the outbox pattern.
 * <p>
 * Events are written to the outbox table within the same transaction as domain state changes.
 * Debezium CDC reads the outbox table and publishes events to Kafka topics asynchronously.
 * This provides guaranteed delivery and prevents event loss during failures.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher implements DocumentEventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Publish command to start saga in orchestrator.
     * Must be called within an existing transaction.
     *
     * @param command the start saga command
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishStartSagaCommand(StartSagaCommand command) {
        Map<String, String> headers = new HashMap<>();
        headers.put("documentType", command.getDocumentType());
        if (command.getCorrelationId() != null) {
            headers.put("correlationId", command.getCorrelationId());
        }

        String partitionKey = command.getCorrelationId() != null
            ? command.getCorrelationId()
            : command.getDocumentId();

        outboxService.saveWithRouting(
            command,
            "IncomingDocument",
            command.getDocumentId(),
            "saga.commands.orchestrator",
            partitionKey,
            toJson(headers)
        );

        log.info("Published StartSagaCommand for document: {}", command.getDocumentId());
    }

    /**
     * Publish trace event for notification-service.
     * Must be called within an existing transaction.
     *
     * @param event the document received trace event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishTraceEvent(DocumentReceivedTraceEvent event) {
        Map<String, String> headers = new HashMap<>();
        headers.put("documentType", event.getDocumentType());
        if (event.getCorrelationId() != null) {
            headers.put("correlationId", event.getCorrelationId());
        }

        String partitionKey = event.getCorrelationId() != null
            ? event.getCorrelationId()
            : event.getDocumentId();

        outboxService.saveWithRouting(
            event,
            "IncomingDocument",
            event.getDocumentId(),
            "trace.document.received",
            partitionKey,
            toJson(headers)
        );

        log.debug("Published DocumentReceivedTraceEvent for document: {}", event.getDocumentId());
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }
}
