package com.wpanther.document.intake.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentIntakeMetrics
 * Tests metric recording for document intake operations
 */
@DisplayName("DocumentIntakeMetrics Tests")
class DocumentIntakeMetricsTest {

    private MeterRegistry meterRegistry;
    private DocumentIntakeMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = Mockito.mock(MeterRegistry.class);
        // Use real Counter and Timer for testing
        // Create a simple in-memory registry for actual metrics
        io.micrometer.core.instrument.simple.SimpleMeterRegistry simpleRegistry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        metrics = new DocumentIntakeMetrics(simpleRegistry);
    }

    @Test
    @DisplayName("incrementReceived increments the received counter")
    void testIncrementReceived() {
        Counter before = metrics.getDocumentsReceived();
        double countBefore = before.count();

        metrics.incrementReceived();

        assertThat(metrics.getDocumentsReceived().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("incrementValidated increments validated counter")
    void testIncrementValidated() {
        Counter before = metrics.getDocumentsValidated();
        double countBefore = before.count();

        metrics.incrementValidated("TAX_INVOICE");

        assertThat(metrics.getDocumentsValidated().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("incrementValidated records document type")
    void testIncrementValidatedRecordsType() {
        // Should not throw - verifies the method handles document type parameter
        metrics.incrementValidated("TAX_INVOICE");
        metrics.incrementValidated("RECEIPT");
        metrics.incrementValidated("INVOICE");
    }

    @Test
    @DisplayName("incrementInvalid increments invalid counter")
    void testIncrementInvalid() {
        Counter before = metrics.getDocumentsInvalid();
        double countBefore = before.count();

        metrics.incrementInvalid("SCHEMA_VALIDATION");

        assertThat(metrics.getDocumentsInvalid().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("incrementInvalid records reason")
    void testIncrementInvalidRecordsReason() {
        // Should not throw - verifies the method handles reason parameter
        metrics.incrementInvalid("SCHEMA_VALIDATION");
        metrics.incrementInvalid("SCHEMATRON_RULES");
    }

    @Test
    @DisplayName("incrementForwarded increments forwarded counter")
    void testIncrementForwarded() {
        Counter before = metrics.getDocumentsForwarded();
        double countBefore = before.count();

        metrics.incrementForwarded("TAX_INVOICE");

        assertThat(metrics.getDocumentsForwarded().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("incrementForwarded records document type")
    void testIncrementForwardedRecordsType() {
        // Should not throw - verifies the method handles document type parameter
        metrics.incrementForwarded("TAX_INVOICE");
        metrics.incrementForwarded("RECEIPT");
    }

    @Test
    @DisplayName("incrementFailed increments failed counter")
    void testIncrementFailed() {
        Counter before = metrics.getDocumentsFailed();
        double countBefore = before.count();

        metrics.incrementFailed("VALIDATION");

        assertThat(metrics.getDocumentsFailed().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("incrementFailed records stage")
    void testIncrementFailedRecordsStage() {
        // Should not throw - verifies the method handles stage parameter
        metrics.incrementFailed("VALIDATION");
        metrics.incrementFailed("PARSING");
    }

    @Test
    @DisplayName("recordProcessingTime records time")
    void testRecordProcessingTime() {
        Timer before = metrics.getProcessingTimer();
        long countBefore = before.count();

        metrics.recordProcessingTime(100L);

        assertThat(metrics.getProcessingTimer().count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("recordProcessingTime with various durations")
    void testRecordProcessingTimeVariousDurations() {
        // Test various processing durations
        metrics.recordProcessingTime(10L);
        metrics.recordProcessingTime(50L);
        metrics.recordProcessingTime(100L);
        metrics.recordProcessingTime(500L);
        metrics.recordProcessingTime(1000L);

        assertThat(metrics.getProcessingTimer().count()).isEqualTo(5);
    }

    @Test
    @DisplayName("getDocumentsReceived returns counter")
    void testGetDocumentsReceived() {
        Counter counter = metrics.getDocumentsReceived();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("document.intake.received");
    }

    @Test
    @DisplayName("getDocumentsValidated returns counter")
    void testGetDocumentsValidated() {
        Counter counter = metrics.getDocumentsValidated();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("document.intake.validated");
    }

    @Test
    @DisplayName("getDocumentsInvalid returns counter")
    void testGetDocumentsInvalid() {
        Counter counter = metrics.getDocumentsInvalid();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("document.intake.invalid");
    }

    @Test
    @DisplayName("getDocumentsForwarded returns counter")
    void testGetDocumentsForwarded() {
        Counter counter = metrics.getDocumentsForwarded();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("document.intake.forwarded");
    }

    @Test
    @DisplayName("getDocumentsFailed returns counter")
    void testGetDocumentsFailed() {
        Counter counter = metrics.getDocumentsFailed();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo("document.intake.failed");
    }

    @Test
    @DisplayName("getProcessingTimer returns timer")
    void testGetProcessingTimer() {
        Timer timer = metrics.getProcessingTimer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("document.intake.processing.time");
    }

    @Test
    @DisplayName("multiple operations accumulate correctly")
    void testMultipleOperationsAccumulate() {
        // Record multiple operations
        metrics.incrementReceived();
        metrics.incrementReceived();
        metrics.incrementReceived();

        metrics.incrementValidated("TAX_INVOICE");
        metrics.incrementValidated("TAX_INVOICE");

        metrics.incrementInvalid("SCHEMA");

        metrics.incrementForwarded("TAX_INVOICE");

        metrics.incrementFailed("PARSING");

        metrics.recordProcessingTime(100L);
        metrics.recordProcessingTime(200L);

        // Verify counts
        assertThat(metrics.getDocumentsReceived().count()).isEqualTo(3);
        assertThat(metrics.getDocumentsValidated().count()).isEqualTo(2);
        assertThat(metrics.getDocumentsInvalid().count()).isEqualTo(1);
        assertThat(metrics.getDocumentsForwarded().count()).isEqualTo(1);
        assertThat(metrics.getDocumentsFailed().count()).isEqualTo(1);
        assertThat(metrics.getProcessingTimer().count()).isEqualTo(2);
    }
}