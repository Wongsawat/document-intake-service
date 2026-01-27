package com.wpanther.document.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application main class test
 * Verifies that the main application class is properly configured
 */
@DisplayName("Document Intake Service Application Tests")
class DocumentIntakeServiceApplicationTest {

    @Test
    @DisplayName("Main class exists and has main method")
    void testMainClassExistsAndHasMainMethod() throws Exception {
        // Verify the main application class exists
        Class<?> mainClass = Class.forName("com.wpanther.document.intake.DocumentIntakeServiceApplication");
        assertThat(mainClass).isNotNull();

        // Verify it has a main method
        assertThat(mainClass.getDeclaredMethod("main", String[].class)).isNotNull();
    }

    @Test
    @DisplayName("Main class is annotated with SpringBootApplication")
    void testMainClassHasSpringBootApplicationAnnotation() throws Exception {
        Class<?> mainClass = Class.forName("com.wpanther.document.intake.DocumentIntakeServiceApplication");

        boolean hasSpringBootAnnotation = mainClass.isAnnotationPresent(
            org.springframework.boot.autoconfigure.SpringBootApplication.class
        );

        assertThat(hasSpringBootAnnotation).isTrue();
    }
}
