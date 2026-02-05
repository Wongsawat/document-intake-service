# CDC Integration Tests

This document provides detailed explanations of all CDC (Change Data Capture) integration tests for the Document Intake Service.

## Overview

The CDC integration tests verify the complete outbox pattern flow:
```
Document Intake Service → PostgreSQL → Outbox Table → Debezium CDC → Kafka
```

### Test Files

| File | Purpose |
|------|---------|
| `AbstractCdcIntegrationTest.java` | Base class with infrastructure setup, Kafka polling utilities |
| `OutboxTableIntegrationTest.java` | Verifies outbox table schema (Debezium columns, indexes) |
| `DocumentIntakeCdcIntegrationTest.java` | Full CDC flow tests (Database → Outbox → Debezium → Kafka) |

## Prerequisites

### External Containers

Before running CDC integration tests, start the required containers:

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
```

### Container Configuration

| Container | Host | Port | Purpose |
|-----------|------|------|---------|
| PostgreSQL | localhost | 5433 | Test database (`intake_db`) |
| Kafka | localhost | 9093 | Message broker for CDC events |
| Debezium Connect | localhost | 8083 | CDC connector |

### Debezium Connector

The tests expect a connector named `outbox-connector-intake` to be running. The connector is configured to:
- Monitor `public.outbox_events` table
- Use EventRouter transform to route events based on `topic` field
- Set message key from `partition_key` field
- Include `headers` as Kafka headers

## Base Class: AbstractCdcIntegrationTest

### Infrastructure Setup (`@BeforeAll`)

```java
setupInfrastructure() {
    1. verifyExternalContainers()     // Check PostgreSQL, Kafka, Debezium are accessible
    2. verifyDebeziumConnectorRunning() // Wait up to 2 minutes for connector RUNNING state
    3. kafkaConfig.createTopics()      // Create required Kafka topics
    4. subscribeToTopics()             // Subscribe consumer to test topics
}
```

### Test Data Cleanup (`@BeforeEach`)

```java
cleanupTestData() {
    DELETE FROM outbox_events;
    DELETE FROM incoming_invoices;
    receivedMessages.clear();
}
```

### Helper Methods

| Method | Purpose |
|--------|---------|
| `verifyExternalContainers()` | Pings PostgreSQL (SELECT 1), Kafka (listTopics), Debezium (GET /connectors) |
| `verifyDebeziumConnectorRunning()` | Polls connector status every 5 seconds for up to 2 minutes |
| `isConnectorRunning(connectorName)` | Checks if connector JSON response contains `"state":"RUNNING"` |
| `pollKafkaMessages()` | Polls Kafka for 500ms and caches messages by topic |
| `hasMessageOnTopic(topic, partitionKey)` | Returns true if message with given key exists on topic |
| `getMessagesFromTopic(topic, partitionKey)` | Returns all messages matching the partition key |
| `parseJson(json)` | Parses JSON string using Jackson ObjectMapper |
| `loadTestXml(filename)` | Loads XML from `src/test/resources/samples/valid/` |
| `await()` | Returns Awaitility config (2 minutes timeout, 2 second poll interval) |

---

## OutboxTableIntegrationTest

**Purpose**: Verify database schema is compatible with Debezium CDC and saga-commons library. These tests do not require Kafka/Debezium to be running.

### Test 1: shouldHaveOutboxEventsTable

**Validates**: The `outbox_events` table exists in the database.

```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'outbox_events'
```

**Why**: Ensures Flyway migration created the outbox table required for the outbox pattern.

---

### Test 2: shouldHaveIncomingInvoicesTable

**Validates**: The `incoming_invoices` table exists in the database.

```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'incoming_invoices'
```

**Why**: Ensures Flyway migration created the main document storage table.

---

### Test 3: shouldHaveDebeziumRoutingColumns

**Validates**: The outbox_events table has Debezium EventRouter columns.

```sql
SELECT column_name FROM information_schema.columns
WHERE table_name = 'outbox_events'
AND column_name IN ('topic', 'partition_key', 'headers')
```

**Expected**: `topic`, `partition_key`, `headers`

**Why**: Debezium EventRouter transform requires these columns:
- `topic` - Specifies which Kafka topic to route the event to
- `partition_key` - Used as Kafka message key for ordering
- `headers` - Additional metadata to include as Kafka headers

---

### Test 4: shouldHaveSagaCommonsColumns

**Validates**: The outbox_events table has saga-commons library columns.

```sql
SELECT column_name FROM information_schema.columns
WHERE table_name = 'outbox_events'
AND column_name IN ('retry_count', 'error_message', 'published_at')
```

**Expected**: `retry_count`, `error_message`, `published_at`

**Why**: saga-commons library uses these for polling-based event publishing:
- `retry_count` - Number of retry attempts
- `error_message` - Last error message if publish failed
- `published_at` - Timestamp when event was successfully published

---

### Test 5: shouldHaveStatusIndex

**Validates**: The outbox_events table has an index on the `status` column.

```sql
SELECT indexname FROM pg_indexes
WHERE tablename = 'outbox_events' AND indexname LIKE '%status%'
```

**Why**: The status index is critical for Debezium CDC performance. The connector queries:
```sql
SELECT * FROM outbox_events WHERE status = 'PENDING'
```
Without this index, table scans would occur on every poll.

---

## DocumentIntakeCdcIntegrationTest

**Purpose**: Verify the complete CDC flow from document submission through to Kafka message delivery.

### Nested Test Class: DatabaseWriteTests

#### Test 1.1: shouldSaveIncomingDocumentToDatabase

**Purpose**: Verify documents are persisted with correct status and document type.

**Flow**:
1. Load valid TaxInvoice XML from test samples
2. Call `documentIntakeService.submitInvoice(xml, "API", correlationId)`
3. Verify returned `IncomingDocument` has non-null ID and `FORWARDED` status
4. Query database directly via JDBC (bypasses JPA session cache)
5. Verify `status = 'FORWARDED'` and `document_type = 'TAX_INVOICE'`

**Key Assertions**:
- `document.getId()` is not null
- `document.getStatus()` equals `FORWARDED`
- Database row has `status = 'FORWARDED'`
- Database row has `document_type = 'TAX_INVOICE'`

**Why JDBC not JPA**: Using `JdbcTemplate` ensures we read from the database, not the Hibernate session cache. This proves the data was actually persisted.

---

#### Test 1.2: shouldCreateOutboxEventsInSameTransaction

**Purpose**: Verify outbox events are created atomically with document state changes.

**Flow**:
1. Submit valid TaxInvoice document
2. Query `outbox_events` table for all events with the document's aggregate ID
3. Verify at least 3 events exist

**Expected Events**:
1. `DocumentReceivedTraceEvent` (status=RECEIVED)
2. `DocumentReceivedTraceEvent` (status=VALIDATED)
3. `StartSagaCommand` (to orchestrator)
4. `DocumentReceivedTraceEvent` (status=FORWARDED)

**Key Assertion**: `outboxEvents.size() >= 3`

**Why**: The transactional boundary ensures that if the document is saved, all outbox events must also be saved. This is the core guarantee of the outbox pattern.

---

### Nested Test Class: OutboxPatternTests

#### Test 2.1: shouldWriteStartSagaCommandWithCorrectTopic

**Purpose**: Verify saga commands have correct routing for Debezium EventRouter.

**Flow**:
1. Submit valid TaxInvoice document
2. Query outbox for `StartSagaCommand` event
3. Verify Debezium routing fields

**Expected Values**:
| Field | Value | Purpose |
|-------|-------|---------|
| `topic` | `saga.commands.orchestrator` | Debezium routes to this topic |
| `partition_key` | `correlationId` | Kafka message key for ordering |
| `aggregate_type` | `IncomingDocument` | Identifies the aggregate |
| `status` | `PENDING` | Awaiting CDC publication |

**Why**: The orchestrator-service consumes from `saga.commands.orchestrator` topic to begin the saga.

---

#### Test 2.2: shouldWriteTraceEventsWithCorrectTopic

**Purpose**: Verify trace events route to notification service.

**Flow**:
1. Submit valid TaxInvoice document
2. Query outbox for all `DocumentReceivedTraceEvent` events
3. Verify all have same topic and partition key

**Expected Values**:
| Field | Value |
|-------|-------|
| `topic` | `trace.document.received` |
| `partition_key` | `correlationId` |

**Why**: The notification-service consumes from `trace.document.received` for lifecycle tracking and alerting.

---

#### Test 2.3: shouldSetCorrectPartitionKeyForOrdering

**Purpose**: Verify all events for a document use the same partition key.

**Flow**:
1. Submit document with correlation ID
2. Query for distinct partition keys matching that correlation ID
3. Verify exactly 1 distinct partition key

**Key Assertion**: Exactly 1 distinct `partition_key` for all events

**Why**: Kafka guarantees ordering within a partition. Using the same `correlationId` as partition key ensures all events for a document are processed in order by consumers.

---

### Nested Test Class: CdcFlowTests

#### Test 3.1: shouldPublishStartSagaCommandToKafkaTopic

**Purpose**: Verify end-to-end CDC flow: Database → Debezium → Kafka.

**Flow**:
1. Submit valid TaxInvoice document
2. Wait for Kafka message (up to 2 minutes with 2-second polls)
3. Verify message arrived on `saga.commands.orchestrator` topic
4. Parse JSON payload and verify fields

**Key Assertions**:
- Message exists on `saga.commands.orchestrator` topic
- `payload.documentId` matches document ID
- `payload.documentType` equals `TAX_INVOICE`
- `payload.correlationId` matches submitted correlation ID

**Why Async Wait**: CDC has inherent latency. Debezium polls PostgreSQL (typically every 1-5 seconds), processes the change, and publishes to Kafka. The `await()` handles this timing.

---

#### Test 3.2: shouldPublishTraceEventsToKafkaTopic

**Purpose**: Verify trace events flow through CDC to Kafka.

**Flow**:
1. Submit valid TaxInvoice document
2. Wait for messages on `trace.document.received` topic
3. Verify at least 1 trace event arrived

**Expected**: Multiple trace events (RECEIVED, VALIDATED, FORWARDED)

**Why**: Confirms the notification-service can consume document lifecycle events.

---

#### Test 3.3: shouldPreserveCorrelationIdThroughCdcFlow

**Purpose**: Verify correlation ID is preserved through entire CDC pipeline.

**Flow**:
1. Submit document with known correlation ID
2. Wait for message on `saga.commands.orchestrator`
3. Verify correlation ID in both Kafka key and payload

**Key Assertions**:
- Kafka message key (`record.key()`) equals correlation ID
- `payload.correlationId` equals correlation ID

**Why**: The correlation ID links the entire document processing flow across services. It's used for:
- Distributed tracing
- Log aggregation
- Debugging multi-service issues

---

### Nested Test Class: ErrorHandlingTests

#### Test 4.1: shouldNotPublishStartSagaCommandForInvalidDocument

**Purpose**: Verify invalid documents don't trigger saga commands.

**Flow**:
1. Submit invalid XML (`<invalid>not a valid e-tax document</invalid>`)
2. Catch expected exception
3. Verify NO `StartSagaCommand` exists in outbox

**Key Assertion**: `sagaCommands` list is empty

**Why**: Invalid documents should not propagate downstream. The outbox pattern ensures atomicity - if validation fails, no saga command is written.

---

### Nested Test Class: DocumentTypeTests

#### Test 5.1: shouldHandleInvoiceDocumentType

**Purpose**: Verify different document types are handled correctly.

**Flow**:
1. Load `Invoice_2p1_valid.xml` (not TaxInvoice)
2. Submit document
3. Verify domain object has `documentType = INVOICE`
4. Verify outbox payload contains `documentType = INVOICE`

**Key Assertions**:
- `document.getDocumentType().name()` equals `INVOICE`
- Outbox payload `documentType` equals `INVOICE`

**Why**: The service must correctly handle all 6 Thai e-Tax document types. This test verifies the Invoice type specifically.

**Note**: Similar tests could be added for:
- RECEIPT
- DEBIT_CREDIT_NOTE
- CANCELLATION_NOTE
- ABBREVIATED_TAX_INVOICE

---

## Test Data

### Valid Sample Documents

Located in `src/test/resources/samples/valid/`:

| File | Document Type | Purpose |
|------|---------------|---------|
| `TaxInvoice_2p1_valid.xml` | TAX_INVOICE | Primary test document |
| `Invoice_2p1_valid.xml` | INVOICE | Document type testing |
| `Receipt_2p1_valid.xml` | RECEIPT | Document type testing |
| `DebitNote_2p1_valid.xml` | DEBIT_CREDIT_NOTE | Document type testing |
| `CreditNote_2p1_valid.xml` | DEBIT_CREDIT_NOTE | Document type testing |
| `AbbreviatedTaxInvoice_2p1_valid.xml` | ABBREVIATED_TAX_INVOICE | Document type testing |

---

## Running the Tests

### Quick Start

```bash
# 1. Start containers (one-time setup)
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors

# 2. Run all CDC integration tests
cd services/document-intake-service
mvn test -Dtest="*CdcIntegrationTest,*TableIntegrationTest" -Dspring.profiles.active=cdc-test

# 3. Stop containers when done
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-stop.sh
```

### Run Specific Test Categories

```bash
# Schema-only tests (no Debezium required)
mvn test -Dtest=OutboxTableIntegrationTest -Dspring.profiles.active=cdc-test

# Full CDC flow tests
mvn test -Dtest=DocumentIntakeCdcIntegrationTest -Dspring.profiles.active=cdc-test

# Specific nested test class
mvn test -Dtest=DocumentIntakeCdcIntegrationTest\$DatabaseWriteTests -Dspring.profiles.active=cdc-test

# Specific test method
mvn test -Dtest=DocumentIntakeCdcIntegrationTest#shouldSaveIncomingDocumentToDatabase -Dspring.profiles.active=cdc-test
```

---

## Common Issues

### Containers Not Running

**Error**: `External containers are not accessible!`

**Solution**: Start the test containers:
```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
```

### Debezium Connector Not Running

**Error**: Tests timeout waiting for connector

**Solution**: Verify connector is deployed:
```bash
curl http://localhost:8083/connectors
curl http://localhost:8083/connectors/outbox-connector-intake/status
```

Expected output:
```json
{
  "name": "outbox-connector-intake",
  "connector": {
    "state": "RUNNING",
    "worker_id": "..."
  }
}
```

### Messages Not Appearing on Kafka

**Error**: `await()` timeout waiting for messages

**Possible Causes**:
1. Debezium connector not configured to watch `intake_db`
2. `wal_level` not set to `logical` in PostgreSQL
3. Kafka topic not created

**Debug Commands**:
```bash
# Check outbox table has events
psql -h localhost -p 5433 -U postgres -d intake_db -c "SELECT * FROM outbox_events ORDER BY created_at DESC LIMIT 5"

# Check Kafka topic exists
kafka-topics --bootstrap-server localhost:9093 --list

# Check connector configuration
curl http://localhost:8083/connectors/outbox-connector-intake/config
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CDC Integration Test Flow                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│  │  Test Setup     │     │  Document       │     │   PostgreSQL    │       │
│  │  (@BeforeAll)   │────▶│  Submission     │────▶│   Database      │       │
│  │                 │     │                 │     │                 │       │
│  │ • Containers    │     │ submitInvoice() │     │ • incoming_     │       │
│  │ • Connector     │     │                 │     │   invoices      │       │
│  │ • Topics        │     │                 │     │ • outbox_events │       │
│  └─────────────────┘     └─────────────────┘     └────────┬────────┘       │
│                                                           │                 │
│                                                           │                 │
│                                                           ▼                 │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│  │  Test           │     │   Debezium      │     │    Kafka        │       │
│  │  Assertions     │◀────│     CDC         │────▶│   Topics        │       │
│  │                 │     │                 │     │                 │       │
│  │ • Database      │     │ • EventRouter   │     │ • saga.commands │       │
│  │   verified      │     │ • Transform     │     │   .orchestrator │       │
│  │ • Outbox        │     │ • Logical       │     │ • trace.document│       │
│  │   verified      │     │   Replication   │     │   .received     │       │
│  │ • Kafka         │     │                 │     │                 │       │
│  │   verified      │     │                 │     │                 │       │
│  └─────────────────┘     └─────────────────┘     └─────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary

| Test Category | Tests | Focus |
|---------------|-------|-------|
| **Database Write** | 2 | Document and outbox persistence |
| **Outbox Pattern** | 3 | Correct topic, partition key, routing |
| **CDC Flow** | 3 | End-to-end Debezium → Kafka delivery |
| **Error Handling** | 1 | Invalid documents don't publish |
| **Document Types** | 1 | Different document type handling |
| **Schema** | 5 | Table structure and indexes |

Total: **15 test cases** comprehensively covering the outbox pattern with Debezium CDC.
