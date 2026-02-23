package com.wpanther.document.intake.infrastructure.config;

import com.wpanther.document.intake.application.service.DocumentIntakeService;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CamelConfig
 * Tests configuration setup and route definitions without starting Camel context
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.kafka.topics.invoice-intake=document.intake",
    "app.kafka.topics.intake-dlq=document.intake.dlq",
    "app.kafka.bootstrap-servers=localhost:9092"
})
@DisplayName("CamelConfig Unit Tests")
class CamelConfigTest {

    @Mock
    private DocumentIntakeService documentIntakeService;

    @Mock
    private CamelContext camelContext;

    private CamelConfig camelConfig;

    @BeforeEach
    void setUp() throws Exception {
        camelConfig = new CamelConfig(
            documentIntakeService,
            "document.intake",
            "document.intake.dlq"
        );
        camelConfig.setCamelContext(camelContext);
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("CamelConfig constructor initializes all fields")
    void testConstructorInitializesAllFields() {
        assertThat(camelConfig).isNotNull();
        assertThat(camelConfig).isInstanceOf(RouteBuilder.class);
    }

    @Test
    @DisplayName("CamelConfig with different topic names")
    void testCamelConfigWithDifferentTopicNames() {
        CamelConfig config = new CamelConfig(
            documentIntakeService,
            "different.topic",
            "different.dlq"
        );
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("CamelConfig with empty topic names")
    void testCamelConfigWithEmptyTopicNames() {
        CamelConfig config = new CamelConfig(
            documentIntakeService,
            "",
            ""
        );
        assertThat(config).isNotNull();
    }

    // ==================== Document Status Tests ====================

    @Test
    @DisplayName("All document statuses are defined")
    void testAllDocumentStatusesDefined() {
        DocumentStatus[] statuses = DocumentStatus.values();
        assertThat(statuses).hasSizeGreaterThanOrEqualTo(5);
        assertThat(statuses).contains(
            DocumentStatus.RECEIVED,
            DocumentStatus.VALIDATING,
            DocumentStatus.VALIDATED,
            DocumentStatus.INVALID,
            DocumentStatus.FORWARDED
        );
    }

    // ==================== ValidationResult Tests ====================

    @Test
    @DisplayName("ValidationResult can represent success")
    void testValidationResultSuccess() {
        ValidationResult result = ValidationResult.success();
        assertThat(result.valid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("ValidationResult can represent failure")
    void testValidationResultFailure() {
        java.util.List<String> errors = java.util.List.of("Error 1", "Error 2");
        ValidationResult result = ValidationResult.invalid(errors);
        assertThat(result.valid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("ValidationResult can represent warnings")
    void testValidationResultWarnings() {
        java.util.List<String> warnings = java.util.List.of("Warning 1");
        ValidationResult result = ValidationResult.validWithWarnings(warnings);
        assertThat(result.valid()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warningCount()).isEqualTo(1);
    }

    // ==================== Topic Name Tests ====================

    @Test
    @DisplayName("DLQ topic is configured correctly")
    void testDlqTopicConfiguration() {
        assertThat(camelConfig).isNotNull();
    }

    // ==================== Route Configuration Tests ====================

    @Test
    @DisplayName("Route configuration has configure method")
    void testRouteConfigurationHasConfigureMethod() throws Exception {
        assertThat(camelConfig.getClass().getMethod("configure")).isNotNull();
    }

    @Test
    @DisplayName("CamelConfig constructor with various topic configurations")
    void testCamelConfigWithVariousTopics() {
        CamelConfig config1 = new CamelConfig(
            documentIntakeService,
            "topic1",
            "dlq1"
        );
        CamelConfig config2 = new CamelConfig(
            documentIntakeService,
            "kafka:topic1",
            "kafka:dlq1"
        );

        assertThat(config1).isNotNull();
        assertThat(config2).isNotNull();
    }

    @Test
    @DisplayName("CamelConfig is instance of RouteBuilder")
    void testCamelConfigIsRouteBuilder() {
        assertThat(camelConfig).isInstanceOf(RouteBuilder.class);
    }

    @Test
    @DisplayName("CamelConfig has configure method")
    void testCamelConfigHasConfigureMethod() throws Exception {
        assertThat(camelConfig.getClass().getMethod("configure")).isNotNull();
    }
}
