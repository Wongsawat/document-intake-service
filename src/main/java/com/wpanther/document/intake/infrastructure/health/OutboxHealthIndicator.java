package com.wpanther.document.intake.infrastructure.health;

import com.wpanther.document.intake.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Boot Actuator health indicator for the transactional outbox.
 * <p>
 * Reports:
 * <ul>
 *   <li>{@code DOWN} when there are FAILED outbox events — these require manual intervention.</li>
 *   <li>{@code OUT_OF_SERVICE} when the PENDING backlog exceeds the configured threshold,
 *       which may indicate the Debezium CDC connector is not consuming events.</li>
 *   <li>{@code DOWN} when the database is unreachable.</li>
 *   <li>{@code UP} otherwise.</li>
 * </ul>
 * <p>
 * The pending backlog threshold is configurable via {@code app.outbox.pending-threshold}
 * (default: 100). Exposed at {@code /actuator/health} as the {@code outbox} component.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OutboxHealthIndicator.class);

    private final SpringDataOutboxRepository outboxRepository;
    private final long pendingThreshold;

    public OutboxHealthIndicator(
            SpringDataOutboxRepository outboxRepository,
            @Value("${app.outbox.pending-threshold:100}") long pendingThreshold) {
        this.outboxRepository = outboxRepository;
        this.pendingThreshold = pendingThreshold;
    }

    @Override
    @Transactional(readOnly = true)
    public Health health() {
        try {
            long failedCount = outboxRepository.countByStatus(OutboxStatus.FAILED);
            long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

            if (failedCount > 0) {
                log.warn("Outbox health: {} failed event(s) require manual intervention", failedCount);
                return Health.down()
                        .withDetail("failedEvents", failedCount)
                        .withDetail("pendingEvents", pendingCount)
                        .withDetail("message", "Outbox has " + failedCount + " failed event(s) requiring manual intervention")
                        .build();
            }

            if (pendingCount >= pendingThreshold) {
                log.warn("Outbox health: pending backlog {} exceeds threshold {}", pendingCount, pendingThreshold);
                return Health.outOfService()
                        .withDetail("failedEvents", failedCount)
                        .withDetail("pendingEvents", pendingCount)
                        .withDetail("message", "Outbox pending backlog (" + pendingCount + ") exceeds threshold (" + pendingThreshold + "). Check Debezium CDC connector.")
                        .build();
            }

            return Health.up()
                    .withDetail("failedEvents", failedCount)
                    .withDetail("pendingEvents", pendingCount)
                    .build();

        } catch (Exception e) {
            log.error("Outbox health check failed — database unreachable", e);
            return Health.down()
                    .withDetail("message", "Database unreachable: " + e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
