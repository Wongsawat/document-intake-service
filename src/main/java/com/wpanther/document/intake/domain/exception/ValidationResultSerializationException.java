package com.wpanther.document.intake.domain.exception;

/**
 * Exception thrown when ValidationResult serialization or deserialization fails.
 */
public class ValidationResultSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidationResultSerializationException(String message) {
        super(message);
    }

    public ValidationResultSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidationResultSerializationException(Throwable cause) {
        super(cause);
    }
}
