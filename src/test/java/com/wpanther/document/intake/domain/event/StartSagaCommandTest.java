package com.wpanther.document.intake.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StartSagaCommand Tests")
class StartSagaCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

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

        ObjectMapper mapper = MAPPER;
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

        ObjectMapper mapper = MAPPER;
        StartSagaCommand command = mapper.readValue(json, StartSagaCommand.class);

        assertThat(command.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Should create command with null correlationId via builder")
    void shouldCreateCommandWithNullCorrelationIdViaBuilder() {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId(null)
                .source("API")
                .build();

        assertThat(command.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Should create command with KAFKA source")
    void shouldCreateCommandWithKafkaSource() {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("KAFKA")
                .build();

        assertThat(command.getSource()).isEqualTo("KAFKA");
    }

    @Test
    @DisplayName("Should create command with all document types")
    void shouldCreateCommandWithAllDocumentTypes() {
        String[] documentTypes = {
            "TAX_INVOICE", "RECEIPT", "INVOICE", "DEBIT_CREDIT_NOTE",
            "CANCELLATION_NOTE", "ABBREVIATED_TAX_INVOICE"
        };

        for (String docType : documentTypes) {
            StartSagaCommand command = StartSagaCommand.builder()
                    .documentId("doc-123")
                    .documentType(docType)
                    .documentNumber("INV-001")
                    .xmlContent("<xml></xml>")
                    .correlationId("corr-123")
                    .source("API")
                    .build();

            assertThat(command.getDocumentType()).isEqualTo(docType);
        }
    }

    @Test
    @DisplayName("Should serialize to JSON")
    void shouldSerializeToJson() throws Exception {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("API")
                .build();

        ObjectMapper mapper = MAPPER;
        String json = mapper.writeValueAsString(command);

        assertThat(json).contains("\"documentId\":\"doc-123\"");
        assertThat(json).contains("\"documentType\":\"TAX_INVOICE\"");
        assertThat(json).contains("\"documentNumber\":\"INV-001\"");
        assertThat(json).contains("\"xmlContent\":\"<xml></xml>\"");
        assertThat(json).contains("\"correlationId\":\"corr-123\"");
        assertThat(json).contains("\"source\":\"API\"");
    }

    @Test
    @DisplayName("Should deserialize and serialize roundtrip")
    void shouldDeserializeAndSerializeRoundtrip() throws Exception {
        StartSagaCommand original = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("API")
                .build();

        ObjectMapper mapper = MAPPER;
        String json = mapper.writeValueAsString(original);
        StartSagaCommand deserialized = mapper.readValue(json, StartSagaCommand.class);

        assertThat(deserialized.getDocumentId()).isEqualTo(original.getDocumentId());
        assertThat(deserialized.getDocumentType()).isEqualTo(original.getDocumentType());
        assertThat(deserialized.getDocumentNumber()).isEqualTo(original.getDocumentNumber());
        assertThat(deserialized.getXmlContent()).isEqualTo(original.getXmlContent());
        assertThat(deserialized.getCorrelationId()).isEqualTo(original.getCorrelationId());
        assertThat(deserialized.getSource()).isEqualTo(original.getSource());
    }

    @Test
    @DisplayName("Should handle null correlationId in serialization")
    void shouldHandleNullCorrelationIdInSerialization() throws Exception {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId(null)
                .source("API")
                .build();

        ObjectMapper mapper = MAPPER;
        String json = mapper.writeValueAsString(command);

        assertThat(json).contains("\"correlationId\":null");
    }
}
