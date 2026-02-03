# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Commit Conventions

When creating git commits for this repository:
- **Do NOT include** `Co-Authored-By:` or any co-author attribution in commit messages
- Keep commit messages focused and concise

## Project Overview

The **Document Intake Service** (port 8081) is a Spring Boot microservice that serves as the gateway for receiving and validating Thai e-Tax XML documents. It performs three-layer validation (well-formedness, XSD schema, Schematron rules) using the teda library, then publishes commands to the Saga Orchestrator via the outbox pattern.

**Tech Stack**: Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, PostgreSQL, Kafka, Eureka, Debezium CDC

**Package**: `com.wpanther.document.intake`

## Saga Orchestrator Pattern

This service implements the **Outbox Pattern** with Debezium CDC for reliable event delivery to the Saga Orchestrator.

### Architecture Flow

```
External System/REST/Kafka
    ↓
DocumentIntakeService.submitInvoice()
    ↓
┌─────────────────────────────────────────────────────────────┐
│  Transaction Boundary                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Save IncomingDocument (status=RECEIVED)            │  │
│  │ 2. Write DocumentReceivedTraceEvent (status=RECEIVED) │  │
│  │    → outbox_events table                              │  │
│  │ 3. Update status=VALIDATING                            │  │
│  │ 4. Perform validation (XSD + Schematron)              │  │
│  │ 5. Update status=VALIDATED/INVALID                     │  │
│  │ 6. Write DocumentReceivedTraceEvent (status=VALIDATED) │  │
│  │    → outbox_events table                              │  │
│  │ 7. If valid:                                          │  │
│  │    a. Write StartSagaCommand                          │  │
│  │       → outbox_events table                          │  │
│  │    b. Update status=FORWARDED                          │  │
│  │    c. Write DocumentReceivedTraceEvent (FORWARDED)     │  │
│  │       → outbox_events table                          │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
    ↓
Debezium CDC (reads outbox_events table)
    ↓
Kafka Topics:
  - saga.commands.orchestrator (StartSagaCommand)
  - trace.document.received (DocumentReceivedTraceEvent)
```

### Outbox Table Schema

The outbox table follows the saga-commons `JpaOutboxEventEntity` schema:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,      -- e.g., "IncomingDocument"
    aggregate_id VARCHAR(100) NOT NULL,         -- e.g., document ID
    event_type VARCHAR(100) NOT NULL,           -- e.g., "StartSagaCommand"
    topic VARCHAR(255) NOT NULL,                -- Kafka topic name (Debezium EventRouter)
    partition_key VARCHAR(255),                 -- Kafka partition key (ordering)
    payload JSON NOT NULL,                      -- Event payload (JSONB)
    headers JSON,                               -- Kafka headers (JSONB)
    status VARCHAR(20),                         -- "PENDING", "PUBLISHED", "FAILED"
    retry_count INTEGER DEFAULT 0,              -- For polling-based retry
    error_message VARCHAR(1000),                -- Last error message
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);
```

**Migration sequence**:
- `V2__create_outbox_events_table.sql`: Initial outbox table
- `V4__enhance_outbox_for_debezium.sql`: Added Debezium CDC fields (`topic`, `partition_key`, `headers`)
- `V5__add_saga_commons_outbox_columns.sql`: Added saga-commons standard columns (`retry_count`, `error_message`)

### Event Types

**StartSagaCommand** (sent to `saga.commands.orchestrator`):
```json
{
  "commandId": "uuid",
  "occurredAt": "2025-12-03T10:30:00Z",
  "documentId": "uuid",
  "documentType": "TAX_INVOICE",
  "invoiceNumber": "INV-2025-001",
  "xmlContent": "<TaxInvoice_CrossIndustryInvoice>...</>",
  "correlationId": "uuid",
  "source": "API"
}
```

**DocumentReceivedTraceEvent** (sent to `trace.document.received`):
```json
{
  "documentId": "uuid",
  "documentType": "TAX_INVOICE",
  "invoiceNumber": "INV-2025-001",
  "correlationId": "uuid",
  "status": "RECEIVED|VALIDATING|VALIDATED|FORWARDED|INVALID",
  "source": "API|KAFKA"
}
```

## Build and Run Commands

```bash
# Build (FIRST: install teda library if not already)
cd ../../../../teda && mvn clean install

# Build this service
mvn clean package

# Run locally (requires PostgreSQL, Kafka)
mvn spring-boot:run

# Run tests
mvn test

# Run single test method (use fully qualified test name)
mvn test -Dtest=DocumentIntakeServiceTest#testSubmitInvoiceWithValidXml

# Run all tests in a class
mvn test -Dtest=XmlValidationServiceImplTest

# Run tests with specific log level
mvn test -Dtest=CamelConfigTest -Dlogging.level.com.wpanther.document.intake=TRACE

# Run integration tests only (tests ending with IntegrationTest)
mvn test -Dtest=*IntegrationTest

# Database migrations
mvn flyway:migrate
mvn flyway:info

# Code coverage (90% line coverage required per package)
mvn verify
```

### Prerequisites

- PostgreSQL on `localhost:5432` (database: `intake_db`)
- Kafka on `localhost:9092`
- Eureka on `localhost:8761` (optional)
- teda library installed: `cd ../../../../teda && mvn clean install`
- **Debezium Kafka Connect** (for outbox CDC to Kafka)

### Test Configuration

Tests use H2 in-memory database (configured in `src/test/resources/application-test.yml`). No PostgreSQL required for testing.

**Test samples**: Real Thai e-Tax XML documents are in `src/test/resources/samples/`:
- `valid/` - Valid documents for each document type
- `invalid/` - Documents with XSD/Schematron violations for testing validation logic

### Environment Variables

| Variable | Default |
|----------|---------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `intake_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `KAFKA_BROKERS` | `localhost:9092` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |

## Architecture

### DDD Layered Structure

```
com.wpanther.document.intake/
├── domain/           # Core business logic (no framework dependencies)
│   ├── model/        # IncomingDocument, ValidationResult, DocumentStatus
│   ├── repository/   # IncomingDocumentRepository interface
│   ├── service/      # XmlValidationService interface
│   └── event/        # Integration events (Kafka DTOs)
│       ├── StartSagaCommand.java
│       └── DocumentReceivedTraceEvent.java
│
├── application/      # Use cases and orchestration
│   ├── controller/   # DocumentIntakeController (REST API)
│   └── service/      # DocumentIntakeService (orchestration)
│
└── infrastructure/   # Framework and external concerns
    ├── persistence/  # IncomingDocumentEntity, JpaIncomingDocumentRepository
    ├── validation/   # XmlValidationServiceImpl, DocumentType
    ├── messaging/    # EventPublisher (uses saga-commons OutboxService)
    └── config/       # CamelConfig (Camel consumer routes only)
```

### Document State Machine

```
RECEIVED → VALIDATING → VALIDATED → FORWARDED
                      ↘ INVALID
                      ↘ FAILED
```

State transitions are enforced in `IncomingDocument` aggregate:
- `startValidation()`: RECEIVED → VALIDATING
- `markValidated(result)`: VALIDATING → VALIDATED/INVALID (based on result)
- `markForwarded()`: VALIDATED → FORWARDED (after saga command published)
- `markFailed(msg)`: Any → FAILED

### Supported Document Types

The service supports 6 Thai e-Tax document types via the `DocumentType` enum (`infrastructure/validation/DocumentType.java`):

| Type | Namespace | Saga Command |
|------|-----------|--------------|
| `TAX_INVOICE` | `urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2` | orchestrator-service |
| `RECEIPT` | `urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2` | orchestrator-service |
| `INVOICE` | `urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2` | orchestrator-service |
| `DEBIT_CREDIT_NOTE` | `urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2` | orchestrator-service |
| `CANCELLATION_NOTE` | `urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2` | orchestrator-service |
| `ABBREVIATED_TAX_INVOICE` | `urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2` | orchestrator-service |

Document type is auto-detected from XML namespace URI or root element name.

All validated documents are sent to the **orchestrator-service** via `saga.commands.orchestrator` topic, regardless of document type. The orchestrator routes to appropriate processing services.

### Three-Layer XML Validation

`XmlValidationServiceImpl` performs:
1. **XML well-formedness** - Implicit in JAXB unmarshaling
2. **XSD schema validation** - JAXB with pre-loaded schemas from teda library
3. **Schematron business rules** - teda's `SchematronValidator` for ETDA business rules

JAXB contexts, XSD schemas, and Schematron validator are initialized once at startup and cached (thread-safe).

#### JAXB Integration with teda Library

The service uses JAXB for strongly-typed XML processing:
- **Context initialization**: Uses `.impl` packages (e.g., `com.wpanther.etax.generated.taxinvoice.rsm.impl`)
- **JAXBElement handling**: Automatically unwraps JAXBElement during unmarshaling
- **Document type detection**: Detected from unmarshaled class type using `DocumentType.fromJaxbClass()`
- **ValidationEventHandler**: Collects validation errors instead of failing fast

**Critical**: The teda library uses an interface/implementation pattern:
- Interfaces in `.rsm`/`.ram` packages
- Implementations in `.impl` subpackages
- JAXB contexts MUST use `.impl` packages for proper unmarshaling

**Example**:
```java
// CORRECT - Use .impl packages for JAXB context
String contextPath = "com.wpanther.etax.generated.taxinvoice.rsm.impl:"
    + "com.wpanther.etax.generated.taxinvoice.ram.impl:"
    + "com.wpanther.etax.generated.common.qdt.impl:"
    + "com.wpanther.etax.generated.common.udt.impl";
JAXBContext jaxbContext = JAXBContext.newInstance(contextPath);
```

### Apache Camel Routes

Routes defined in `CamelConfig.java`:

1. **REST Intake** (`direct:invoice-intake`): REST controller → validates → publishes events via outbox
2. **Kafka Intake** (`kafka:document.intake`): Kafka → validates → publishes events via outbox

**Note**: Producer routes have been removed. Events are now published via the outbox pattern and Debezium CDC.

Both routes use:
- Dead Letter Channel (`document.intake.dlq`) with 3 retries + exponential backoff
- DocumentIntakeService handles validation and event publishing

### Kafka Topics

| Topic | Direction | Purpose | Event Type |
|-------|-----------|---------|------------|
| `document.intake` | Consumer | Receive documents from external systems | - |
| `saga.commands.orchestrator` | Producer | Start saga for validated documents | StartSagaCommand |
| `trace.document.received` | Producer | Trace document lifecycle for notifications | DocumentReceivedTraceEvent |
| `document.intake.dlq` | Producer | Dead letter queue for failed messages | - |

**LEGACY topics** (kept for reference during migration):
- `document.received.tax-invoice`
- `document.received.receipt`
- `document.received.invoice`
- etc.

These are no longer used. The orchestrator-service now handles routing to processing services.

Topic names are configurable via `app.kafka.topics.*` in `application.yml`.

## Key Implementation Details

### Outbox Pattern

The outbox pattern is implemented using **saga-commons** library components:

**OutboxService** (from saga-commons):
- Uses `MANDATORY` transaction propagation to ensure atomicity
- Writes events to `outbox_events` table within the same transaction as domain state changes
- Provides two publish methods:
  - `publish(IntegrationEvent, String aggregateType, String aggregateId, String payload)` - for polling-based publishers
  - `publishWithRouting(IntegrationEvent, String aggregateType, String aggregateId, String payload, String topic, String partitionKey, String headers)` - for Debezium CDC

**JpaOutboxEventRepository** (from saga-commons):
- Spring Data JPA implementation of `OutboxEventRepository`
- Automatically configured via `@EnableJpaRepositories` scanning
- Maps `JpaOutboxEventEntity` to domain `OutboxEvent`

**EventPublisher** (`infrastructure/messaging/EventPublisher.java`):
- Wrapper around OutboxService for domain use
- Methods:
  - `publishStartSagaCommand(StartSagaCommand)` - to `saga.commands.orchestrator`
  - `publishTraceEvent(DocumentReceivedTraceEvent)` - to `trace.document.received`
- Uses `publishWithRouting()` to include Debezium CDC fields

**Debezium CDC Flow**:
1. Service writes domain state + outbox event in same transaction
2. Debezium connector reads `outbox_events` table via logical replication
3. EventRouter transform routes events to Kafka topics based on `topic` field
4. Events are published with `partition_key` as message key and `headers` as metadata

### REST API

- `POST /api/v1/invoices` (Content-Type: application/xml): Submit document
  - Optional header: `X-Correlation-ID`
  - Returns 202 Accepted with correlationId
- `GET /api/v1/invoices/{id}`: Get document status and validation result

### Database

Flyway migrations in `src/main/resources/db/migration/`:
- `V1__create_incoming_invoices_table.sql`: Main table with JSONB `validation_result` column
- `V2__create_outbox_events_table.sql`: Outbox pattern support
- `V3__add_document_type_column.sql`: Document type column for content-based routing
- `V4__enhance_outbox_for_debezium.sql`: Enhanced outbox schema for Debezium CDC

Entity uses `@JdbcTypeCode(SqlTypes.JSON)` for JSONB mapping.

**Connection Pool**: HikariCP configured with:
- `maximum-pool-size: 10`
- `minimum-idle: 5`
- `connection-timeout: 30000ms`

For high-volume scenarios, consider increasing pool size via environment variables in `application.yml`.

### Debezium CDC Configuration

For the outbox pattern to work, Debezium must be configured:

**Connector Configuration** (`intake-connector.json`):
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

### Monitoring

Actuator endpoints (exposed via `management.endpoints.web.exposure.include`):
- `/actuator/health` - Health check (show-details: always)
- `/actuator/info` - Application info
- `/actuator/metrics` - Micrometer metrics
- `/actuator/prometheus` - Prometheus metrics export

### Logging

Logging levels in `application.yml`:
- `com.wpanther.document.intake`: DEBUG
- `org.apache.camel`: INFO
- `org.hibernate.SQL`: DEBUG

### Validation Resources

XSD schemas and Schematron rules are bundled in the **teda library** at:
`e-tax-invoice-receipt-v2.1/ETDA/data/standard/`

- `*_CrossIndustryInvoice_2p1.xsd` - Main schema per document type
- `*_ReusableAggregateBusinessInformationEntity_2p1.xsd` - RABIE schemas
- `*_Schematron_2p1.sch` - Business rule validation

## Troubleshooting

### Common Issues

**JAXB Unmarshaling Fails with "ClassCastException"**
- Ensure JAXB context uses `.impl` packages (not interface packages)
- Check that teda library is installed: `cd ../../../../teda && mvn clean install`

**Validation Always Passes/Fails**
- Verify XSD schemas and Schematron rules are accessible from teda JAR
- Check logs for `XmlValidationServiceImpl` initialization messages

**Database Connection Errors**
- Verify PostgreSQL is running: `psql -h localhost -U postgres -d intake_db`
- Check Flyway migrations have run: `mvn flyway:info`
- HikariCP pool settings: max 10, min idle 5, timeout 30s (in `application.yml`)

**Kafka Connection Errors**
- Verify Kafka is running: `nc -zv localhost 9092`
- Check topic exists: `kafka-topics --bootstrap-server localhost:9092 --list`

**Flyway Baseline Issues**
- This service uses `baseline-on-migrate: true` for existing database compatibility
- First run will baseline at version 0, then apply migrations

**Events Not Published to Kafka**
- Verify Debezium connector is running: `curl http://localhost:8083/connectors`
- Check connector status: `curl http://localhost:8083/connectors/outbox-connector-intake/status`
- Verify outbox_events table has entries: `psql -d intake_db -c "SELECT * FROM outbox_events ORDER BY created_at DESC LIMIT 5"`
- Check PostgreSQL wal_level=logical

## Testing Patterns

### Invalid Sample Categories

The `src/test/resources/samples/invalid/` directory contains 15+ invalid samples covering these failure modes:

| Category | Examples | What It Tests |
|----------|----------|---------------|
| **Malformed XML** | `malformed-xml.xml` | XML parser error handling |
| **Unknown Namespace** | `unknown-namespace.xml` | Document type detection failure |
| **Missing Required Fields** | `missing-seller-tax-id.xml`, `missing-buyer-tax-id.xml` | XSD validation |
| **Invalid Data** | `invalid-seller-tax-id.xml`, `invalid-buyer-tax-id.xml` | Tax ID format validation |
| **Address Validation** | `missing-seller-address.xml`, `missing-buyer-address.xml` | Schematron business rules |
| **Scheme Version** | `wrong-scheme-version.xml` | Scheme version validation |
| **Business Rules** | `zero-amount.xml`, `negative-vat.xml` | Schematron calculation rules |

### Test Class Conventions

- `*Test` - Unit tests for individual classes (using Mockito)
- `*IntegrationTest` - Full stack tests with Spring context
- Domain tests in `domain/model/` test business logic and state transitions
- Validation tests use real XML samples from `samples/` directory
- Outbox tests in `infrastructure/outbox/` test outbox pattern implementation

## Dependencies

### saga-commons (1.0.0-SNAPSHOT)

This service uses the saga-commons library for outbox pattern support:
- **OutboxEvent**: Domain model with Debezium CDC fields (`topic`, `partitionKey`, `headers`)
- **OutboxEventRepository**: Repository interface for outbox persistence
- **OutboxService**: Service for publishing events to the outbox within transactions
- **OutboxStatus**: Enum for event status (`PENDING`, `PUBLISHED`, `FAILED`)
- **Auto-configuration**: Automatically creates beans when repository implementation is available

**Auto-Configuration**:
- `OutboxEventJpaConfig`: Activated when Spring Data JPA is on the classpath
- Creates `JpaOutboxEventRepository` bean when `SpringDataJpaOutboxEventRepository` is scanned
- Creates `OutboxService` bean when `OutboxEventRepository` is available
- Creates `OutboxCleanupService` bean when `saga.outbox.cleanup.enabled=true`

**Debezium CDC Support**:
The outbox table includes Debezium-specific fields for EventRouter pattern:
```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,        -- Kafka topic for EventRouter
    partition_key VARCHAR(255),         -- Kafka partition key
    payload JSON NOT NULL,
    headers JSON,                       -- Kafka headers as JSON
    status VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
```

### teda Library (1.0.0)

This service depends on the **teda library** (`com.wpanther:thai-etax-invoice`) which provides:
- JAXB-generated classes for all 6 Thai e-Tax document types
- XSD schemas and Schematron rules (bundled in JAR)
- `SchematronValidator` for business rule validation
- `DocumentSchematron` enum for Schematron file mapping
- Package structure examples:
  - `com.wpanther.etax.generated.taxinvoice.rsm` - TaxInvoice root/RSM types
  - `com.wpanther.etax.generated.taxinvoice.ram` - TaxInvoice reusable aggregate types
  - `com.wpanther.etax.generated.common.qdt` - Qualified data types (shared)
  - `com.wpanther.etax.generated.common.udt` - Unqualified data types (shared)
  - `com.wpanther.etax.validation` - Validation classes

### Annotation Processors

**IMPORTANT**: Lombok must be processed before MapStruct (configured in `maven-compiler-plugin`):
1. Lombok generates getters/setters/builders
2. MapStruct uses those generated types for entity↔domain mapping

## Relation to Other Services

This service is the entry point for the Thai e-Tax document processing pipeline using the Saga Orchestrator pattern:

**Upstream:**
- External systems submit documents via REST API or Kafka (`document.intake`)

**Downstream:**
- **orchestrator-service** (port 8093) - Consumes `saga.commands.orchestrator`, orchestrates multi-step processing
- **notification-service** (port 8085) - Consumes `trace.document.received` for lifecycle tracking

**Processing Services** (orchestrated by orchestrator-service):
- invoice-processing-service (port 8082)
- taxinvoice-processing-service (port 8088)
- xml-signing-service (port 8086)
- invoice-pdf-generation-service (port 8090)
- taxinvoice-pdf-generation-service (port 8089)
- pdf-signing-service (port 8087)
- document-storage-service (port 8084)
- ebms-sending-service (port 8092)
