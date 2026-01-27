# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The **Document Intake Service** (port 8081) is a Spring Boot microservice that serves as the gateway for receiving and validating Thai e-Tax XML documents. It performs three-layer validation (well-formedness, XSD schema, Schematron rules) using the teda library, then routes validated documents to document-type-specific Kafka topics.

**Tech Stack**: Java 21, Spring Boot 3.2.5, Apache Camel 4.3, PostgreSQL, Kafka, Eureka

**Package**: `com.wpanther.document.intake`

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
│   └── service/      # XmlValidationService interface
│
├── application/      # Use cases and orchestration
│   ├── controller/   # DocumentIntakeController (REST API)
│   └── service/      # DocumentIntakeService (orchestration)
│
└── infrastructure/   # Framework and external concerns
    ├── persistence/  # IncomingDocumentEntity, JpaIncomingDocumentRepository, IncomingDocumentRepositoryImpl
    ├── validation/   # XmlValidationServiceImpl, DocumentType, ValidationErrorHandler
    └── config/       # CamelConfig (Camel routes)
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
- `markForwarded()`: VALIDATED → FORWARDED
- `markFailed(msg)`: Any → FAILED

### Supported Document Types

The service supports 6 Thai e-Tax document types via the `DocumentType` enum (`infrastructure/validation/DocumentType.java`):

| Type | Namespace | Output Topic |
|------|-----------|--------------|
| `TAX_INVOICE` | `urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2` | `document.received.tax-invoice` |
| `RECEIPT` | `urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2` | `document.received.receipt` |
| `INVOICE` | `urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2` | `document.received.invoice` |
| `DEBIT_CREDIT_NOTE` | `urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2` | `document.received.debit-credit-note` |
| `CANCELLATION_NOTE` | `urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2` | `document.received.cancellation` |
| `ABBREVIATED_TAX_INVOICE` | `urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2` | `document.received.abbreviated` |

Document type is auto-detected from XML namespace URI or root element name.

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

1. **REST Intake** (`direct:invoice-intake`): REST controller → validates → content-based routing to document-type-specific Kafka topic
2. **Kafka Intake** (`kafka:document.intake`): Kafka → validates → content-based routing

Both routes use:
- Dead Letter Channel (`document.intake.dlq`) with 3 retries + exponential backoff
- Content-based routing by `documentType` header to route to the correct output topic

### Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `document.intake` | Consumer | Receive documents from external systems |
| `document.received.tax-invoice` | Producer | Forward validated TaxInvoice documents |
| `document.received.receipt` | Producer | Forward validated Receipt documents |
| `document.received.invoice` | Producer | Forward validated Invoice documents |
| `document.received.debit-credit-note` | Producer | Forward validated DebitCreditNote documents |
| `document.received.cancellation` | Producer | Forward validated CancellationNote documents |
| `document.received.abbreviated` | Producer | Forward validated AbbreviatedTaxInvoice documents |
| `document.intake.dlq` | Producer | Dead letter queue for failed messages |

Topic names are configurable via `app.kafka.topics.*` in `application.yml`.

### Kafka Event Schema

Events published to output topics use this schema (defined in `CamelConfig.createDocumentReceivedEvent()`):
```json
{
  "eventId": "uuid",
  "eventType": "document.received",
  "occurredAt": "2025-12-03T10:30:00Z",
  "version": 1,
  "documentId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentType": "TAX_INVOICE",
  "xmlContent": "<TaxInvoice_CrossIndustryInvoice>...</TaxInvoice_CrossIndustryInvoice>",
  "correlationId": "uuid"
}
```

## Key Implementation Details

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

Entity uses `@JdbcTypeCode(SqlTypes.JSON)` for JSONB mapping.

**Connection Pool**: HikariCP configured with:
- `maximum-pool-size: 10`
- `minimum-idle: 5`
- `connection-timeout: 30000ms`

For high-volume scenarios, consider increasing pool size via environment variables in `application.yml`.

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

- `*Test` - Unit tests for individual classes
- `*IntegrationTest` - Full stack tests with Spring context
- Domain tests in `domain/model/` test business logic and state transitions
- Validation tests use real XML samples from `samples/` directory

## Dependencies

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

This service is part of the Thai e-Tax document processing pipeline:
- Forwards validated documents to **Document Processing Service** (port 8082)
- Publishes events consumed by **Notification Service** (port 8085)
- Part of the 7-microservice event-driven architecture
