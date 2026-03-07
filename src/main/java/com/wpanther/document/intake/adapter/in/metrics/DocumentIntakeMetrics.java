package com.wpanther.document.intake.adapter.in.metrics;

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
public class DocumentIntakeMetrics {

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

    /**
     * Increment the documents received counter.
     */
    public void incrementReceived() {
        documentsReceived.increment();
    }

    /**
     * Increment the documents validated counter.
     *
     * @param documentType The type of document (TAX_INVOICE, RECEIPT, etc.)
     */
    public void incrementValidated(String documentType) {
        Counter.builder("document.intake.validated.by.type")
                .description("Number of documents that passed validation, by type")
                .tag("service", "document-intake")
                .tag("type", documentType)
                .register(meterRegistry)
                .increment();
        documentsValidated.increment();
    }

    /**
     * Increment the documents invalid counter.
     *
     * @param reason The validation failure reason
     */
    public void incrementInvalid(String reason) {
        Counter.builder("document.intake.invalid.by.reason")
                .description("Number of documents that failed validation, by reason")
                .tag("service", "document-intake")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
        documentsInvalid.increment();
    }

    /**
     * Increment the documents forwarded counter.
     *
     * @param documentType The type of document being forwarded
     */
    public void incrementForwarded(String documentType) {
        Counter.builder("document.intake.forwarded.by.type")
                .description("Number of documents forwarded to saga orchestrator, by type")
                .tag("service", "document-intake")
                .tag("type", documentType)
                .register(meterRegistry)
                .increment();
        documentsForwarded.increment();
    }

    /**
     * Increment the documents failed counter.
     *
     * @param stage The processing stage where failure occurred
     */
    public void incrementFailed(String stage) {
        Counter.builder("document.intake.failed.by.stage")
                .description("Number of documents that failed during processing, by stage")
                .tag("service", "document-intake")
                .tag("stage", stage)
                .register(meterRegistry)
                .increment();
        documentsFailed.increment();
    }

    /**
     * Record the processing time for a document.
     *
     * @param duration The duration in milliseconds
     */
    public void recordProcessingTime(long duration) {
        processingTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    /**
     * Get the documents received counter.
     *
     * @return The counter
     */
    public Counter getDocumentsReceived() {
        return documentsReceived;
    }

    /**
     * Get the documents validated counter.
     *
     * @return The counter
     */
    public Counter getDocumentsValidated() {
        return documentsValidated;
    }

    /**
     * Get the documents invalid counter.
     *
     * @return The counter
     */
    public Counter getDocumentsInvalid() {
        return documentsInvalid;
    }

    /**
     * Get the documents forwarded counter.
     *
     * @return The counter
     */
    public Counter getDocumentsForwarded() {
        return documentsForwarded;
    }

    /**
     * Get the documents failed counter.
     *
     * @return The counter
     */
    public Counter getDocumentsFailed() {
        return documentsFailed;
    }

    /**
     * Get the processing timer.
     *
     * @return The timer
     */
    public Timer getProcessingTimer() {
        return processingTimer;
    }
}
