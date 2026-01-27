package com.wpanther.document.intake.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentStatus enum
 */
class DocumentStatusTest {

    @Test
    void testAllStatusValuesExist() {
        // Verify all expected status values exist
        assertThat(DocumentStatus.values()).hasSize(6);
        assertThat(DocumentStatus.values()).containsExactlyInAnyOrder(
            DocumentStatus.RECEIVED,
            DocumentStatus.VALIDATING,
            DocumentStatus.VALIDATED,
            DocumentStatus.INVALID,
            DocumentStatus.FORWARDED,
            DocumentStatus.FAILED
        );
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void testStatusValueIsNotNull(DocumentStatus status) {
        assertThat(status).isNotNull();
    }

    @Test
    void testStatusNamesAreCorrect() {
        assertThat(DocumentStatus.RECEIVED.name()).isEqualTo("RECEIVED");
        assertThat(DocumentStatus.VALIDATING.name()).isEqualTo("VALIDATING");
        assertThat(DocumentStatus.VALIDATED.name()).isEqualTo("VALIDATED");
        assertThat(DocumentStatus.INVALID.name()).isEqualTo("INVALID");
        assertThat(DocumentStatus.FORWARDED.name()).isEqualTo("FORWARDED");
        assertThat(DocumentStatus.FAILED.name()).isEqualTo("FAILED");
    }

    @Test
    void testStatusOrdinalOrder() {
        // Verify status follows expected state machine order
        assertThat(DocumentStatus.RECEIVED.ordinal()).isLessThan(DocumentStatus.VALIDATING.ordinal());
        assertThat(DocumentStatus.VALIDATING.ordinal()).isLessThan(DocumentStatus.VALIDATED.ordinal());
        assertThat(DocumentStatus.VALIDATED.ordinal()).isLessThan(DocumentStatus.FORWARDED.ordinal());
    }
}
