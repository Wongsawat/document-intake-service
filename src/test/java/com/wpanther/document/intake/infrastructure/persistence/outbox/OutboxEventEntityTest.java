package com.wpanther.document.intake.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OutboxEventEntity Tests")
class OutboxEventEntityTest {

    @Test
    @DisplayName("Should convert domain OutboxEvent to entity")
    void shouldConvertDomainToEntity() {
        OutboxEvent domain = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("IncomingDocument")
                .aggregateId("doc-123")
                .eventType("StartSagaCommand")
                .payload("{\"data\":\"test\"}")
                .createdAt(Instant.now())
                .publishedAt(Instant.now())
                .status(OutboxStatus.PUBLISHED)
                .retryCount(2)
                .errorMessage("Test error")
                .topic("saga.commands.orchestrator")
                .partitionKey("corr-123")
                .headers("{\"key\":\"value\"}")
                .build();

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(domain);

        assertThat(entity.getId()).isEqualTo(domain.getId());
        assertThat(entity.getAggregateType()).isEqualTo(domain.getAggregateType());
        assertThat(entity.getAggregateId()).isEqualTo(domain.getAggregateId());
        assertThat(entity.getEventType()).isEqualTo(domain.getEventType());
        assertThat(entity.getPayload()).isEqualTo(domain.getPayload());
        assertThat(entity.getCreatedAt()).isEqualTo(domain.getCreatedAt());
        assertThat(entity.getPublishedAt()).isEqualTo(domain.getPublishedAt());
        assertThat(entity.getStatus()).isEqualTo(domain.getStatus());
        assertThat(entity.getRetryCount()).isEqualTo(domain.getRetryCount());
        assertThat(entity.getErrorMessage()).isEqualTo(domain.getErrorMessage());
        assertThat(entity.getTopic()).isEqualTo(domain.getTopic());
        assertThat(entity.getPartitionKey()).isEqualTo(domain.getPartitionKey());
        assertThat(entity.getHeaders()).isEqualTo(domain.getHeaders());
    }

    @Test
    @DisplayName("Should convert entity to domain OutboxEvent")
    void shouldConvertEntityToDomain() {
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("IncomingDocument")
                .aggregateId("doc-123")
                .eventType("StartSagaCommand")
                .payload("{\"data\":\"test\"}")
                .createdAt(Instant.now())
                .publishedAt(Instant.now())
                .status(OutboxStatus.PUBLISHED)
                .retryCount(2)
                .errorMessage("Test error")
                .topic("saga.commands.orchestrator")
                .partitionKey("corr-123")
                .headers("{\"key\":\"value\"}")
                .build();

        OutboxEvent domain = entity.toDomain();

        assertThat(domain.getId()).isEqualTo(entity.getId());
        assertThat(domain.getAggregateType()).isEqualTo(entity.getAggregateType());
        assertThat(domain.getAggregateId()).isEqualTo(entity.getAggregateId());
        assertThat(domain.getEventType()).isEqualTo(entity.getEventType());
        assertThat(domain.getPayload()).isEqualTo(entity.getPayload());
        assertThat(domain.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(domain.getPublishedAt()).isEqualTo(entity.getPublishedAt());
        assertThat(domain.getStatus()).isEqualTo(entity.getStatus());
        assertThat(domain.getRetryCount()).isEqualTo(entity.getRetryCount());
        assertThat(domain.getErrorMessage()).isEqualTo(entity.getErrorMessage());
        assertThat(domain.getTopic()).isEqualTo(entity.getTopic());
        assertThat(domain.getPartitionKey()).isEqualTo(entity.getPartitionKey());
        assertThat(domain.getHeaders()).isEqualTo(entity.getHeaders());
    }

    @Test
    @DisplayName("Should initialize defaults on pre-persist")
    void shouldInitializeDefaultsOnPrePersist() {
        OutboxEventEntity entity = new OutboxEventEntity();

        entity.onCreate();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should not override existing values on pre-persist")
    void shouldNotOverrideExistingValuesOnPrePersist() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        int retryCount = 5;
        OutboxStatus status = OutboxStatus.FAILED;

        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .status(status)
                .createdAt(createdAt)
                .retryCount(retryCount)
                .build();

        entity.onCreate();

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getStatus()).isEqualTo(status);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getRetryCount()).isEqualTo(retryCount);
    }
}
