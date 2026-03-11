package com.wpanther.document.intake.infrastructure.config.outbox;

import com.wpanther.document.intake.infrastructure.adapter.out.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.document.intake.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OutboxEventRepository bean.
 * <p>
 * Registers the JPA implementation of saga-commons OutboxEventRepository,
 * enabling document-intake-service to use the outbox pattern for reliable
 * event publishing.
 * <p>
 * The @ConditionalOnMissingBean annotation allows for flexibility in testing
 * or alternative implementations.
 */
@Configuration
public class OutboxConfig {

    /**
     * Creates the OutboxEventRepository bean.
     * This bean is injected by saga-commons' OutboxService and OutboxCleanupService.
     *
     * @param springRepository The Spring Data JPA repository
     * @return JPA implementation of OutboxEventRepository
     */
    @Bean
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository outboxEventRepository(SpringDataOutboxRepository springRepository) {
        return new JpaOutboxEventRepository(springRepository);
    }
}
