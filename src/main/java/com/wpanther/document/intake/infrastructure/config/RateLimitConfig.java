package com.wpanther.document.intake.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limiting configuration using Apache Camel Throttler.
 * <p>
 * Provides token bucket rate limiting to prevent abuse and DoS attacks.
 * Rate limiting is applied via Camel's throttler component in routes.
 * <p>
 * Configuration via app.rate-limit.* properties:
 * - enabled: Enable/disable rate limiting (default: true)
 * - requests-per-second: Maximum requests per second (default: 10)
 * - time-period-seconds: Time period for rate limit (default: 60)
 * <p>
 * Rate limiting can be disabled by setting:
 * {@code app.rate-limit.enabled=false}
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    name = "app.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
        log.info("Configuring rate limiting: {} requests/{} seconds per client",
            properties.getRequestsPerSecond(), properties.getTimePeriodSeconds());
    }

    /**
     * Calculate the maximum requests per period.
     */
    public long getMaximumRequestsPerPeriod() {
        return properties.getRequestsPerSecond() * properties.getTimePeriodSeconds();
    }
}
