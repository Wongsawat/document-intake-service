package com.wpanther.document.intake.integration.config;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test configuration for CDC integration tests.
 * <p>
 * Excludes Camel auto-configuration to prevent Kafka consumers from starting.
 * The tests verify CDC flow by consuming from Kafka directly.
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    CamelAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@Import(TestKafkaConsumerConfig.class)
@EnableJpaRepositories(basePackages = {
    "com.wpanther.document.intake.infrastructure.adapter.out"
})
@EntityScan(basePackages = {
    "com.wpanther.document.intake.infrastructure.adapter.out"
})
@ComponentScan(
    basePackages = {
        "com.wpanther.document.intake.domain",
        "com.wpanther.document.intake.application.service",
        "com.wpanther.document.intake.application.usecase",
        "com.wpanther.document.intake.infrastructure.adapter.out",
        "com.wpanther.document.intake.infrastructure.validation",
        "com.wpanther.document.intake.infrastructure.messaging",
        "com.wpanther.document.intake.infrastructure.config",
        "com.wpanther.saga.infrastructure"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*CamelConfig.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Controller.*")
    }
)
public class CdcTestConfiguration {
}
