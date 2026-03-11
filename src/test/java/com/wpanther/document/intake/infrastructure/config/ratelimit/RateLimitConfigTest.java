package com.wpanther.document.intake.infrastructure.config.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitConfig
 */
@DisplayName("RateLimitConfig Unit Tests")
class RateLimitConfigTest {

    private RateLimitProperties properties;
    private RateLimitConfig rateLimitConfig;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setRequestsPerSecond(10);
        properties.setTimePeriodSeconds(60);

        rateLimitConfig = new RateLimitConfig(properties);
    }

    @Test
    @DisplayName("RateLimitConfig constructor initializes correctly")
    void testConstructorInitializesCorrectly() {
        assertThat(rateLimitConfig).isNotNull();
    }

    @Test
    @DisplayName("getMaximumRequestsPerPeriod calculates correctly")
    void testGetMaximumRequestsPerPeriod() {
        long maxRequests = rateLimitConfig.getMaximumRequestsPerPeriod();
        // 10 requests/second * 60 seconds = 600 requests per period
        assertThat(maxRequests).isEqualTo(600);
    }

    @Test
    @DisplayName("getMaximumRequestsPerPeriod with different values")
    void testGetMaximumRequestsWithDifferentValues() {
        properties.setRequestsPerSecond(5);
        properties.setTimePeriodSeconds(30);

        RateLimitConfig config = new RateLimitConfig(properties);
        long maxRequests = config.getMaximumRequestsPerPeriod();

        // 5 requests/second * 30 seconds = 150 requests per period
        assertThat(maxRequests).isEqualTo(150);
    }

    @Test
    @DisplayName("getMaximumRequestsPerPeriod handles edge cases")
    void testGetMaximumRequestsPerPeriodHandlesEdgeCases() {
        // Test with 1 request per second
        properties.setRequestsPerSecond(1);
        properties.setTimePeriodSeconds(60);

        RateLimitConfig config = new RateLimitConfig(properties);
        assertThat(config.getMaximumRequestsPerPeriod()).isEqualTo(60);

        // Test with high values
        properties.setRequestsPerSecond(100);
        properties.setTimePeriodSeconds(1);

        RateLimitConfig config2 = new RateLimitConfig(properties);
        assertThat(config2.getMaximumRequestsPerPeriod()).isEqualTo(100);
    }

    @Test
    @DisplayName("RateLimitConfig with default properties")
    void testRateLimitConfigWithDefaultProperties() {
        RateLimitProperties defaultProperties = new RateLimitProperties();
        RateLimitConfig config = new RateLimitConfig(defaultProperties);

        // Default: 10 req/s * 60 sec = 600
        assertThat(config.getMaximumRequestsPerPeriod()).isEqualTo(600);
    }
}
