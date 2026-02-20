package com.wpanther.document.intake.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.integration.config.CdcTestConfiguration;
import com.wpanther.document.intake.integration.config.TestKafkaConsumerConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for CDC integration tests.
 * <p>
 * Prerequisites:
 *   1. Start containers: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 *   2. Containers must be running:
 *      - PostgreSQL: localhost:5433
 *      - Kafka: localhost:9093
 *      - Debezium: localhost:8083
 */
@SpringBootTest(
    classes = CdcTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("cdc-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCdcIntegrationTest {

    protected static final String POSTGRES_HOST = "localhost";
    protected static final int POSTGRES_PORT = 5433;
    protected static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9093";
    protected static final String DEBEZIUM_URL = "http://localhost:8083";
    protected static final String DEBEZIUM_CONNECTOR_NAME = "outbox-connector-intake";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected KafkaConsumer<String, String> testKafkaConsumer;

    @Autowired
    protected TestKafkaConsumerConfig kafkaConfig;

    protected HttpClient httpClient = HttpClient.newHttpClient();

    // Cache for received Kafka messages (topic -> list of records)
    protected Map<String, List<ConsumerRecord<String, String>>> receivedMessages = new ConcurrentHashMap<>();

    @BeforeAll
    void setupInfrastructure() throws Exception {
        verifyExternalContainers();
        verifyDebeziumConnectorRunning();
        kafkaConfig.createTopics();
        subscribeToTopics();
    }

    @BeforeEach
    void cleanupTestData() {
        // Clean tables in correct order (foreign key constraints)
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM incoming_invoices");
        receivedMessages.clear();
    }

    /**
     * Verify that external containers are accessible.
     */
    private void verifyExternalContainers() {
        try {
            // Test PostgreSQL connection
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            assertThat(result).isEqualTo(1);

            // Test Kafka connection
            Properties props = new Properties();
            props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
            try (AdminClient adminClient = AdminClient.create(props)) {
                adminClient.listTopics().names().get();
            }

            // Test Debezium connection
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
                "Please start them first:\n" +
                "  cd /home/wpanther/projects/etax/invoice-microservices\n" +
                "  ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors\n\n" +
                "Error: " + e.getMessage() + "\n", e);
        }
    }

    /**
     * Verify that Debezium connector is running.
     * Waits up to 2 minutes for connector to be RUNNING.
     */
    private void verifyDebeziumConnectorRunning() {
        await().atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> isConnectorRunning(DEBEZIUM_CONNECTOR_NAME));
    }

    /**
     * Check if a Debezium connector is in RUNNING state.
     */
    protected boolean isConnectorRunning(String connectorName) {
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

    /**
     * Subscribe to Kafka topics for message verification.
     */
    private void subscribeToTopics() {
        testKafkaConsumer.subscribe(List.of(
            "saga.commands.orchestrator",
            "trace.document.received"
        ));
    }

    /**
     * Poll Kafka for messages and cache them.
     */
    protected void pollKafkaMessages() {
        ConsumerRecords<String, String> records = testKafkaConsumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            receivedMessages.computeIfAbsent(record.topic(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);
        }
    }

    /**
     * Check if a message with given partition key exists on topic.
     */
    protected boolean hasMessageOnTopic(String topic, String partitionKey) {
        pollKafkaMessages();
        List<ConsumerRecord<String, String>> messages = receivedMessages.get(topic);
        if (messages == null) return false;
        return messages.stream().anyMatch(r -> partitionKey.equals(r.key()));
    }

    /**
     * Get messages from topic matching partition key.
     */
    protected List<ConsumerRecord<String, String>> getMessagesFromTopic(String topic, String partitionKey) {
        pollKafkaMessages();
        List<ConsumerRecord<String, String>> messages = receivedMessages.get(topic);
        if (messages == null) return Collections.emptyList();
        return messages.stream()
            .filter(r -> partitionKey.equals(r.key()))
            .toList();
    }

    /**
     * Parse JSON from Kafka message value.
     */
    protected JsonNode parseJson(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    /**
     * Load test XML from resources.
     */
    protected String loadTestXml(String filename) throws IOException {
        Path path = Path.of(getClass().getClassLoader()
            .getResource("samples/valid/" + filename).getPath());
        return Files.readString(path);
    }

    /**
     * Awaitility helper with default configuration.
     */
    protected ConditionFactory await() {
        return Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2));
    }
}