package com.wpanther.document.intake.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.document.intake.application.usecase.SubmitDocumentUseCase;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full CDC integration tests for document intake service.
 * Verifies the complete flow: Document -> Database -> Outbox -> Debezium CDC -> Kafka.
 */
@DisplayName("Document Intake CDC Integration Tests")
class DocumentIntakeCdcIT extends AbstractCdcIT {

    @Autowired
    private SubmitDocumentUseCase documentIntakeService;

    @Nested
    @DisplayName("Database Write Tests")
    class DatabaseWriteTests {

        @Test
        @DisplayName("Should save incoming document to database")
        void shouldSaveIncomingDocumentToDatabase() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);

            assertThat(document.getId()).isNotNull();
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.FORWARDED);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM incoming_documents WHERE id = ?::uuid",
                document.getId().toString());

            assertThat(row.get("status")).isEqualTo("FORWARDED");
            assertThat(row.get("document_type")).isEqualTo("TAX_INVOICE");
        }

        @Test
        @DisplayName("Should create outbox events in same transaction")
        void shouldCreateOutboxEventsInSameTransaction() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);

            List<Map<String, Object>> outboxEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
                document.getId().toString());

            assertThat(outboxEvents).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Outbox Pattern Tests")
    class OutboxPatternTests {

        @Test
        @DisplayName("Should write StartSagaCommand with correct topic")
        void shouldWriteStartSagaCommandWithCorrectTopic() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);

            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'StartSagaCommand'",
                document.getId().toString());

            assertThat(outbox.get("topic")).isEqualTo("saga.commands.orchestrator");
            assertThat(outbox.get("partition_key")).isEqualTo(correlationId);
            assertThat(outbox.get("aggregate_type")).isEqualTo("IncomingDocument");
            assertThat(outbox.get("status")).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should write trace events with correct topic")
        void shouldWriteTraceEventsWithCorrectTopic() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);

            List<Map<String, Object>> traceEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'DocumentReceivedTraceEvent'",
                document.getId().toString());

            assertThat(traceEvents).isNotEmpty();
            for (Map<String, Object> event : traceEvents) {
                assertThat(event.get("topic")).isEqualTo("trace.document.received");
                assertThat(event.get("partition_key")).isEqualTo(correlationId);
            }
        }

        @Test
        @DisplayName("Should set correct partition key for ordering")
        void shouldSetCorrectPartitionKeyForOrdering() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            documentIntakeService.submitDocument(xml, "API", correlationId);

            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT DISTINCT partition_key FROM outbox_events WHERE partition_key = ?",
                correlationId);

            assertThat(events).hasSize(1);
        }
    }

    @Nested
    @DisplayName("CDC Flow Tests")
    class CdcFlowTests {

        @Test
        @DisplayName("Should publish StartSagaCommand to Kafka topic via CDC")
        void shouldPublishStartSagaCommandToKafkaTopic() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);
            String documentId = document.getId().toString();

            await().until(() -> hasMessageOnTopic("saga.commands.orchestrator", correlationId));

            List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.commands.orchestrator", correlationId);
            assertThat(messages).isNotEmpty();

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
            assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
            assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Should publish trace events to Kafka topic via CDC")
        void shouldPublishTraceEventsToKafkaTopic() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            documentIntakeService.submitDocument(xml, "API", correlationId);

            await().until(() -> hasMessageOnTopic("trace.document.received", correlationId));

            List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("trace.document.received", correlationId);
            assertThat(messages).isNotEmpty();
            assertThat(messages.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should preserve correlation ID through CDC flow")
        void shouldPreserveCorrelationIdThroughCdcFlow() throws Exception {
            String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            documentIntakeService.submitDocument(xml, "API", correlationId);

            await().until(() -> hasMessageOnTopic("saga.commands.orchestrator", correlationId));

            List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.commands.orchestrator", correlationId);
            ConsumerRecord<String, String> message = messages.get(0);

            assertThat(message.key()).isEqualTo(correlationId);

            JsonNode payload = parseJson(message.value());
            assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not publish StartSagaCommand for invalid document")
        void shouldNotPublishStartSagaCommandForInvalidDocument() throws Exception {
            String invalidXml = "<invalid>not a valid e-tax document</invalid>";
            String correlationId = UUID.randomUUID().toString();

            try {
                documentIntakeService.submitDocument(invalidXml, "API", correlationId);
            } catch (Exception e) {
                // Expected - invalid document
            }

            List<Map<String, Object>> sagaCommands = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE event_type = 'StartSagaCommand' AND partition_key = ?",
                correlationId);

            assertThat(sagaCommands).isEmpty();
        }
    }

    @Nested
    @DisplayName("Document Type Tests")
    class DocumentTypeTests {

        @Test
        @DisplayName("Should handle Invoice document type")
        void shouldHandleInvoiceDocumentType() throws Exception {
            String xml = loadTestXml("Invoice_2p1_valid.xml");
            String correlationId = UUID.randomUUID().toString();

            IncomingDocument document = documentIntakeService.submitDocument(xml, "API", correlationId);

            assertThat(document.getDocumentType().name()).isEqualTo("INVOICE");

            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'StartSagaCommand'",
                document.getId().toString());

            String payload = (String) outbox.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.get("documentType").asText()).isEqualTo("INVOICE");
        }
    }
}
