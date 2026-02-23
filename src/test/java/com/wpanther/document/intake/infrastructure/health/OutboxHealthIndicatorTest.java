package com.wpanther.document.intake.infrastructure.health;

import com.wpanther.document.intake.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OutboxHealthIndicator
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxHealthIndicator Unit Tests")
class OutboxHealthIndicatorTest {

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    private OutboxHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new OutboxHealthIndicator(outboxRepository, 100);
    }

    @Test
    @DisplayName("Health returns UP when no failed or pending events")
    void testHealthReturnsUpWhenNoEvents() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("failedEvents", 0L);
        assertThat(health.getDetails()).containsEntry("pendingEvents", 0L);
    }

    @Test
    @DisplayName("Health returns UP when pending events below threshold")
    void testHealthReturnsUpWhenPendingBelowThreshold() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(50L);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pendingEvents", 50L);
    }

    @Test
    @DisplayName("Health returns DOWN when failed events exist")
    void testHealthReturnsDownWhenFailedEventsExist() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(5L);
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(10L);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("failedEvents", 5L);
        assertThat(health.getDetails()).containsKey("message");
    }

    @Test
    @DisplayName("Health returns OUT_OF_SERVICE when pending exceeds threshold")
    void testHealthReturnsOutOfServiceWhenPendingExceedsThreshold() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(150L);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("pendingEvents", 150L);
        assertThat(health.getDetails()).containsKey("message");
    }

    @Test
    @DisplayName("Health returns DOWN when database unreachable")
    void testHealthReturnsDownWhenDatabaseUnreachable() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED))
            .thenThrow(new RuntimeException("Database connection failed"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("message");
    }

    @Test
    @DisplayName("Constructor with custom pending threshold")
    void testConstructorWithCustomThreshold() {
        OutboxHealthIndicator indicator = new OutboxHealthIndicator(outboxRepository, 50);
        assertThat(indicator).isNotNull();
    }
}
