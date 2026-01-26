package com.invoice.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application main class test
 * Verifies that the main application class is properly configured
 */
@DisplayName("Invoice Intake Service Application Tests")
class InvoiceIntakeServiceApplicationTest {

    @Test
    @DisplayName("Main class exists and has main method")
    void testMainClassExistsAndHasMainMethod() throws Exception {
        // Verify the main application class exists
        Class<?> mainClass = Class.forName("com.invoice.intake.InvoiceIntakeServiceApplication");
        assertThat(mainClass).isNotNull();

        // Verify it has a main method
        assertThat(mainClass.getDeclaredMethod("main", String[].class)).isNotNull();
    }

    @Test
    @DisplayName("Main class is annotated with SpringBootApplication")
    void testMainClassHasSpringBootApplicationAnnotation() throws Exception {
        Class<?> mainClass = Class.forName("com.invoice.intake.InvoiceIntakeServiceApplication");

        boolean hasSpringBootAnnotation = mainClass.isAnnotationPresent(
            org.springframework.boot.autoconfigure.SpringBootApplication.class
        );

        assertThat(hasSpringBootAnnotation).isTrue();
    }
}
