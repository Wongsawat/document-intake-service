package com.wpanther.document.intake.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.integration.config.RestApiCdcTestConfiguration;
import com.wpanther.document.intake.integration.config.TestKafkaConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CDC integration tests for the REST API endpoint.
 * <p>
 * Verifies: HTTP POST → REST Controller → Camel Route → SubmitDocumentUseCase →
 * Database + Outbox → Debezium CDC → Kafka.
 * <p>
 * Prerequisites:
 * 1. Start containers: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 * 2. Containers must be running:
 * - PostgreSQL: localhost:5433
 * - Kafka: localhost:9093
 * - Debezium: localhost:8083
 */
@SpringBootTest(
    classes = RestApiCdcTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.kafka.consumer.auto-startup=false",
        "app.security.enabled=false"
    }
)
@ActiveProfiles("cdc-test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Document Intake REST API CDC Integration Tests")
class DocumentIntakeRestApiCdcIT {

    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9093";
    private static final String DEBEZIUM_URL = "http://localhost:8083";
    private static final String DEBEZIUM_CONNECTOR_NAME = "outbox-connector-intake";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaConsumer<String, String> testKafkaConsumer;

    @Autowired
    private TestKafkaConsumerConfig kafkaConfig;

    private HttpClient httpClient = HttpClient.newHttpClient();

    // Cache for received Kafka messages (topic -> list of records)
    private final Map<String, List<ConsumerRecord<String, String>>> receivedMessages =
        new ConcurrentHashMap<>();

    @BeforeAll
    void setupInfrastructure() throws Exception {
        verifyExternalContainers();
        verifyDebeziumConnectorRunning();
        kafkaConfig.createTopics();
        subscribeToTopics();
    }

    @BeforeEach
    void cleanupTestData() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM incoming_documents");
        receivedMessages.clear();
    }

    private void verifyExternalContainers() {
        try {
            // Verify PostgreSQL
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            assertThat(result).isEqualTo(1);

            // Verify Kafka
            Properties props = new Properties();
            props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
            try (var adminClient = org.apache.kafka.clients.admin.AdminClient.create(props)) {
                adminClient.listTopics().names().get();
            }

            // Verify Debezium Connect
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEBEZIUM_URL + "/connectors"))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Debezium Connect returned status " + response.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "\n\n" +
                "==========================================================\n" +
                "External containers are not accessible!\n" +
                "==========================================================\n" +
                "Please start them first (from invoice-microservices root):\n" +
                "  ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors\n\n" +
                "Error: " + e.getMessage() + "\n", e);
        }
    }

    private void verifyDebeziumConnectorRunning() {
        await().atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> isConnectorRunning(DEBEZIUM_CONNECTOR_NAME));
    }

    private boolean isConnectorRunning(String connectorName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEBEZIUM_URL + "/connectors/" + connectorName + "/status"))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            return response.body().contains("\"state\":\"RUNNING\"");
        } catch (Exception e) {
            return false;
        }
    }

    private void subscribeToTopics() {
        testKafkaConsumer.subscribe(List.of(
            "saga.commands.orchestrator",
            "trace.document.received"
        ));
    }

    private void pollKafkaMessages() {
        var records = testKafkaConsumer.poll(Duration.ofMillis(500));
        for (var record : records) {
            receivedMessages.computeIfAbsent(record.topic(), k -> Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(record);
        }
    }

    private boolean hasMessageOnTopic(String topic, String partitionKey) {
        pollKafkaMessages();
        var messages = receivedMessages.get(topic);
        if (messages == null) return false;
        return messages.stream().anyMatch(r -> partitionKey.equals(r.key()));
    }

    private List<ConsumerRecord<String, String>> getMessagesFromTopic(String topic, String partitionKey) {
        pollKafkaMessages();
        var messages = receivedMessages.get(topic);
        if (messages == null) return List.of();
        return messages.stream().filter(r -> partitionKey.equals(r.key())).toList();
    }

    private String loadTestXml(String filename) throws Exception {
        Path path = Path.of(getClass().getClassLoader()
            .getResource("samples/valid/" + filename).toURI());
        return Files.readString(path);
    }

    private ConditionFactory await() {
        return Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("TC-01: Should submit Tax Invoice via REST API and produce CDC message")
    void shouldSubmitTaxInvoiceViaRestApiAndProduceCdcMessage() throws Exception {
        // Given
        String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
        String correlationId = java.util.UUID.randomUUID().toString();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-Correlation-ID", correlationId)
                .content(xml))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value(correlationId))
            .andExpect(jsonPath("$.message").exists())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseBody);
        String returnedCorrelationId = responseJson.get("correlationId").asText();

        // Then - Verify database state
        Map<String, Object> invoice = jdbcTemplate.queryForMap(
            "SELECT * FROM incoming_documents WHERE correlation_id = ?",
            returnedCorrelationId);

        assertThat(invoice.get("document_type")).isEqualTo("TAX_INVOICE");
        assertThat(invoice.get("correlation_id")).isEqualTo(returnedCorrelationId);
        assertThat(invoice.get("status")).isEqualTo("FORWARDED");

        // Verify outbox event for StartSagaCommand
        Map<String, Object> outboxEvent = jdbcTemplate.queryForMap(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'StartSagaCommand'",
            invoice.get("id").toString());

        assertThat(outboxEvent.get("topic")).isEqualTo("saga.commands.orchestrator");
        assertThat(outboxEvent.get("partition_key")).isEqualTo(returnedCorrelationId);

        // Then - Verify CDC message via Kafka
        await().until(() -> hasMessageOnTopic("saga.commands.orchestrator", returnedCorrelationId));

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.commands.orchestrator", returnedCorrelationId);
        assertThat(messages).isNotEmpty();

        JsonNode payload = objectMapper.readTree(messages.get(0).value());
        assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        assertThat(payload.get("correlationId").asText()).isEqualTo(returnedCorrelationId);
        assertThat(payload.get("documentId").asText()).isNotNull();
        assertThat(payload.get("xmlContent").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-02: Should submit Invoice via REST API and produce CDC message")
    void shouldSubmitInvoiceViaRestApiAndProduceCdcMessage() throws Exception {
        // Given
        String xml = loadTestXml("Invoice_2p1_valid.xml");
        String correlationId = java.util.UUID.randomUUID().toString();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-Correlation-ID", correlationId)
                .content(xml))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value(correlationId))
            .andExpect(jsonPath("$.message").exists())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseBody);
        String returnedCorrelationId = responseJson.get("correlationId").asText();

        // Then - Verify database state
        Map<String, Object> invoice = jdbcTemplate.queryForMap(
            "SELECT * FROM incoming_documents WHERE correlation_id = ?",
            returnedCorrelationId);

        assertThat(invoice.get("document_type")).isEqualTo("INVOICE");
        assertThat(invoice.get("correlation_id")).isEqualTo(returnedCorrelationId);
        assertThat(invoice.get("status")).isEqualTo("FORWARDED");

        // Verify outbox event for StartSagaCommand
        Map<String, Object> outboxEvent = jdbcTemplate.queryForMap(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'StartSagaCommand'",
            invoice.get("id").toString());

        assertThat(outboxEvent.get("topic")).isEqualTo("saga.commands.orchestrator");
        assertThat(outboxEvent.get("partition_key")).isEqualTo(returnedCorrelationId);

        // Then - Verify CDC message via Kafka
        await().until(() -> hasMessageOnTopic("saga.commands.orchestrator", returnedCorrelationId));

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("saga.commands.orchestrator", returnedCorrelationId);
        assertThat(messages).isNotEmpty();

        JsonNode payload = objectMapper.readTree(messages.get(0).value());
        assertThat(payload.get("documentType").asText()).isEqualTo("INVOICE");
        assertThat(payload.get("correlationId").asText()).isEqualTo(returnedCorrelationId);
        assertThat(payload.get("documentId").asText()).isNotNull();
        assertThat(payload.get("xmlContent").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-03: Should publish trace event to Kafka via CDC")
    void shouldPublishTraceEventToKafkaViaCdc() throws Exception {
        // Given
        String xml = loadTestXml("TaxInvoice_2p1_valid.xml");
        String correlationId = java.util.UUID.randomUUID().toString();

        // When
        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-Correlation-ID", correlationId)
                .content(xml))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.correlationId").value(correlationId));

        // Then - Verify trace event via CDC
        await().until(() -> hasMessageOnTopic("trace.document.received", correlationId));

        List<ConsumerRecord<String, String>> messages = getMessagesFromTopic("trace.document.received", correlationId);
        assertThat(messages).isNotEmpty();

        // Find the FORWARDED trace event
        JsonNode forwardedEvent = null;
        for (ConsumerRecord<String, String> record : messages) {
            JsonNode payload = objectMapper.readTree(record.value());
            if ("FORWARDED".equals(payload.get("status").asText())) {
                forwardedEvent = payload;
                break;
            }
        }

        assertThat(forwardedEvent).isNotNull();
        assertThat(forwardedEvent.get("correlationId").asText()).isEqualTo(correlationId);
        assertThat(forwardedEvent.get("documentType").asText()).isEqualTo("TAX_INVOICE");
    }
}
