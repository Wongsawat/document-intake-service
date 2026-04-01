package com.wpanther.document.intake.infrastructure.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for rate limiting.
 * <p>
 * Properties are loaded from app.rate-limit.* prefix in application.yml:
 * - enabled: Enable/disable rate limiting
 * - requests-per-second: Maximum requests per second per client
 * - time-period-seconds: Time period for rate limiting
 */
@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /**
     * Enable or disable rate limiting.
     */
    private boolean enabled = true;

    /**
     * Maximum requests per second per client IP.
     * Default: 10 requests/second
     */
    private long requestsPerSecond = 10;

    /**
     * Time period in seconds for the rate limit.
     * Default: 60 seconds (1 minute)
     */
    private long timePeriodSeconds = 60;
}
