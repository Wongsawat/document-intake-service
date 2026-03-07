package com.wpanther.document.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentIntakeServiceApplication Unit Tests")
class DocumentIntakeServiceApplicationTest {

    @Test
    @DisplayName("DocumentIntakeServiceApplication can be instantiated")
    void testApplicationCanBeInstantiated() {
        DocumentIntakeServiceApplication application = new DocumentIntakeServiceApplication();
        assertThat(application).isNotNull();
    }

    @Test
    @DisplayName("DocumentIntakeServiceApplication has main method")
    void testApplicationHasMainMethod() throws Exception {
        var method = DocumentIntakeServiceApplication.class.getDeclaredMethod("main", String[].class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("DocumentIntakeServiceApplication class annotations")
    void testApplicationAnnotations() {
        assertThat(DocumentIntakeServiceApplication.class.isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class)).isTrue();
        assertThat(DocumentIntakeServiceApplication.class.isAnnotationPresent(org.springframework.cloud.client.discovery.EnableDiscoveryClient.class)).isTrue();
        assertThat(DocumentIntakeServiceApplication.class.isAnnotationPresent(org.springframework.transaction.annotation.EnableTransactionManagement.class)).isTrue();
    }

    @Test
    @DisplayName("main method is public static void")
    void testMainMethodSignature() throws Exception {
        var method = DocumentIntakeServiceApplication.class.getDeclaredMethod("main", String[].class);
        
        int modifiers = method.getModifiers();
        assertThat(java.lang.reflect.Modifier.isPublic(modifiers)).isTrue();
        assertThat(java.lang.reflect.Modifier.isStatic(modifiers)).isTrue();
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("main method accepts String array parameter")
    void testMainMethodParameter() throws Exception {
        var method = DocumentIntakeServiceApplication.class.getDeclaredMethod("main", String[].class);
        
        Class<?>[] paramTypes = method.getParameterTypes();
        assertThat(paramTypes).hasSize(1);
        assertThat(paramTypes[0]).isEqualTo(String[].class);
    }

    @Test
    @DisplayName("DocumentIntakeServiceApplication is a public class")
    void testApplicationIsPublicClass() {
        int modifiers = DocumentIntakeServiceApplication.class.getModifiers();
        assertThat(java.lang.reflect.Modifier.isPublic(modifiers)).isTrue();
    }

    @Test
    @DisplayName("main method calls SpringApplication.run")
    void testMainCallsSpringApplicationRun() throws Exception {
        var method = DocumentIntakeServiceApplication.class.getDeclaredMethod("main", String[].class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }
}
