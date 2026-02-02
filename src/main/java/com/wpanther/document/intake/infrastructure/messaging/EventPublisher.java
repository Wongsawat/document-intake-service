package com.wpanther.document.intake.infrastructure.messaging;

import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
public class EventPublisher {

    private final OutboxService outboxService;

    /**
     * Publish command to start saga in orchestrator.
     * Must be called within an existing transaction.
     *
     * @param command the start saga command
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishStartSagaCommand(StartSagaCommand command) {
        outboxService.writeEvent(
            "IncomingDocument",
            command.getDocumentId(),
            "StartSagaCommand",
            "saga.commands.orchestrator",
            command,
            Map.of(
                "correlationId", command.getCorrelationId(),
                "documentType", command.getDocumentType()
            )
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
        outboxService.writeEvent(
            "IncomingDocument",
            event.getDocumentId(),
            "DocumentReceivedTraceEvent",
            "trace.document.received",
            event,
            Map.of(
                "correlationId", event.getCorrelationId(),
                "documentType", event.getDocumentType()
            )
        );
        log.debug("Published DocumentReceivedTraceEvent for document: {}", event.getDocumentId());
    }
}
