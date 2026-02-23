package com.wpanther.document.intake.infrastructure.health;

import com.wpanther.document.intake.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for the transactional outbox.
 * <p>
 * Reports:
 * <ul>
 *   <li>{@code DOWN} when there are FAILED outbox events — these require manual intervention.</li>
 *   <li>{@code OUT_OF_SERVICE} when the PENDING backlog exceeds the warning threshold,
 *       which may indicate Debezium CDC is not consuming events.</li>
 *   <li>{@code UP} otherwise.</li>
 * </ul>
 * <p>
 * Exposed via {@code /actuator/health} as the {@code outbox} component.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private static final long PENDING_BACKLOG_WARNING_THRESHOLD = 100;

    private final SpringDataOutboxRepository outboxRepository;

    public OutboxHealthIndicator(SpringDataOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Health health() {
        long failedCount = outboxRepository.countByStatus(OutboxStatus.FAILED);
        long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

        if (failedCount > 0) {
            return Health.down()
                    .withDetail("failedEvents", failedCount)
                    .withDetail("pendingEvents", pendingCount)
                    .withDetail("message", "Outbox has " + failedCount + " failed event(s) requiring manual intervention")
                    .build();
        }

        if (pendingCount >= PENDING_BACKLOG_WARNING_THRESHOLD) {
            return Health.outOfService()
                    .withDetail("failedEvents", failedCount)
                    .withDetail("pendingEvents", pendingCount)
                    .withDetail("message", "Outbox pending backlog (" + pendingCount + ") exceeds threshold (" + PENDING_BACKLOG_WARNING_THRESHOLD + "). Check Debezium CDC connector.")
                    .build();
        }

        return Health.up()
                .withDetail("failedEvents", failedCount)
                .withDetail("pendingEvents", pendingCount)
                .build();
    }
}
