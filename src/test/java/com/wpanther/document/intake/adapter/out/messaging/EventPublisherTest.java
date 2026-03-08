package com.wpanther.document.intake.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Tests")
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(outboxService, objectMapper);
    }

    @Test
    @DisplayName("Should publish StartSagaCommand with headers")
    void shouldPublishStartSagaCommand() throws JsonProcessingException {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("API")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"documentType\":\"TAX_INVOICE\"}");

        eventPublisher.publishStartSagaCommand(command);

        ArgumentCaptor<String> headersJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectMapper).writeValueAsString(anyMap());
        verify(outboxService).saveWithRouting(
                eq(command),
                eq("IncomingDocument"),
                eq("doc-123"),
                eq("saga.commands.orchestrator"),
                eq("corr-123"),
                headersJsonCaptor.capture()
        );

        assertThat(headersJsonCaptor.getValue()).contains("corr-123").contains("TAX_INVOICE");
    }

    @Test
    @DisplayName("Should publish DocumentReceivedTraceEvent with headers")
    void shouldPublishDocumentReceivedTraceEvent() throws JsonProcessingException {
        DocumentReceivedTraceEvent event = DocumentReceivedTraceEvent.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .correlationId("corr-123")
                .status("VALIDATED")
                .source("API")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"documentType\":\"TAX_INVOICE\"}");

        eventPublisher.publishTraceEvent(event);

        ArgumentCaptor<String> headersJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectMapper).writeValueAsString(anyMap());
        verify(outboxService).saveWithRouting(
                eq(event),
                eq("IncomingDocument"),
                eq("doc-123"),
                eq("trace.document.received"),
                eq("corr-123"),
                headersJsonCaptor.capture()
        );

        assertThat(headersJsonCaptor.getValue()).contains("corr-123").contains("TAX_INVOICE");
    }

    @Test
    @DisplayName("Should handle JSON serialization failure gracefully")
    void shouldHandleJsonSerializationFailure() throws JsonProcessingException {
        StartSagaCommand command = StartSagaCommand.builder()
                .documentId("doc-123")
                .documentType("TAX_INVOICE")
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .correlationId("corr-123")
                .source("API")
                .build();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON error") {});

        eventPublisher.publishStartSagaCommand(command);

        ArgumentCaptor<String> headersJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
                eq(command),
                eq("IncomingDocument"),
                eq("doc-123"),
                eq("saga.commands.orchestrator"),
                eq("corr-123"),
                headersJsonCaptor.capture()
        );

        assertThat(headersJsonCaptor.getValue()).isNull();
    }
}
