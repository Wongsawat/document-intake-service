package com.wpanther.document.intake.application.port.out;

/**
 * Outbound port for recording document intake metrics.
 * <p>
 * Abstracts the metrics infrastructure (Micrometer) from the application layer,
 * keeping the application core free of monitoring framework dependencies.
 */
public interface DocumentIntakeMetricsPort {
    void incrementReceived();
    void incrementValidated(String documentType);
    void incrementInvalid(String reason);
    void incrementForwarded(String documentType);
    void incrementFailed(String stage);
    void recordProcessingTime(long durationMs);
}
