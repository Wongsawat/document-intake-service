package com.wpanther.document.intake.integration.config;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Test configuration for REST API CDC integration tests.
 * <p>
 * Unlike CdcTestConfiguration, this configuration:
 * <ul>
 *   <li>Does NOT exclude CamelAutoConfiguration (ProducerTemplate needed by REST controller)</li>
 *   <li>Does NOT exclude Controller components (REST API is fully exercised)</li>
 *   <li>Excludes ONLY KafkaAutoConfiguration (prevents Spring from auto-creating Kafka consumer beans)</li>
 * </ul>
 * <p>
 * The direct:document-intake Camel route is active for synchronous REST request processing.
 * Kafka consumer is disabled via {@code app.kafka.consumer.auto-startup=false} property.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = {
    "com.wpanther.document.intake.infrastructure.adapter.out.persistence"
})
@EntityScan(basePackages = {
    "com.wpanther.document.intake.infrastructure.adapter.out.persistence"
})
@ComponentScan(
    basePackages = {
        "com.wpanther.document.intake.domain",
        "com.wpanther.document.intake.application.service",
        "com.wpanther.document.intake.infrastructure.adapter.out.persistence",
        "com.wpanther.document.intake.infrastructure.validation",
        "com.wpanther.document.intake.infrastructure.messaging",
        "com.wpanther.document.intake.infrastructure.config",
        "com.wpanther.saga.infrastructure"
    },
    // NOTE: Controllers are INCLUDED (unlike CdcTestConfiguration which excludes them)
    excludeFilters = {
        // No exclusions - include everything needed for REST API + direct Camel route
    }
)
@EnableTransactionManagement
@Import({TestKafkaConsumerConfig.class, CamelAutoConfiguration.class, FlywayAutoConfiguration.class})
public class RestApiCdcTestConfiguration {
}
