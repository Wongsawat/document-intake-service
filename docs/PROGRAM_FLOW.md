# Document Intake Service - Program Flow

## Overview

This document describes the complete program flow for the Document Intake Service, detailing how XML documents are received, validated, and forwarded to downstream services via the **outbox pattern with Debezium CDC**.

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    Document Intake Service                                               │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                          │
│  ┌──────────────┐         ┌─────────────────┐         ┌──────────────────────────────────────────────┐  │
│  │  REST API    │────────▶│  Apache Camel   │────────▶│        DocumentIntakeService              │  │
│  │  Controller  │         │  Routes         │         │        (Application Layer)                 │  │
│  └──────────────┘         └─────────────────┘         └──────────────────────────────────────────────┘  │
│  ┌──────────────┘                                                           │                          │
│  │Kafka Consumer│                                                            │                          │
│  │(document.intake)                                                         │                          │
│  └────────────────────────────────────────────────────────────────────────┘                          │
│                                                                               │                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                         Transaction Boundary (@Transactional)                                     │  │
│  │                                                                                                  │  │
│  │  1. Save IncomingDocument (status=RECEIVED)         ┌─────────────────────────────────────────┐ │  │
│  │  2. Write DocumentReceivedTraceEvent (RECEIVED)    │         PostgreSQL Database              │ │  │
│  │     → outbox_events table                          │                                         │ │  │
│  │  3. Update status=VALIDATING                       │  ┌─────────────────┐  ┌──────────────┐  │ │  │
│  │  4. Perform validation (XSD + Schematron)         │  │incoming_invoices│  │outbox_events │  │ │  │
│  │  5. Update status=VALIDATED/INVALID               │  │                 │  │              │  │ │  │
│  │  6. Write DocumentReceivedTraceEvent (VALIDATED)   │  └─────────────────┘  └──────────────┘  │ │  │
│  │     → outbox_events table                          │                                         │ │  │
│  │  7. If valid:                                      │                                         │ │  │
│  │     a. Write StartSagaCommand                      │                                         │ │  │
│  │        → outbox_events table                       │                                         │ │  │
│  │     b. Update status=FORWARDED                     │                                         │ │  │
│  │     c. Write DocumentReceivedTraceEvent (FORWARDED) │                                         │ │  │
│  └───────────────────────────────────────────────────┴─────────────────────────────────────────┘ │  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                      Debezium CDC Layer                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                          │
│  ┌─────────────────────────┐         ┌─────────────────┐         ┌────────────────────────────────────┐  │
│  │  PostgreSQL Logical     │────────▶│  Debezium       │────────▶│     Kafka Topics                   │  │
│  │  Replication (WAL)      │         │  Connect        │         │                                    │  │
│  │  (outbox_events table)  │         │  EventRouter    │         │  • saga.commands.orchestrator     │  │
│  └─────────────────────────┘         └─────────────────┘         │  • trace.document.received        │  │
│                                                               └────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Entry Points

The service has two entry points for receiving documents:

### 1. REST API Entry Point

```
POST /api/v1/invoices
Content-Type: application/xml
X-Correlation-ID: <optional-correlation-id>

<Document>...</Document>
```

### 2. Kafka Consumer Entry Point

```
Topic: document.intake
Key: correlation-id
Value: <Document XML content>
```

---

## Flow 1: REST API Document Submission with Outbox Pattern

### Sequence Diagram

```
┌────────┐  ┌────────────┐  ┌───────┐  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────┐  ┌──────────────┐  ┌───────┐  ┌──────────┐
│ Client │  │ Controller │  │ Camel │  │  Service    │  │ OutboxService │  │ Repository │  │Database  │  │   Debezium   │  │ Kafka │  │Orchestrator│
└───┬────┘  └─────┬──────┘  └───┬───┘  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘  └────┬─────┘  └──────┬───────┘  └───┬───┘  └─────┬────┘
    │             │              │              │                │               │              │              │             │              │
    │POST         │              │              │                │               │              │              │             │              │
    │/api/v1/invoices            │              │                │               │              │              │             │              │
    │(XML+cID)    │              │              │                │               │              │              │             │              │
    │────────────▶│              │              │                │               │              │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │send to Camel │              │                │               │              │              │             │              │
    │             │─────────────▶│              │                │               │              │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │submitInvoice()│                │               │              │              │             │              │
    │             │              │─────────────▶│                │               │              │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │┌───────────────┴───────────────────────────────┐│              │             │              │
    │             │              │              ││          TRANSACTION START                  ││              │             │              │
    │             │              │              │└───────────────────────────────────────────────┘│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │save(RECEIVED)   │               │              │              │             │              │
    │             │              │              │──────────────────────────────▶│              │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │                │               │ incoming_    │              │             │              │
    │             │              │              │                │               │ invoices     │              │             │              │
    │             │              │              │                │               │◀─────────────│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │publish TraceEvent(RECEIVED) → outbox          │              │             │              │
    │             │              │              │────────────────▶│ saveWithRouting()              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │ outbox_events│             │              │
    │             │              │              │                │               │◀─────────────│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │status=VALIDATING│               │              │              │             │              │
    │             │              │              │──────────────────────────────▶│              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │validate XML(XSD+Schematron)                   │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │status=VALIDATED │               │              │              │             │              │
    │             │              │              │──────────────────────────────▶│              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │publish TraceEvent(VALIDATED) → outbox         │              │             │              │
    │             │              │              │────────────────▶│ saveWithRouting()              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │if valid:       │               │              │              │             │              │
    │             │              │              │publish StartSagaCommand → outbox              │              │             │              │
    │             │              │              │────────────────▶│ saveWithRouting()              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │status=FORWARDED │               │              │              │             │              │
    │             │              │              │──────────────────────────────▶│              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │publish TraceEvent(FORWARDED) → outbox          │              │             │              │
    │             │              │              │────────────────▶│ saveWithRouting()              │              │             │              │
    │             │              │              │                │               │─────────────▶│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │              │┌───────────────┴───────────────────────────────┐│              │             │              │
    │             │              │              ││          TRANSACTION COMMIT                  ││              │             │              │
    │             │              │              │└───────────────────────────────────────────────┘│              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │              │◀─────────────│                │               │              │              │             │              │
    │             │              │              │                │               │              │              │             │              │
    │             │◀─────────────│              │                │               │              │              │             │              │
    │202 Accepted │              │                │               │              │              │             │              │
    │{correlationId}             │                │               │              │              │             │              │
    │             │              │                │               │              │              │             │              │
    │             │              │                │               │              │              │CDC reads WAL│             │              │
    │             │              │                │               │              │              │◀────────────│             │              │
    │             │              │                │               │              │              │             │             │              │
    │             │              │                │               │              │              │EventRouter   │             │              │
    │             │              │                │               │              │              │─────────────▶│             │              │
    │             │              │                │               │              │              │             │ saga.commands│              │
    │             │              │                │               │              │              │             │ .orchestrator│              │
    │             │              │                │               │              │              │             │             │              │
    │             │              │                │               │              │              │             │             │Consumes     │
    │             │              │                │               │              │              │             │─────────────▶│              │
```

### Step-by-Step Flow

1. **Client sends POST request**
   - Endpoint: `POST /api/v1/invoices`
   - Headers: `Content-Type: application/xml`, `X-Correlation-ID: <optional>`
   - Body: XML document content

2. **DocumentIntakeController receives request**
   - File: `application/controller/DocumentIntakeController.java`
   - Generates correlation ID if not provided
   - Sends to Camel route via `ProducerTemplate`

3. **Camel Route processes message**
   - File: `infrastructure/config/CamelConfig.java`
   - Route ID: `document-intake-direct`
   - Calls `DocumentIntakeService.submitInvoice()`

4. **DocumentIntakeService orchestrates within @Transactional**
   - File: `application/service/DocumentIntakeService.java`
   - **All database operations and outbox writes occur in a single transaction**

5. **Transaction Operations** (all or nothing):
   a. Save `IncomingDocument` (status: RECEIVED) → `incoming_invoices` table
   b. Publish `DocumentReceivedTraceEvent` (status: RECEIVED) → `outbox_events` table
   c. Update status to VALIDATING → `incoming_invoices` table
   d. Perform validation (XSD + Schematron)
   e. Update status to VALIDATED/INVALID → `incoming_invoices` table
   f. Publish `DocumentReceivedTraceEvent` (status: VALIDATED) → `outbox_events` table
   g. If valid:
      - Publish `StartSagaCommand` → `outbox_events` table
      - Update status to FORWARDED → `incoming_invoices` table
      - Publish `DocumentReceivedTraceEvent` (status: FORWARDED) → `outbox_events` table

6. **Transaction commits** - Both domain state and outbox events are atomically persisted

7. **Debezium CDC processes outbox table**:
   - Debezium connector reads `outbox_events` via PostgreSQL logical replication (WAL)
   - EventRouter transform routes events based on `topic` field
   - Events published to Kafka topics:
     - `saga.commands.orchestrator` (StartSagaCommand)
     - `trace.document.received` (DocumentReceivedTraceEvent)

8. **Response returned to client**
   - 202 Accepted with correlation ID

---

## Flow 2: Debezium CDC Event Publishing

### Debezium CDC Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    Debezium CDC Flow                                                      │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                          │
│  1. Transaction Commit in Document Intake Service                                                        │
│     ┌─────────────────┐     ┌─────────────────┐                                                           │
│     │incoming_invoices│     │ outbox_events   │                                                           │
│     │     table       │     │     table       │                                                           │
│     └────────┬────────┘     └────────┬────────┘                                                           │
│              │                      │                                                                   │
│              └──────────┬───────────┘                                                                   │
│                         │                                                                               │
│                         ▼                                                                               │
│  2. PostgreSQL Write-Ahead Log (WAL)                                                                    │
│     ┌─────────────────────────────────────────────────────────┐                                        │
│     │  Logical Replication Slot: debezium_slot                │                                        │
│     │  • Changes captured as they commit                       │                                        │
│     │  • Guaranteed ordering                                  │                                        │
│     └────────────────────────┬────────────────────────────────┘                                        │
│                              │                                                                         │
│                              ▼                                                                         │
│  3. Debezium PostgreSQL Connector                                                                     │
│     ┌─────────────────────────────────────────────────────────┐                                        │
│     │  Reads WAL changes for:                                 │                                        │
│     │  • table.include.list: public.outbox_events             │                                        │
│     │  • poll.interval.ms: 1000 (default)                     │                                        │
│     └────────────────────────┬────────────────────────────────┘                                        │
│                              │                                                                         │
│                              ▼                                                                         │
│  4. EventRouter Transform                                                                            │
│     ┌─────────────────────────────────────────────────────────┐                                        │
│     │  Extracts from outbox_events row:                       │                                        │
│     │  • topic field → Kafka topic name                       │                                        │
│     │  • partition_key field → Kafka message key               │                                        │
│     │  • payload field → Kafka message body                    │                                        │
│     │  • headers field → Kafka message headers                 │                                        │
│     └────────────────────────┬────────────────────────────────┘                                        │
│                              │                                                                         │
│                              ▼                                                                         │
│  5. Kafka Topics                                                                                    │
│     ┌──────────────────────────┐  ┌───────────────────────────────────────┐                            │
│     │ saga.commands.orchestrator│  │ trace.document.received              │                            │
│     │ (StartSagaCommand)        │  │ (DocumentReceivedTraceEvent)         │                            │
│     └──────────────────────────┘  └───────────────────────────────────────┘                            │
│                 │                                    │                                            │
│                 ▼                                    ▼                                            │
│     ┌──────────────────────┐          ┌──────────────────────────┐                                   │
│     │ orchestrator-service │          │ notification-service     │                                   │
│     └──────────────────────┘          └──────────────────────────┘                                   │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Outbox Event Flow Detail

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              Outbox Event Lifecycle                                                      │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                          │
│  Service writes event to outbox:                    │                                                   │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐ │
│  │ outbox_events table                                                                               │ │
│  │ ┌───────────────┬──────────────┬──────────────────┬─────────────┬─────────────┬──────────────┐   │ │
│  │ │ id           │ aggregate_id │ event_type       │ topic       │ partition_key│ payload      │   │ │
│  │ ├───────────────┼──────────────┼──────────────────┼─────────────┼─────────────┼──────────────┤   │ │
│  │ │ uuid-1       │ doc-123      │ StartSagaCommand │ saga.commands│ doc-123     │ {...}        │   │ │
│  │ │              │              │                  │ .orchestrator│             │              │   │ │
│  │ ├───────────────┼──────────────┼──────────────────┼─────────────┼─────────────┼──────────────┤   │ │
│  │ │ uuid-2       │ doc-123      │ TraceEvent       │ trace.document│ doc-123     │ {...}        │   │ │
│  │ │              │              │                  │ .received    │             │              │   │ │
│  │ └───────────────┴──────────────┴──────────────────┴─────────────┴─────────────┴──────────────┘   │ │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                              │                                                                         │
│                              │ Debezium CDC reads row                                                  │
│                              ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐ │
│  │ Debezium EventRouter Transform                                                                    │ │
│  │                                                                                                    │ │
│  │ Input (outbox row):                         Output (Kafka record):                                 │ │
│  │ • topic: "saga.commands.orchestrator"       topic: saga.commands.orchestrator                     │ │
│  │ • partition_key: "doc-123"              key: "doc-123"                                             │ │
│  │ • payload: '{"commandId":"..."}'         value: {"commandId":"..."}                                │ │
│  │ • headers: '{"contentType":"application/json"}'  headers: {"contentType":"application/json"}       │ │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                              │                                                                         │
│                              ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐ │
│  │ Kafka Topic: saga.commands.orchestrator                                                          │ │
│  │                                                                                                    │ │
│  │ Partition 0 (key: doc-123)                                                                         │ │
│  │ ┌──────────────────────────────────────────────────────────────────────────────────────────────┐ │ │
│  │ │ Offset 100: {"commandId":"...","documentId":"doc-123",...}                                  │ │ │
│  │ └──────────────────────────────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Debezium Connector Configuration

**Connector Name**: `outbox-connector-intake`

**Key Settings**:
```json
{
  "name": "outbox-connector-intake",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.dbname": "intake_db",
    "table.include.list": "public.outbox_events",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.by.field": "topic",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "transforms.outbox.table.field.event.key": "partition_key",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.fields.additional.placement": "headers:header"
  }
}
```

---

## Document State Machine

```
                                    ┌─────────────┐
                                    │   FAILED    │
                                    └─────────────┘
                                          ▲
                                          │ markFailed()
                                          │ (any state)
    ┌──────────┐      ┌────────────┐      │       ┌───────────┐      ┌───────────┐
    │ RECEIVED │─────▶│ VALIDATING │──────┼──────▶│ VALIDATED │─────▶│ FORWARDED │
    └──────────┘      └────────────┘      │       └───────────┘      └───────────┘
         │           startValidation()    │      markValidated()     markForwarded()
         │                                │       (valid=true)
         │                                │
         │                                ▼
         │                         ┌───────────┐
         │                         │  INVALID  │
         │                         └───────────┘
         │                        markValidated()
         │                         (valid=false)
         │
         └── Initial state when document is created
```

**State Transitions with Outbox Events**:

| Current State | Method | Outbox Events Written | Next State |
|---------------|--------|----------------------|------------|
| Initial | `submitInvoice()` | `DocumentReceivedTraceEvent` (RECEIVED) | RECEIVED |
| RECEIVED | `startValidation()` | None | VALIDATING |
| VALIDATING | `markValidated(true)` | `DocumentReceivedTraceEvent` (VALIDATED), `StartSagaCommand`, `DocumentReceivedTraceEvent` (FORWARDED) | FORWARDED |
| VALIDATING | `markValidated(false)` | `DocumentReceivedTraceEvent` (INVALID) | INVALID |
| Any | `markFailed()` | `DocumentReceivedTraceEvent` (FAILED) | FAILED |

---

## Error Handling

### Dead Letter Queue (DLQ)

Failed messages from the `document.intake` consumer are sent to `document.intake.dlq` after retry exhaustion.

```
Error occurs
    │
    ▼
Retry (attempt 1, delay 1s)
    │
    ▼ (still failing)
Retry (attempt 2, delay 2s - exponential backoff)
    │
    ▼ (still failing)
Retry (attempt 3, delay 4s - exponential backoff)
    │
    ▼ (still failing)
Send to DLQ (document.intake.dlq)
```

### Error Scenarios

| Error Type | Handling |
|------------|----------|
| Invalid XML structure | Validation fails, status = INVALID, trace event published, NO saga command |
| Database connection error | Transaction rolls back, no outbox events written, retry via DLQ |
| Debezium connector failure | Outbox events remain in table with status=PENDING (future: cleanup service) |
| Kafka unavailable | Debezium retries internally, events preserved in outbox table |

### Outbox Event Status

| Status | Meaning |
|--------|---------|
| PENDING | Event written to outbox, awaiting CDC processing |
| PUBLISHED | Event successfully published to Kafka (future: polling-based cleanup) |
| FAILED | Event publish failed (future: retry mechanism) |

---

## Outbox Table Schema

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,      -- "IncomingDocument"
    aggregate_id VARCHAR(100) NOT NULL,         -- document ID
    event_type VARCHAR(100) NOT NULL,           -- "StartSagaCommand", "DocumentReceivedTraceEvent"
    topic VARCHAR(255),                        -- Kafka topic (Debezium routing)
    partition_key VARCHAR(255),                 -- Kafka message key (ordering)
    payload TEXT NOT NULL,                      -- Event JSON payload
    headers TEXT,                               -- Kafka headers (JSON)
    status VARCHAR(20),                         -- "PENDING", "PUBLISHED", "FAILED"
    retry_count INTEGER DEFAULT 0,              -- For future retry mechanism
    error_message VARCHAR(1000),                -- Last error if failed
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

-- Critical for CDC performance
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);
```

---

## Kafka Events

### StartSagaCommand (sent to `saga.commands.orchestrator`)

```json
{
  "commandId": "uuid",
  "occurredAt": "2025-12-07T10:30:00Z",
  "documentId": "uuid",
  "documentType": "TAX_INVOICE",
  "invoiceNumber": "INV-2025-001",
  "xmlContent": "<TaxInvoice_CrossIndustryInvoice>...</>",
  "correlationId": "uuid",
  "source": "API"
}
```

### DocumentReceivedTraceEvent (sent to `trace.document.received`)

```json
{
  "documentId": "uuid",
  "documentType": "TAX_INVOICE",
  "invoiceNumber": "INV-2025-001",
  "correlationId": "uuid",
  "status": "RECEIVED",
  "source": "API",
  "occurredAt": "2025-12-07T10:30:00Z"
}
```

### Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `document.intake` | External systems | Document Intake | Receive documents from external systems |
| `saga.commands.orchestrator` | Document Intake (via CDC) | orchestrator-service | Start saga for validated documents |
| `trace.document.received` | Document Intake (via CDC) | notification-service | Trace document lifecycle |
| `document.intake.dlq` | Document Intake | - | Dead letter queue |

---

## Component Responsibilities

### DocumentIntakeController
- REST API endpoint handler
- Request/response mapping
- Delegates to Camel route via ProducerTemplate

### CamelConfig (Camel Routes)
- Message routing orchestration
- Error handling with DLQ
- Kafka integration
- No longer produces events directly (uses outbox pattern)

### DocumentIntakeService
- Business logic orchestration with @Transactional
- Coordinates domain objects and repositories
- Publishes events via EventPublisher (outbox pattern)

### EventPublisher
- Wrapper around OutboxService from saga-commons
- Methods:
  - `publishStartSagaCommand(StartSagaCommand)` → `saga.commands.orchestrator`
  - `publishTraceEvent(DocumentReceivedTraceEvent)` → `trace.document.received`

### OutboxService (from saga-commons)
- Writes events to `outbox_events` table
- Methods:
  - `saveWithRouting()` - includes Debezium CDC fields (topic, partition_key, headers)

### IncomingDocument (Aggregate Root)
- Enforces state machine transitions
- Validates business invariants
- Encapsulates document lifecycle

### JpaOutboxEventRepository (service-specific)
- JPA implementation of saga-commons OutboxEventRepository
- Registered as bean via OutboxConfig

---

## Database Schema

### incoming_invoices Table

```sql
CREATE TABLE incoming_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(100),
    document_type VARCHAR(50) NOT NULL,
    xml_content TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    validation_result JSONB,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Configuration

### Kafka Topics (application.yml)

```yaml
app:
  kafka:
    topics:
      invoice-intake: document.intake
      intake-dlq: document.intake.dlq
      saga-commands-orchestrator: saga.commands.orchestrator
      trace-document-received: trace.document.received
```

### CDC Test Profile (application-cdc-test.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/intake_db
  kafka:
    bootstrap-servers: localhost:9093
```

### Camel Error Handler

```java
errorHandler(deadLetterChannel("kafka:" + intakeDlqTopic)
    .maximumRedeliveries(3)
    .redeliveryDelay(1000)
    .useExponentialBackOff()
    .logExhausted(true));
```

---

## Summary of Key Changes from Legacy Architecture

| Aspect | Legacy (Direct Kafka) | Current (Outbox + CDC) |
|--------|----------------------|------------------------|
| Event Publishing | Direct Kafka producer in Camel routes | Outbox table within transaction |
| Event Delivery | Best-effort (no guarantee) | Guaranteed by transaction + CDC |
| Downstream Consumer | Direct to processing services | orchestrator-service via saga pattern |
| Topics | `document.received.{type}` | `saga.commands.orchestrator` |
| Trace Events | None | `trace.document.received` |
| Failure Handling | DLQ on Kafka error | Events preserved in outbox table |
| Ordering | Per Kafka partition | Per correlation ID (partition_key) |
