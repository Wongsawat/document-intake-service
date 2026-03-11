package com.wpanther.document.intake.infrastructure.config.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitProperties
 */
@DisplayName("RateLimitProperties Unit Tests")
class RateLimitPropertiesTest {

    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
    }

    @Test
    @DisplayName("Default values are set correctly")
    void testDefaultValues() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getRequestsPerSecond()).isEqualTo(10);
        assertThat(properties.getTimePeriodSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Setter and getter for enabled")
    void testEnabledSetterGetter() {
        properties.setEnabled(false);
        assertThat(properties.isEnabled()).isFalse();

        properties.setEnabled(true);
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Setter and getter for requests per second")
    void testRequestsPerSecondSetterGetter() {
        properties.setRequestsPerSecond(20);
        assertThat(properties.getRequestsPerSecond()).isEqualTo(20);

        properties.setRequestsPerSecond(100);
        assertThat(properties.getRequestsPerSecond()).isEqualTo(100);
    }

    @Test
    @DisplayName("Setter and getter for time period seconds")
    void testTimePeriodSecondsSetterGetter() {
        properties.setTimePeriodSeconds(30);
        assertThat(properties.getTimePeriodSeconds()).isEqualTo(30);

        properties.setTimePeriodSeconds(120);
        assertThat(properties.getTimePeriodSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("Multiple properties can be set together")
    void testMultiplePropertiesSetTogether() {
        properties.setEnabled(false);
        properties.setRequestsPerSecond(50);
        properties.setTimePeriodSeconds(90);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRequestsPerSecond()).isEqualTo(50);
        assertThat(properties.getTimePeriodSeconds()).isEqualTo(90);
    }
}
