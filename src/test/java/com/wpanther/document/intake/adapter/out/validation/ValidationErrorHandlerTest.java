package com.wpanther.document.intake.adapter.out.validation;

import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ValidationErrorHandler.
 * Tests error/warning collection and message formatting with location information.
 */
@DisplayName("ValidationErrorHandler Tests")
class ValidationErrorHandlerTest {

    private ValidationErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ValidationErrorHandler();
    }

    @Test
    @DisplayName("Handle ERROR event - adds to errors list")
    void handleEvent_ErrorSeverity_AddsToErrors() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.ERROR, "Test error", 10, 5, null);

        // When
        boolean result = handler.handleEvent(event);

        // Then
        assertThat(result).isTrue(); // Continue validation
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).contains("Line 10");
        assertThat(handler.getErrors().get(0)).contains("Column 5");
        assertThat(handler.getErrors().get(0)).contains("Test error");
        assertThat(handler.getWarnings()).isEmpty();
        assertThat(handler.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Handle FATAL_ERROR event - adds to errors list")
    void handleEvent_FatalErrorSeverity_AddsToErrors() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.FATAL_ERROR, "Fatal error", 15, 20, null);

        // When
        boolean result = handler.handleEvent(event);

        // Then
        assertThat(result).isTrue();
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).contains("Fatal error");
        assertThat(handler.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Handle WARNING event - adds to warnings list")
    void handleEvent_WarningSeverity_AddsToWarnings() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.WARNING, "Test warning", 5, 10, null);

        // When
        boolean result = handler.handleEvent(event);

        // Then
        assertThat(result).isTrue();
        assertThat(handler.getWarnings()).hasSize(1);
        assertThat(handler.getWarnings().get(0)).contains("Test warning");
        assertThat(handler.getErrors()).isEmpty();
        assertThat(handler.hasWarnings()).isTrue();
    }

    @Test
    @DisplayName("Handle event without location info - formats message correctly")
    void handleEvent_NoLocation_FormatsWithoutLocation() {
        // Given
        ValidationEvent event = createEventNoLocation(ValidationEvent.ERROR, "Error without location");

        // When
        handler.handleEvent(event);

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).isEqualTo("Error without location");
        assertThat(handler.getErrors().get(0)).doesNotContain("Line");
    }

    @Test
    @DisplayName("Handle event with linked exception - includes linked message")
    void handleEvent_WithLinkedException_IncludesLinkedMessage() {
        // Given
        Exception linkedException = new RuntimeException("Underlying cause");
        ValidationEvent event = createEvent(ValidationEvent.ERROR, "Main error", 10, 5, linkedException);

        // When
        handler.handleEvent(event);

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).contains("Main error");
        assertThat(handler.getErrors().get(0)).contains("(Underlying cause)");
    }

    @Test
    @DisplayName("Handle event with null linked exception - no NPE")
    void handleEvent_NullLinkedException_HandlesGracefully() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.ERROR, "Error message", 10, 5, null);

        // When
        handler.handleEvent(event);

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).contains("Error message");
        assertThat(handler.getErrors().get(0)).doesNotContain("(");
    }

    @Test
    @DisplayName("Handle event with linked exception having same message - no duplicate")
    void handleEvent_LinkedExceptionSameMessage_NoDuplicate() {
        // Given
        Exception linkedException = new RuntimeException("Duplicate message");
        ValidationEvent event = mock(ValidationEvent.class);
        ValidationEventLocator locator = createLocator(10, 5);

        when(event.getMessage()).thenReturn("Duplicate message");
        when(event.getSeverity()).thenReturn(ValidationEvent.ERROR);
        when(event.getLocator()).thenReturn(locator);
        when(event.getLinkedException()).thenReturn(linkedException);

        // When
        handler.handleEvent(event);

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        // Should not have duplicate message in parentheses
        assertThat(handler.getErrors().get(0)).doesNotContain("(Duplicate message)");
        assertThat(handler.getErrors().get(0)).contains("Duplicate message");
    }

    @Test
    @DisplayName("Handle event with line number but no column - formats correctly")
    void handleEvent_LineOnlyNoColumn_FormatsWithLineOnly() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.ERROR, "Error with line only", 20, -1, null);

        // When
        handler.handleEvent(event);

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors().get(0)).contains("Line 20:");
        assertThat(handler.getErrors().get(0)).doesNotContain("Column");
    }

    @Test
    @DisplayName("Handle multiple events with mixed severity - categorizes correctly")
    void handleEvent_MixedSeverity_CategorizesCorrectly() {
        // Given
        ValidationEvent error1 = createEvent(ValidationEvent.ERROR, "Error 1", 1, 1, null);
        ValidationEvent warning1 = createEvent(ValidationEvent.WARNING, "Warning 1", 2, 1, null);
        ValidationEvent error2 = createEvent(ValidationEvent.FATAL_ERROR, "Error 2", 3, 1, null);
        ValidationEvent warning2 = createEvent(ValidationEvent.WARNING, "Warning 2", 4, 1, null);

        // When
        handler.handleEvent(error1);
        handler.handleEvent(warning1);
        handler.handleEvent(error2);
        handler.handleEvent(warning2);

        // Then
        assertThat(handler.getErrors()).hasSize(2);
        assertThat(handler.getWarnings()).hasSize(2);
        assertThat(handler.getTotalIssueCount()).isEqualTo(4);
        assertThat(handler.hasErrors()).isTrue();
        assertThat(handler.hasWarnings()).isTrue();
    }

    @Test
    @DisplayName("Get errors returns defensive copy - modifications don't affect internal list")
    void getErrors_ReturnsDefensiveCopy() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.ERROR, "Test", 1, 1, null);
        handler.handleEvent(event);

        // When
        var errors = handler.getErrors();
        errors.add("Modified");

        // Then
        assertThat(handler.getErrors()).hasSize(1);
        assertThat(handler.getErrors()).doesNotContain("Modified");
    }

    @Test
    @DisplayName("Get warnings returns defensive copy")
    void getWarnings_ReturnsDefensiveCopy() {
        // Given
        ValidationEvent event = createEvent(ValidationEvent.WARNING, "Test", 1, 1, null);
        handler.handleEvent(event);

        // When
        var warnings = handler.getWarnings();
        warnings.add("Modified");

        // Then
        assertThat(handler.getWarnings()).hasSize(1);
        assertThat(handler.getWarnings()).doesNotContain("Modified");
    }

    @Test
    @DisplayName("New handler has no errors or warnings")
    void newHandler_HasNoIssues() {
        assertThat(handler.hasErrors()).isFalse();
        assertThat(handler.hasWarnings()).isFalse();
        assertThat(handler.getTotalIssueCount()).isZero();
        assertThat(handler.getErrors()).isEmpty();
        assertThat(handler.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Handle event always returns true to continue validation")
    void handleEvent_AlwaysReturnsTrue() {
        // Given
        ValidationEvent errorEvent = createEvent(ValidationEvent.ERROR, "Error", 1, 1, null);
        ValidationEvent warningEvent = createEvent(ValidationEvent.WARNING, "Warning", 1, 1, null);

        // When & Then
        assertThat(handler.handleEvent(errorEvent)).isTrue();
        assertThat(handler.handleEvent(warningEvent)).isTrue();
    }

    // Helper methods

    private ValidationEvent createEvent(int severity, String message, int line, int column, Exception linkedException) {
        ValidationEvent event = mock(ValidationEvent.class);
        ValidationEventLocator locator = createLocator(line, column);

        when(event.getMessage()).thenReturn(message);
        when(event.getSeverity()).thenReturn(severity);
        when(event.getLocator()).thenReturn(locator);
        when(event.getLinkedException()).thenReturn(linkedException);

        return event;
    }

    private ValidationEvent createEventNoLocation(int severity, String message) {
        ValidationEvent event = mock(ValidationEvent.class);
        ValidationEventLocator locator = mock(ValidationEventLocator.class);

        when(event.getMessage()).thenReturn(message);
        when(event.getSeverity()).thenReturn(severity);
        when(event.getLocator()).thenReturn(locator);
        when(event.getLinkedException()).thenReturn(null);
        when(locator.getLineNumber()).thenReturn(-1);
        when(locator.getColumnNumber()).thenReturn(-1);

        return event;
    }

    private ValidationEventLocator createLocator(int line, int column) {
        ValidationEventLocator locator = mock(ValidationEventLocator.class);
        when(locator.getLineNumber()).thenReturn(line);
        when(locator.getColumnNumber()).thenReturn(column);
        return locator;
    }
}
