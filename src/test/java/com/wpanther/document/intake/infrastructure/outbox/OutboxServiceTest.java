package com.wpanther.document.intake.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OutboxService
 * Tests use mocks for repository and ObjectMapper dependencies
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxService Unit Tests")
class OutboxServiceTest {

    @Mock
    private JpaOutboxEventRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    private static final String TEST_CORRELATION_ID = "test-corr-123";
    private static final String TEST_DOCUMENT_ID = "doc-123";
    private static final String TEST_INVOICE_NUMBER = "INV-001";

    @BeforeEach
    void setUp() throws Exception {
        // Default mock behaviors for JSON serialization
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"test\":\"payload\"}");
        when(objectMapper.writeValueAsString(any(Map.class)))
            .thenReturn("{\"correlationId\":\"" + TEST_CORRELATION_ID + "\"}");
    }

    // ==================== Happy Path Tests ====================

    @Test
    @DisplayName("Write event saves outbox entry with correct fields")
    void testWriteEvent_SavesOutboxEntry() throws Exception {
        // Given
        StartSagaCommand command = createTestCommand();
        String expectedPayload = "{\"documentId\":\"" + TEST_DOCUMENT_ID + "\"}";
        String expectedHeaders = "{\"correlationId\":\"" + TEST_CORRELATION_ID + "\"}";

        when(objectMapper.writeValueAsString(command)).thenReturn(expectedPayload);
        when(objectMapper.writeValueAsString(Map.of("correlationId", TEST_CORRELATION_ID, "documentType", "TAX_INVOICE")))
            .thenReturn(expectedHeaders);
        when(repository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboxService.writeEvent(
            "IncomingDocument",
            TEST_DOCUMENT_ID,
            "StartSagaCommand",
            "saga.commands.orchestrator",
            command,
            Map.of("correlationId", TEST_CORRELATION_ID, "documentType", "TAX_INVOICE")
        );

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        OutboxEventEntity savedEvent = captor.getValue();
        assertThat(savedEvent.getAggregateType()).isEqualTo("IncomingDocument");
        assertThat(savedEvent.getAggregateId()).isEqualTo(TEST_DOCUMENT_ID);
        assertThat(savedEvent.getEventType()).isEqualTo("StartSagaCommand");
        assertThat(savedEvent.getTopic()).isEqualTo("saga.commands.orchestrator");
        assertThat(savedEvent.getPayload()).isEqualTo(expectedPayload);
        assertThat(savedEvent.getHeaders()).isEqualTo(expectedHeaders);
        assertThat(savedEvent.getStatus()).isEqualTo("PENDING");
        assertThat(savedEvent.getPartitionKey()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(savedEvent.getCreatedAt()).isNotNull();
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Write event with null headers uses aggregate ID as partition key")
    void testWriteEvent_WithNullHeaders_UsesAggregateIdAsPartitionKey() throws Exception {
        // Given
        StartSagaCommand command = createTestCommand();
        when(objectMapper.writeValueAsString(command)).thenReturn("{\"test\":\"payload\"}");
        when(repository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboxService.writeEvent(
            "IncomingDocument",
            TEST_DOCUMENT_ID,
            "TestEvent",
            "test.topic",
            command,
            null
        );

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        OutboxEventEntity savedEvent = captor.getValue();
        assertThat(savedEvent.getPartitionKey()).isEqualTo(TEST_DOCUMENT_ID);
        assertThat(savedEvent.getHeaders()).isNull();
    }

    @Test
    @DisplayName("Write event serializes payload to JSON")
    void testWriteEvent_SerializesPayloadToJson() throws Exception {
        // Given
        StartSagaCommand command = StartSagaCommand.builder()
            .documentId("doc-456")
            .documentType("INVOICE")
            .invoiceNumber("INV-002")
            .xmlContent("<Invoice>test</Invoice>")
            .correlationId("corr-456")
            .source("KAFKA")
            .build();

        String expectedPayload = "{\"documentId\":\"doc-456\",\"documentType\":\"INVOICE\"}";
        when(objectMapper.writeValueAsString(command)).thenReturn(expectedPayload);
        when(repository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboxService.writeEvent(
            "IncomingDocument",
            command.getDocumentId(),
            "StartSagaCommand",
            "saga.commands.orchestrator",
            command,
            Map.of("correlationId", command.getCorrelationId())
        );

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).isEqualTo(expectedPayload);
        verify(objectMapper).writeValueAsString(command);
    }

    @Test
    @DisplayName("Write event serializes headers to JSON")
    void testWriteEvent_SerializesHeadersToJson() throws Exception {
        // Given
        Map<String, String> headers = Map.of(
            "correlationId", "test-corr",
            "documentType", "TAX_INVOICE",
            "source", "API"
        );

        String expectedHeaders = "{\"correlationId\":\"test-corr\",\"documentType\":\"TAX_INVOICE\",\"source\":\"API\"}";
        when(objectMapper.writeValueAsString(headers)).thenReturn(expectedHeaders);
        when(repository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboxService.writeEvent(
            "IncomingDocument",
            "doc-789",
            "TestEvent",
            "test.topic",
            Map.of("test", "payload"),
            headers
        );

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getHeaders()).isEqualTo(expectedHeaders);
        verify(objectMapper).writeValueAsString(headers);
    }

    // ==================== Partition Key Tests ====================

    @Test
    @DisplayName("Partition key uses correlationId from headers when present")
    void testWriteEvent_PartitionKey_UsesCorrelationIdFromHeaders() throws Exception {
        // Given
        String correlationId = "my-custom-correlation-id";
        Map<String, String> headers = Map.of("correlationId", correlationId);
        when(repository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboxService.writeEvent(
            "IncomingDocument",
            "doc-aaa",
            "TestEvent",
            "test.topic",
            Map.of("test", "value"),
            headers
        );

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getPartitionKey()).isEqualTo(correlationId);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Write event throws RuntimeException when payload serialization fails")
    void testWriteEvent_ThrowsException_WhenPayloadSerializationFails() throws Exception {
        // Given
        StartSagaCommand command = createTestCommand();
        when(objectMapper.writeValueAsString(command))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization failed") {});

        // When/Then
        assertThatThrownBy(() ->
            outboxService.writeEvent(
                "IncomingDocument",
                TEST_DOCUMENT_ID,
                "TestEvent",
                "test.topic",
                command,
                Map.of("correlationId", TEST_CORRELATION_ID)
            )
        ).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to serialize outbox event");

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Write event throws RuntimeException when headers serialization fails")
    void testWriteEvent_ThrowsException_WhenHeadersSerializationFails() throws Exception {
        // Given
        StartSagaCommand command = createTestCommand();
        Map<String, String> headers = Map.of("correlationId", TEST_CORRELATION_ID);

        when(objectMapper.writeValueAsString(command)).thenReturn("{\"payload\"}");
        when(objectMapper.writeValueAsString(headers))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Headers serialization failed") {});

        // When/Then
        assertThatThrownBy(() ->
            outboxService.writeEvent(
                "IncomingDocument",
                TEST_DOCUMENT_ID,
                "TestEvent",
                "test.topic",
                command,
                headers
            )
        ).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to serialize outbox event");

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    // ==================== Helper Methods ====================

    private StartSagaCommand createTestCommand() {
        return StartSagaCommand.builder()
            .documentId(TEST_DOCUMENT_ID)
            .documentType("TAX_INVOICE")
            .invoiceNumber(TEST_INVOICE_NUMBER)
            .xmlContent("<xml/>")
            .correlationId(TEST_CORRELATION_ID)
            .source("API")
            .build();
    }
}
