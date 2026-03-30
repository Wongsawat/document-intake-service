package com.wpanther.document.intake.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for outbox table schema.
 * Verifies database structure without requiring Kafka/Debezium.
 */
@DisplayName("Outbox Table Schema Tests")
class OutboxTableIT extends AbstractCdcIT {

    @Test
    @DisplayName("Should have outbox_events table")
    void shouldHaveOutboxEventsTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'outbox_events'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have incoming_invoices table")
    void shouldHaveIncomingInvoicesTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'incoming_invoices'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have Debezium routing columns in outbox_events")
    void shouldHaveDebeziumRoutingColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'outbox_events' " +
            "AND column_name IN ('topic', 'partition_key', 'headers')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder("topic", "partition_key", "headers");
    }

    @Test
    @DisplayName("Should have saga-commons columns in outbox_events")
    void shouldHaveSagaCommonsColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'outbox_events' " +
            "AND column_name IN ('retry_count', 'error_message', 'published_at')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder("retry_count", "error_message", "published_at");
    }

    @Test
    @DisplayName("Should have status index for Debezium polling")
    void shouldHaveStatusIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'outbox_events' AND indexname LIKE '%status%'");

        assertThat(indexes).isNotEmpty();
    }
}
