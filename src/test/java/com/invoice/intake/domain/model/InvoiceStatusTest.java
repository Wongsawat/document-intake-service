package com.invoice.intake.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InvoiceStatus enum
 */
class InvoiceStatusTest {

    @Test
    void testAllStatusValuesExist() {
        // Verify all expected status values exist
        assertThat(InvoiceStatus.values()).hasSize(6);
        assertThat(InvoiceStatus.values()).containsExactlyInAnyOrder(
            InvoiceStatus.RECEIVED,
            InvoiceStatus.VALIDATING,
            InvoiceStatus.VALIDATED,
            InvoiceStatus.INVALID,
            InvoiceStatus.FORWARDED,
            InvoiceStatus.FAILED
        );
    }

    @ParameterizedTest
    @EnumSource(InvoiceStatus.class)
    void testStatusValueIsNotNull(InvoiceStatus status) {
        assertThat(status).isNotNull();
    }

    @Test
    void testStatusNamesAreCorrect() {
        assertThat(InvoiceStatus.RECEIVED.name()).isEqualTo("RECEIVED");
        assertThat(InvoiceStatus.VALIDATING.name()).isEqualTo("VALIDATING");
        assertThat(InvoiceStatus.VALIDATED.name()).isEqualTo("VALIDATED");
        assertThat(InvoiceStatus.INVALID.name()).isEqualTo("INVALID");
        assertThat(InvoiceStatus.FORWARDED.name()).isEqualTo("FORWARDED");
        assertThat(InvoiceStatus.FAILED.name()).isEqualTo("FAILED");
    }

    @Test
    void testStatusOrdinalOrder() {
        // Verify status follows expected state machine order
        assertThat(InvoiceStatus.RECEIVED.ordinal()).isLessThan(InvoiceStatus.VALIDATING.ordinal());
        assertThat(InvoiceStatus.VALIDATING.ordinal()).isLessThan(InvoiceStatus.VALIDATED.ordinal());
        assertThat(InvoiceStatus.VALIDATED.ordinal()).isLessThan(InvoiceStatus.FORWARDED.ordinal());
    }
}
