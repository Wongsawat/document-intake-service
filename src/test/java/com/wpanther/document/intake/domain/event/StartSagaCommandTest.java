package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StartSagaCommand Tests")
class StartSagaCommandTest {

    @Test
    @DisplayName("Should create command with builder")
    void shouldCreateCommandWithBuilder() {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("API")
                .build();

        assertThat(command.getDocumentId()).isEqualTo("doc-123");
        assertThat(command.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(command.getDocumentNumber()).isEqualTo("INV-001");
        assertThat(command.getXmlContent()).isEqualTo("<xml></xml>");
        assertThat(command.getCorrelationId()).isEqualTo("corr-123");
        assertThat(command.getSource()).isEqualTo("API");
        assertThat(command.getEventId()).isNotNull();
        assertThat(command.getOccurredAt()).isNotNull();
        assertThat(command.getEventType()).isEqualTo("StartSagaCommand");
        assertThat(command.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should deserialize from JSON with all fields")
    void shouldDeserializeFromJson() throws Exception {
        String json = "{\n" +
                "  \"eventId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
                "  \"occurredAt\": \"2024-01-01T00:00:00Z\",\n" +
                "  \"eventType\": \"StartSagaCommand\",\n" +
                "  \"version\": 1,\n" +
                "  \"documentId\": \"doc-123\",\n" +
                "  \"documentType\": \"TAX_INVOICE\",\n" +
                "  \"documentNumber\": \"INV-001\",\n" +
                "  \"xmlContent\": \"<xml></xml>\",\n" +
                "  \"correlationId\": \"corr-123\",\n" +
                "  \"source\": \"API\"\n" +
                "}";

        ObjectMapper mapper = new ObjectMapper();
        StartSagaCommand command = mapper.readValue(json, StartSagaCommand.class);

        assertThat(command.getEventId()).isNotNull();
        assertThat(command.getDocumentId()).isEqualTo("doc-123");
        assertThat(command.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(command.getDocumentNumber()).isEqualTo("INV-001");
        assertThat(command.getXmlContent()).isEqualTo("<xml></xml>");
        assertThat(command.getCorrelationId()).isEqualTo("corr-123");
        assertThat(command.getSource()).isEqualTo("API");
        assertThat(command.getEventType()).isEqualTo("StartSagaCommand");
        assertThat(command.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should deserialize from JSON with null correlationId")
    void shouldDeserializeFromJsonWithNullCorrelationId() throws Exception {
        String json = "{\n" +
                "  \"eventId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
                "  \"occurredAt\": \"2024-01-01T00:00:00Z\",\n" +
                "  \"eventType\": \"StartSagaCommand\",\n" +
                "  \"version\": 1,\n" +
                "  \"documentId\": \"doc-123\",\n" +
                "  \"documentType\": \"TAX_INVOICE\",\n" +
                "  \"documentNumber\": \"INV-001\",\n" +
                "  \"xmlContent\": \"<xml></xml>\",\n" +
                "  \"correlationId\": null,\n" +
                "  \"source\": \"API\"\n" +
                "}";

        ObjectMapper mapper = new ObjectMapper();
        StartSagaCommand command = mapper.readValue(json, StartSagaCommand.class);

        assertThat(command.getCorrelationId()).isNull();
    }
}
