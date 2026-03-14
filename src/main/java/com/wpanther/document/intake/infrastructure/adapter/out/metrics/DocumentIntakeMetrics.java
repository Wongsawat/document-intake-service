package com.wpanther.document.intake.infrastructure.adapter.out.metrics;

import com.wpanther.document.intake.application.port.out.DocumentIntakeMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer metrics for document intake operations.
 * <p>
 * Tracks document submission rates, validation results, and processing times.
 * Metrics are automatically exposed via Actuator endpoint: /actuator/metrics
 * and Prometheus endpoint: /actuator/prometheus
 */
@Component
public class DocumentIntakeMetrics implements DocumentIntakeMetricsPort {

    private final Counter documentsReceived;
    private final Counter documentsValidated;
    private final Counter documentsInvalid;
    private final Counter documentsForwarded;
    private final Counter documentsFailed;
    private final Timer processingTimer;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a new DocumentIntakeMetrics with registered meters.
     *
     * @param meterRegistry The Micrometer meter registry
     */
    public DocumentIntakeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.documentsReceived = Counter.builder("document.intake.received")
                .description("Total number of documents received")
                .tag("service", "document-intake")
                .register(meterRegistry);

        this.documentsValidated = Counter.builder("document.intake.validated")
                .description("Number of documents that passed validation")
                .tag("service", "document-intake")
                .register(meterRegistry);

        this.documentsInvalid = Counter.builder("document.intake.invalid")
                .description("Number of documents that failed validation")
                .tag("service", "document-intake")
                .register(meterRegistry);

        this.documentsForwarded = Counter.builder("document.intake.forwarded")
                .description("Number of documents forwarded to saga orchestrator")
                .tag("service", "document-intake")
                .register(meterRegistry);

        this.documentsFailed = Counter.builder("document.intake.failed")
                .description("Number of documents that failed during processing")
                .tag("service", "document-intake")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("document.intake.processing.time")
                .description("Time taken to process documents")
                .tag("service", "document-intake")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public void incrementReceived() {
        documentsReceived.increment();
    }

    /**
     * Increments both the base validated counter and a document type-specific counter.
     * <p>
     * The type-specific counter uses dynamic tagging for observability by document type.
     * This dual-counter approach provides both:
     * <ul>
     *   <li>A simple total count via {@code documentsValidated}</li>
     *   <li>Granular counts per document type (TAX_INVOICE, RECEIPT, etc.)</li>
     * </ul>
     * The dynamic counter is looked up via {@code meterRegistry.counter()} for efficiency
     * rather than rebuilding on each call.
     *
     * @param documentType The type of document (TAX_INVOICE, RECEIPT, etc.)
     */
    @Override
    public void incrementValidated(String documentType) {
        meterRegistry.counter("document.intake.validated.by.type", "service", "document-intake", "type", documentType).increment();
        documentsValidated.increment();
    }

    /**
     * Increments both the base invalid counter and a reason-specific counter.
     * <p>
     * The reason-specific counter uses dynamic tagging for observability by failure reason.
     * This dual-counter approach provides both:
     * <ul>
     *   <li>A simple total count via {@code documentsInvalid}</li>
     *   <li>Granular counts per validation failure reason</li>
     * </ul>
     *
     * @param reason The validation failure reason
     */
    @Override
    public void incrementInvalid(String reason) {
        meterRegistry.counter("document.intake.invalid.by.reason", "service", "document-intake", "reason", reason).increment();
        documentsInvalid.increment();
    }

    /**
     * Increments both the base forwarded counter and a document type-specific counter.
     * <p>
     * The type-specific counter uses dynamic tagging for observability by document type.
     * This dual-counter approach provides both:
     * <ul>
     *   <li>A simple total count via {@code documentsForwarded}</li>
     *   <li>Granular counts per document type forwarded to the orchestrator</li>
     * </ul>
     *
     * @param documentType The type of document being forwarded
     */
    @Override
    public void incrementForwarded(String documentType) {
        meterRegistry.counter("document.intake.forwarded.by.type", "service", "document-intake", "type", documentType).increment();
        documentsForwarded.increment();
    }

    /**
     * Increments both the base failed counter and a stage-specific counter.
     * <p>
     * The stage-specific counter uses dynamic tagging for observability by processing stage.
     * This dual-counter approach provides both:
     * <ul>
     *   <li>A simple total count via {@code documentsFailed}</li>
     *   <li>Granular counts per failure stage</li>
     * </ul>
     *
     * @param stage The processing stage where failure occurred
     */
    @Override
    public void incrementFailed(String stage) {
        meterRegistry.counter("document.intake.failed.by.stage", "service", "document-intake", "stage", stage).increment();
        documentsFailed.increment();
    }

    @Override
    public void recordProcessingTime(long durationMs) {
        processingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Get the documents received counter (for testing).
     */
    public Counter getDocumentsReceived() {
        return documentsReceived;
    }

    /**
     * Get the documents validated counter (for testing).
     */
    public Counter getDocumentsValidated() {
        return documentsValidated;
    }

    /**
     * Get the documents invalid counter (for testing).
     */
    public Counter getDocumentsInvalid() {
        return documentsInvalid;
    }

    /**
     * Get the documents forwarded counter (for testing).
     */
    public Counter getDocumentsForwarded() {
        return documentsForwarded;
    }

    /**
     * Get the documents failed counter (for testing).
     */
    public Counter getDocumentsFailed() {
        return documentsFailed;
    }

    /**
     * Get the processing timer (for testing).
     */
    public Timer getProcessingTimer() {
        return processingTimer;
    }
}
