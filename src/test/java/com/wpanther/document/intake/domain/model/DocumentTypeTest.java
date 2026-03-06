package com.wpanther.document.intake.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    @Test
    void hasExactlySixValues() {
        assertThat(DocumentType.values()).hasSize(6);
    }

    @Test
    void containsAllExpectedTypes() {
        assertThat(DocumentType.values()).containsExactlyInAnyOrder(
            DocumentType.TAX_INVOICE,
            DocumentType.RECEIPT,
            DocumentType.INVOICE,
            DocumentType.DEBIT_CREDIT_NOTE,
            DocumentType.CANCELLATION_NOTE,
            DocumentType.ABBREVIATED_TAX_INVOICE
        );
    }

    @Test
    void isValueOfByName() {
        assertThat(DocumentType.valueOf("TAX_INVOICE")).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(DocumentType.valueOf("ABBREVIATED_TAX_INVOICE")).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
    }
}
