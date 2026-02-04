package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight event published immediately when a document is received (before validation).
 * This event is used for counting all received documents regardless of validation outcome.
 */
@Getter
public class DocumentReceivedCountingEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "document.received.counting";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("receivedAt")
    private final Instant receivedAt;

    /**
     * Create a new document received counting event.
     * This constructor is used when publishing a new event.
     *
     * @param documentId the unique identifier of the document
     * @param correlationId the correlation ID for tracking
     * @param receivedAt the timestamp when the document was received
     */
    public DocumentReceivedCountingEvent(String documentId, String correlationId, Instant receivedAt) {
        super();
        this.documentId = documentId;
        this.correlationId = correlationId;
        this.receivedAt = receivedAt;
    }

    /**
     * Constructor used by JSON deserializer when consuming from Kafka.
     */
    @JsonCreator
    public DocumentReceivedCountingEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("receivedAt") Instant receivedAt
    ) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.correlationId = correlationId;
        this.receivedAt = receivedAt;
    }
}
