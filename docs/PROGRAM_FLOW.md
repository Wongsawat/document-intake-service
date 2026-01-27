# Document Intake Service - Program Flow

## Overview

This document describes the complete program flow for the Document Intake Service, detailing how XML documents are received, validated, and forwarded to downstream services.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Document Intake Service                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌─────────────────┐         ┌──────────────────┐  │
│  │  REST API    │────────▶│  Apache Camel   │────────▶│  Kafka Producer  │  │
│  │  Controller  │         │  Routes         │         │  (document.received)│ │
│  └──────────────┘         └─────────────────┘         └──────────────────┘  │
│                                   │                                          │
│  ┌──────────────┐                 │                                          │
│  │Kafka Consumer│─────────────────┘                                          │
│  │(document.intake)                                                         │
│  └──────────────┘                 │                                          │
│                                   ▼                                          │
│                    ┌─────────────────────────┐                               │
│                    │  DocumentIntakeService  │                               │
│                    │  (Application Layer)    │                               │
│                    └─────────────────────────┘                               │
│                                   │                                          │
│                    ┌──────────────┴──────────────┐                           │
│                    ▼                             ▼                           │
│         ┌──────────────────┐          ┌──────────────────┐                   │
│         │XmlValidationService│        │IncomingDocument   │                   │
│         │(Domain Service)    │        │(Aggregate Root)  │                   │
│         └──────────────────┘          └──────────────────┘                   │
│                                              │                               │
│                                              ▼                               │
│                               ┌──────────────────────────┐                   │
│                               │  PostgreSQL Database     │                   │
│                               │  (incoming_documents)     │                   │
│                               └──────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
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

## Flow 1: REST API Document Submission

### Sequence Diagram

```
┌────────┐     ┌────────────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌────────────────┐     ┌──────────┐     ┌───────┐
│ Client │     │DocumentIntakeController│  │ProducerTemplate│  │   CamelConfig   │     │DocumentIntakeService│ │  Database  │   │ Kafka │
└───┬────┘     └──────────┬───────────┘  └──────┬────────┘   └────────┬────────┘     └────────┬───────┘     └─────┬────┘     └───┬───┘
    │                     │                     │                     │                       │                   │              │
    │ POST /api/v1/invoices                     │                     │                       │                   │              │
    │ (XML + correlationId)                     │                     │                       │                   │              │
    │────────────────────▶│                     │                     │                       │                   │              │
    │                     │                     │                     │                       │                   │              │
    │                     │ sendBodyAndHeader() │                     │                       │                   │              │
    │                     │ "direct:invoice-intake"                   │                       │                   │              │
    │                     │────────────────────▶│                     │                       │                   │              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │ route: direct:invoice-intake                │                   │              │
    │                     │                     │────────────────────▶│                       │                   │              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │ submitDocument()       │                   │              │
    │                     │                     │                     │──────────────────────▶│                   │              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │                       │ save(RECEIVED)    │              │
    │                     │                     │                     │                       │──────────────────▶│              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │                       │ save(VALIDATING)  │              │
    │                     │                     │                     │                       │──────────────────▶│              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │                       │ validate XML      │              │
    │                     │                     │                     │                       │ (XmlValidationService)           │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │                       │ save(VALIDATED)   │              │
    │                     │                     │                     │                       │──────────────────▶│              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │◀──────────────────────│                   │              │
    │                     │                     │                     │ IncomingDocument       │                   │              │
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │ if valid: publish to Kafka               │              │
    │                     │                     │                     │───────────────────────────────────────────────────────▶│
    │                     │                     │                     │                       │                   │              │
    │                     │                     │                     │ markForwarded()       │                   │              │
    │                     │                     │                     │──────────────────────▶│                   │              │
    │                     │                     │                     │                       │ save(FORWARDED)   │              │
    │                     │                     │                     │                       │──────────────────▶│              │
    │                     │                     │                     │                       │                   │              │
    │                     │◀────────────────────│                     │                       │                   │              │
    │                     │                     │                     │                       │                   │              │
    │◀────────────────────│                     │                     │                       │                   │              │
    │ 202 Accepted        │                     │                     │                       │                   │              │
    │ {correlationId}     │                     │                     │                       │                   │              │
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
   - Calls `DocumentIntakeService.submitDocument()`

4. **DocumentIntakeService orchestrates business logic**
   - File: `application/service/DocumentIntakeService.java`
   - Extracts invoice number from XML
   - Checks for duplicate invoice numbers
   - Creates `IncomingDocument` aggregate
   - Saves to database (status: RECEIVED)
   - Transitions to VALIDATING status
   - Performs XSD validation
   - Marks as VALIDATED or INVALID

5. **Camel Route handles result**
   - If valid: Creates `DocumentReceivedEvent` and publishes to Kafka
   - Marks document as FORWARDED
   - If invalid: Logs failure, does not forward

6. **Response returned to client**
   - 202 Accepted with correlation ID

---

## Flow 2: Kafka Document Consumption

### Sequence Diagram

```
┌───────┐     ┌─────────────────┐     ┌─────────────────┐     ┌──────────┐     ┌───────┐
│ Kafka │     │   CamelConfig   │     │DocumentIntakeService│ │  Database  │   │ Kafka │
│(intake)│    │  Kafka Route    │     │                   │   │            │   │(received)│
└───┬───┘     └────────┬────────┘     └────────┬──────────┘   └─────┬────┘   └────┬────┘
    │                  │                       │                    │              │
    │ Message          │                       │                    │              │
    │ (XML content)    │                       │                    │              │
    │─────────────────▶│                       │                    │              │
    │                  │                       │                    │              │
    │                  │ submitDocument()       │                    │              │
    │                  │──────────────────────▶│                    │              │
    │                  │                       │                    │              │
    │                  │                       │ save(RECEIVED)     │              │
    │                  │                       │───────────────────▶│              │
    │                  │                       │                    │              │
    │                  │                       │ save(VALIDATING)   │              │
    │                  │                       │───────────────────▶│              │
    │                  │                       │                    │              │
    │                  │                       │ validate XML       │              │
    │                  │                       │                    │              │
    │                  │                       │ save(VALIDATED)    │              │
    │                  │                       │───────────────────▶│              │
    │                  │                       │                    │              │
    │                  │◀──────────────────────│                    │              │
    │                  │ IncomingDocument       │                    │              │
    │                  │                       │                    │              │
    │                  │ if valid: publish     │                    │              │
    │                  │───────────────────────────────────────────────────────▶│
    │                  │                       │                    │              │
    │                  │ markForwarded()       │                    │              │
    │                  │──────────────────────▶│                    │              │
    │                  │                       │ save(FORWARDED)    │              │
    │                  │                       │───────────────────▶│              │
```

### Step-by-Step Flow

1. **Kafka message consumed**
   - Topic: `document.intake`
   - Consumer Group: `intake-service`
   - Correlation ID from Kafka message key

2. **Camel Kafka Route processes message**
   - Route ID: `document-intake-kafka`
   - Same processing logic as REST route

3. **Validation and forwarding**
   - Same as REST flow steps 4-5

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

### State Transitions

| Current State | Method | Condition | Next State |
|---------------|--------|-----------|------------|
| RECEIVED | `startValidation()` | - | VALIDATING |
| VALIDATING | `markValidated(result)` | result.valid() == true | VALIDATED |
| VALIDATING | `markValidated(result)` | result.valid() == false | INVALID |
| VALIDATED | `markForwarded()` | - | FORWARDED |
| Any | `markFailed(error)` | - | FAILED |

---

## Error Handling

### Dead Letter Queue (DLQ)

Failed messages are sent to `document.intake.dlq` after retry exhaustion.

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
| Invalid XML structure | Validation fails, status = INVALID |
| Duplicate invoice number | IllegalStateException, no record created |
| Missing invoice number | IllegalArgumentException, no record created |
| Database error | Retry with exponential backoff, then DLQ |
| Kafka publish error | Retry with exponential backoff, then DLQ |

---

## Kafka Events

### DocumentReceivedEvent (Published)

```json
{
  "eventId": "uuid",
  "eventType": "document.received",
  "occurredAt": "2025-12-07T10:30:00Z",
  "version": 1,
  "documentId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "xmlContent": "<Document>...</Document>",
  "correlationId": "uuid"
}
```

### Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `document.intake` | Consumer | Receive documents from external systems |
| `document.received` | Producer | Forward validated documents to processing |
| `document.intake.dlq` | Producer | Dead letter queue for failed messages |

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
- Event creation and publishing

### DocumentIntakeService
- Business logic orchestration
- Transaction management
- Idempotency checks
- Coordinates domain objects and repositories

### IncomingDocument (Aggregate Root)
- Enforces state machine transitions
- Validates business invariants
- Encapsulates document lifecycle

### XmlValidationService
- XSD schema validation
- Invoice number extraction
- Integration with teda library

### IncomingDocumentRepository
- Data persistence abstraction
- Domain-oriented query methods

---

## Database Schema

### incoming_documents Table

```sql
CREATE TABLE incoming_documents (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
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
      document-intake: document.intake
      document-received: document.received
      intake-dlq: document.intake.dlq
```

### Camel Error Handler

```java
errorHandler(deadLetterChannel("kafka:" + intakeDlqTopic)
    .maximumRedeliveries(3)
    .redeliveryDelay(1000)
    .useExponentialBackOff()
    .logExhausted(true));
```
