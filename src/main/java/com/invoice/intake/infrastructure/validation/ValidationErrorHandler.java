package com.invoice.intake.infrastructure.validation;

import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;
import jakarta.xml.bind.ValidationEventLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom JAXB ValidationEventHandler that collects all validation errors and warnings
 * instead of failing fast. Provides detailed error messages with line and column numbers.
 */
public class ValidationErrorHandler implements ValidationEventHandler {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * Creates a new ValidationErrorHandler.
     */
    public ValidationErrorHandler() {
        // Default constructor
    }

    @Override
    public boolean handleEvent(ValidationEvent event) {
        String message = formatWithLocation(event);

        if (event.getSeverity() == ValidationEvent.ERROR ||
            event.getSeverity() == ValidationEvent.FATAL_ERROR) {
            errors.add(message);
        } else if (event.getSeverity() == ValidationEvent.WARNING) {
            warnings.add(message);
        }

        // Return true to continue validation (collect all errors)
        return true;
    }

    /**
     * Format validation event with location information (line/column numbers).
     *
     * @param event the validation event
     * @return formatted error message with location
     */
    private String formatWithLocation(ValidationEvent event) {
        ValidationEventLocator locator = event.getLocator();
        StringBuilder sb = new StringBuilder();

        // Add location information if available
        if (locator != null && locator.getLineNumber() != -1) {
            sb.append("Line ").append(locator.getLineNumber());

            if (locator.getColumnNumber() != -1) {
                sb.append(", Column ").append(locator.getColumnNumber());
            }

            sb.append(": ");
        }

        // Add the actual error message
        sb.append(event.getMessage());

        // Add linked exception message if available
        if (event.getLinkedException() != null) {
            String linkedMsg = event.getLinkedException().getMessage();
            if (linkedMsg != null && !linkedMsg.equals(event.getMessage())) {
                sb.append(" (").append(linkedMsg).append(")");
            }
        }

        return sb.toString();
    }

    /**
     * Get all collected errors.
     *
     * @return list of error messages
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Get all collected warnings.
     *
     * @return list of warning messages
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Check if any errors were collected.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Check if any warnings were collected.
     *
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Get total count of errors and warnings.
     *
     * @return total validation issue count
     */
    public int getTotalIssueCount() {
        return errors.size() + warnings.size();
    }
}
