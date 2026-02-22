package com.wpanther.document.intake.domain.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ValidationResultSerializationException Tests")
class ValidationResultSerializationExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateExceptionWithMessage() {
        String message = "Serialization failed";
        ValidationResultSerializationException exception = new ValidationResultSerializationException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        String message = "Serialization failed";
        Throwable cause = new RuntimeException("Root cause");
        ValidationResultSerializationException exception = new ValidationResultSerializationException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should create exception with cause")
    void shouldCreateExceptionWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        ValidationResultSerializationException exception = new ValidationResultSerializationException(cause);

        assertThat(exception.getMessage()).isNotNull();
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
