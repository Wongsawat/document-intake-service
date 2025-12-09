# Invoice Intake Service - Program Flow

## Overview

This document describes the complete program flow for the Invoice Intake Service, detailing how XML invoices are received, validated, and forwarded to downstream services.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Invoice Intake Service                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌─────────────────┐         ┌──────────────────┐  │
│  │  REST API    │────────▶│  Apache Camel   │────────▶│  Kafka Producer  │  │
│  │  Controller  │         │  Routes         │         │  (invoice.received)│ │
│  └──────────────┘         └─────────────────┘         └──────────────────┘  │
│                                   │                                          │
│  ┌──────────────┐                 │                                          │
│  │Kafka Consumer│─────────────────┘                                          │
│  │(invoice.intake)                                                           │
│  └──────────────┘                 │                                          │
│                                   ▼                                          │
│                    ┌─────────────────────────┐                               │
│                    │  InvoiceIntakeService   │                               │
│                    │  (Application Layer)    │                               │
│                    └─────────────────────────┘                               │
│                                   │                                          │
│                    ┌──────────────┴──────────────┐                           │
│                    ▼                             ▼                           │
│         ┌──────────────────┐          ┌──────────────────┐                   │
│         │XmlValidationService│        │IncomingInvoice   │                   │
│         │(Domain Service)    │        │(Aggregate Root)  │                   │
│         └──────────────────┘          └──────────────────┘                   │
│                                              │                               │
│                                              ▼                               │
│                               ┌──────────────────────────┐                   │
│                               │  PostgreSQL Database     │                   │
│                               │  (incoming_invoices)     │                   │
│                               └──────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Entry Points

The service has two entry points for receiving invoices:

### 1. REST API Entry Point

```
POST /api/v1/invoices
Content-Type: application/xml
X-Correlation-ID: <optional-correlation-id>

<Invoice>...</Invoice>
```

### 2. Kafka Consumer Entry Point

```
Topic: invoice.intake
Key: correlation-id
Value: <Invoice XML content>
```

---

## Flow 1: REST API Invoice Submission

### Sequence Diagram

```
┌────────┐     ┌────────────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌────────────────┐     ┌──────────┐     ┌───────┐
│ Client │     │InvoiceIntakeController│  │ProducerTemplate│  │   CamelConfig   │     │InvoiceIntakeService│ │  Database  │   │ Kafka │
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
    │                     │                     │                     │ submitInvoice()       │                   │              │
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
    │                     │                     │                     │ IncomingInvoice       │                   │              │
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
   - Body: XML invoice content

2. **InvoiceIntakeController receives request**
   - File: `application/controller/InvoiceIntakeController.java`
   - Generates correlation ID if not provided
   - Sends to Camel route via `ProducerTemplate`

3. **Camel Route processes message**
   - File: `infrastructure/config/CamelConfig.java`
   - Route ID: `invoice-intake-direct`
   - Calls `InvoiceIntakeService.submitInvoice()`

4. **InvoiceIntakeService orchestrates business logic**
   - File: `application/service/InvoiceIntakeService.java`
   - Extracts invoice number from XML
   - Checks for duplicate invoice numbers
   - Creates `IncomingInvoice` aggregate
   - Saves to database (status: RECEIVED)
   - Transitions to VALIDATING status
   - Performs XSD validation
   - Marks as VALIDATED or INVALID

5. **Camel Route handles result**
   - If valid: Creates `InvoiceReceivedEvent` and publishes to Kafka
   - Marks invoice as FORWARDED
   - If invalid: Logs failure, does not forward

6. **Response returned to client**
   - 202 Accepted with correlation ID

---

## Flow 2: Kafka Invoice Consumption

### Sequence Diagram

```
┌───────┐     ┌─────────────────┐     ┌─────────────────┐     ┌──────────┐     ┌───────┐
│ Kafka │     │   CamelConfig   │     │InvoiceIntakeService│ │  Database  │   │ Kafka │
│(intake)│    │  Kafka Route    │     │                   │   │            │   │(received)│
└───┬───┘     └────────┬────────┘     └────────┬──────────┘   └─────┬────┘   └────┬────┘
    │                  │                       │                    │              │
    │ Message          │                       │                    │              │
    │ (XML content)    │                       │                    │              │
    │─────────────────▶│                       │                    │              │
    │                  │                       │                    │              │
    │                  │ submitInvoice()       │                    │              │
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
    │                  │ IncomingInvoice       │                    │              │
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
   - Topic: `invoice.intake`
   - Consumer Group: `intake-service`
   - Correlation ID from Kafka message key

2. **Camel Kafka Route processes message**
   - Route ID: `invoice-intake-kafka`
   - Same processing logic as REST route

3. **Validation and forwarding**
   - Same as REST flow steps 4-5

---

## Invoice State Machine

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
         └── Initial state when invoice is created
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

Failed messages are sent to `invoice.intake.dlq` after retry exhaustion.

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
Send to DLQ (invoice.intake.dlq)
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

### InvoiceReceivedEvent (Published)

```json
{
  "eventId": "uuid",
  "eventType": "invoice.received",
  "occurredAt": "2025-12-07T10:30:00Z",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "xmlContent": "<Invoice>...</Invoice>",
  "correlationId": "uuid"
}
```

### Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `invoice.intake` | Consumer | Receive invoices from external systems |
| `invoice.received` | Producer | Forward validated invoices to processing |
| `invoice.intake.dlq` | Producer | Dead letter queue for failed messages |

---

## Component Responsibilities

### InvoiceIntakeController
- REST API endpoint handler
- Request/response mapping
- Delegates to Camel route via ProducerTemplate

### CamelConfig (Camel Routes)
- Message routing orchestration
- Error handling with DLQ
- Kafka integration
- Event creation and publishing

### InvoiceIntakeService
- Business logic orchestration
- Transaction management
- Idempotency checks
- Coordinates domain objects and repositories

### IncomingInvoice (Aggregate Root)
- Enforces state machine transitions
- Validates business invariants
- Encapsulates invoice lifecycle

### XmlValidationService
- XSD schema validation
- Invoice number extraction
- Integration with teda library

### IncomingInvoiceRepository
- Data persistence abstraction
- Domain-oriented query methods

---

## Database Schema

### incoming_invoices Table

```sql
CREATE TABLE incoming_invoices (
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
      invoice-intake: invoice.intake
      invoice-received: invoice.received
      intake-dlq: invoice.intake.dlq
```

### Camel Error Handler

```java
errorHandler(deadLetterChannel("kafka:" + intakeDlqTopic)
    .maximumRedeliveries(3)
    .redeliveryDelay(1000)
    .useExponentialBackOff()
    .logExhausted(true));
```
