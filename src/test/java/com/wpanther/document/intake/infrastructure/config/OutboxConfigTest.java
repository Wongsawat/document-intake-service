package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.document.intake.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxConfig Tests")
class OutboxConfigTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    @Test
    @DisplayName("Should create OutboxEventRepository bean")
    void shouldCreateOutboxEventRepositoryBean() {
        OutboxConfig config = new OutboxConfig();

        OutboxEventRepository repository = config.outboxEventRepository(springRepository);

        assertThat(repository).isNotNull();
        assertThat(repository).isInstanceOf(JpaOutboxEventRepository.class);
    }
}
