package com.wpanther.document.intake.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaOutboxEventRepository Tests")
class JpaOutboxEventRepositoryTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    private JpaOutboxEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaOutboxEventRepository(springRepository);
    }

    @Test
    @DisplayName("Should save outbox event")
    void shouldSaveOutboxEvent() {
        OutboxEvent domainEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("IncomingDocument")
                .aggregateId("doc-123")
                .eventType("StartSagaCommand")
                .payload("{\"data\":\"test\"}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

        OutboxEventEntity savedEntity = OutboxEventEntity.fromDomain(domainEvent);
        when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(savedEntity);

        OutboxEvent result = repository.save(domainEvent);

        assertThat(result).isNotNull();
        verify(springRepository).save(any(OutboxEventEntity.class));
    }

    @Test
    @DisplayName("Should find outbox event by id")
    void shouldFindById() {
        UUID id = UUID.randomUUID();
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("IncomingDocument")
                .aggregateId("doc-123")
                .eventType("StartSagaCommand")
                .payload("{\"data\":\"test\"}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

        when(springRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<OutboxEvent> result = repository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("Should return empty when not found by id")
    void shouldReturnEmptyWhenNotFoundById() {
        UUID id = UUID.randomUUID();
        when(springRepository.findById(id)).thenReturn(Optional.empty());

        Optional<OutboxEvent> result = repository.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find pending events")
    void shouldFindPendingEvents() {
        int limit = 10;
        List<OutboxEventEntity> entities = List.of(
                createEntity(OutboxStatus.PENDING),
                createEntity(OutboxStatus.PENDING)
        );

        when(springRepository.findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any()))
                .thenReturn(entities);

        List<OutboxEvent> result = repository.findPendingEvents(limit);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should find failed events")
    void shouldFindFailedEvents() {
        int limit = 10;
        List<OutboxEventEntity> entities = List.of(
                createEntity(OutboxStatus.FAILED),
                createEntity(OutboxStatus.FAILED)
        );

        when(springRepository.findFailedEventsOrderByCreatedAtAsc(any()))
                .thenReturn(entities);

        List<OutboxEvent> result = repository.findFailedEvents(limit);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should delete published events before timestamp")
    void shouldDeletePublishedBefore() {
        Instant before = Instant.now();
        when(springRepository.deletePublishedBefore(before)).thenReturn(5);

        int result = repository.deletePublishedBefore(before);

        assertThat(result).isEqualTo(5);
        verify(springRepository).deletePublishedBefore(before);
    }

    @Test
    @DisplayName("Should find events by aggregate")
    void shouldFindByAggregate() {
        String aggregateType = "IncomingDocument";
        String aggregateId = "doc-123";
        List<OutboxEventEntity> entities = List.of(
                createEntity(aggregateType, aggregateId)
        );

        when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                aggregateType, aggregateId)).thenReturn(entities);

        List<OutboxEvent> result = repository.findByAggregate(aggregateType, aggregateId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateType()).isEqualTo(aggregateType);
        assertThat(result.get(0).getAggregateId()).isEqualTo(aggregateId);
    }

    private OutboxEventEntity createEntity(OutboxStatus status) {
        return createEntity("IncomingDocument", "doc-" + UUID.randomUUID(), status);
    }

    private OutboxEventEntity createEntity(String aggregateType, String aggregateId) {
        return createEntity(aggregateType, aggregateId, OutboxStatus.PENDING);
    }

    private OutboxEventEntity createEntity(String aggregateType, String aggregateId, OutboxStatus status) {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType("StartSagaCommand")
                .payload("{\"data\":\"test\"}")
                .status(status)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }
}
