package com.wpanther.document.intake.application.dto.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EventStatus Tests")
class EventStatusTest {

    @Test
    @DisplayName("Should return correct value for RECEIVED")
    void shouldReturnValueForReceived() {
        assertThat(EventStatus.RECEIVED.getValue()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("Should return correct value for VALIDATING")
    void shouldReturnValueForValidating() {
        assertThat(EventStatus.VALIDATING.getValue()).isEqualTo("VALIDATING");
    }

    @Test
    @DisplayName("Should return correct value for VALIDATED")
    void shouldReturnValueForValidated() {
        assertThat(EventStatus.VALIDATED.getValue()).isEqualTo("VALIDATED");
    }

    @Test
    @DisplayName("Should return correct value for FORWARDED")
    void shouldReturnValueForForwarded() {
        assertThat(EventStatus.FORWARDED.getValue()).isEqualTo("FORWARDED");
    }

    @Test
    @DisplayName("Should return correct value for INVALID")
    void shouldReturnValueForInvalid() {
        assertThat(EventStatus.INVALID.getValue()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("Should return correct value for FAILED")
    void shouldReturnValueForFailed() {
        assertThat(EventStatus.FAILED.getValue()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Should convert value to EventStatus")
    void shouldConvertValueToEventStatus() {
        assertThat(EventStatus.fromValue("RECEIVED")).isEqualTo(EventStatus.RECEIVED);
        assertThat(EventStatus.fromValue("VALIDATING")).isEqualTo(EventStatus.VALIDATING);
        assertThat(EventStatus.fromValue("VALIDATED")).isEqualTo(EventStatus.VALIDATED);
        assertThat(EventStatus.fromValue("FORWARDED")).isEqualTo(EventStatus.FORWARDED);
        assertThat(EventStatus.fromValue("INVALID")).isEqualTo(EventStatus.INVALID);
        assertThat(EventStatus.fromValue("FAILED")).isEqualTo(EventStatus.FAILED);
    }

    @Test
    @DisplayName("Should return null for null value")
    void shouldReturnNullForNullValue() {
        assertThat(EventStatus.fromValue(null)).isNull();
    }

    @Test
    @DisplayName("Should return null for unknown value")
    void shouldReturnNullForUnknownValue() {
        assertThat(EventStatus.fromValue("UNKNOWN_STATUS")).isNull();
    }
}
