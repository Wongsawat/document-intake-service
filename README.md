# Invoice Intake Service

Gateway microservice for receiving and validating XML invoices in the Invoice Processing System.

## Overview

The Invoice Intake Service is the **first checkpoint** for XML invoices entering the system. It:

- ✅ **Receives** XML invoices via REST API and Kafka
- ✅ **Validates** XML against XSD schema and Schematron business rules (using teda library)
- ✅ **Detects** document type from XML namespace
- ✅ **Extracts** invoice metadata
- ✅ **Routes** validated invoices to document-type-specific Kafka topics

## Supported Document Types

| Document Type | Description |
|---------------|-------------|
| TaxInvoice | Full tax invoice (ใบกำกับภาษี) |
| Receipt | Receipt/tax invoice combined (ใบเสร็จรับเงิน/ใบกำกับภาษี) |
| Invoice | Commercial invoice |
| DebitCreditNote | Debit/credit note (ใบเพิ่มหนี้/ใบลดหนี้) |
| CancellationNote | Cancellation document |
| AbbreviatedTaxInvoice | Abbreviated tax invoice (ใบกำกับภาษีอย่างย่อ) |

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Integration | Apache Camel 4.3 |
| Database | PostgreSQL |
| Messaging | Apache Kafka |
| Service Discovery | Netflix Eureka |
| Database Migration | Flyway |

### Domain Model

**Aggregate Root:**
- `IncomingInvoice` - Manages invoice lifecycle (RECEIVED → VALIDATING → VALIDATED/INVALID → FORWARDED)

**Value Objects:**
- `ValidationResult` - Contains errors and warnings
- `InvoiceStatus` - Enum for tracking lifecycle

## API Endpoints

### Submit Invoice

```http
POST /api/v1/invoices
Content-Type: application/xml
X-Correlation-ID: optional-correlation-id

<TaxInvoice_CrossIndustryInvoice>...</TaxInvoice_CrossIndustryInvoice>

Response: 202 Accepted
{
  "message": "Invoice submitted for processing",
  "correlationId": "uuid"
}
```

### Get Invoice Status

```http
GET /api/v1/invoices/{id}

Response: 200 OK
{
  "id": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentType": "TAX_INVOICE",
  "status": "FORWARDED",
  "receivedAt": "2025-12-03T10:30:00",
  "validationResult": {
    "valid": true,
    "errors": [],
    "warnings": []
  }
}
```

## Apache Camel Routes

### Route 1: REST Intake
```
POST /api/v1/invoices → direct:invoice-intake → Validation → Content-Based Routing → Kafka
```

### Route 2: Kafka Intake
```
Kafka (invoice.intake) → Validation → Content-Based Routing → Kafka
```

### Content-Based Routing

Documents are routed to type-specific topics based on XML namespace:

| Document Type | Output Topic |
|---------------|--------------|
| TaxInvoice | `invoice.received.tax-invoice` |
| Receipt | `invoice.received.receipt` |
| Invoice | `invoice.received.invoice` |
| DebitCreditNote | `invoice.received.debit-credit-note` |
| CancellationNote | `invoice.received.cancellation` |
| AbbreviatedTaxInvoice | `invoice.received.abbreviated` |

### Error Handling
- 3 retry attempts with exponential backoff
- Failed messages → Dead Letter Queue (`invoice.intake.dlq`)

## Kafka Integration

### Consumed Topics
- `invoice.intake` - Invoices from external systems

### Published Topics (Content-Based Routing)

| Topic | Document Type |
|-------|---------------|
| `invoice.received.tax-invoice` | TaxInvoice |
| `invoice.received.receipt` | Receipt |
| `invoice.received.invoice` | Invoice |
| `invoice.received.debit-credit-note` | DebitCreditNote |
| `invoice.received.cancellation` | CancellationNote |
| `invoice.received.abbreviated` | AbbreviatedTaxInvoice |
| `invoice.intake.dlq` | Failed messages |

### Event Schema
```json
{
  "eventId": "uuid",
  "eventType": "invoice.received",
  "occurredAt": "2025-12-03T10:30:00Z",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentType": "TAX_INVOICE",
  "xmlContent": "<TaxInvoice_CrossIndustryInvoice>...</TaxInvoice_CrossIndustryInvoice>",
  "correlationId": "uuid"
}
```

## Database Schema

### incoming_invoices Table
- `id` (UUID) - Primary key
- `invoice_number` (VARCHAR) - Unique invoice identifier
- `document_type` (VARCHAR) - Document type enum
- `xml_content` (TEXT) - Full XML document
- `source` (VARCHAR) - Origin (REST/KAFKA)
- `correlation_id` (VARCHAR) - Request correlation
- `status` (VARCHAR) - Lifecycle status
- `validation_result` (JSONB) - Validation errors/warnings
- Timestamps: `received_at`, `processed_at`, `created_at`, `updated_at`

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `intake_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `KAFKA_BROKERS` | Kafka bootstrap servers | `localhost:9092` |
| `EUREKA_URL` | Eureka server URL | `http://localhost:8761/eureka/` |

## Running the Service

### Prerequisites
1. PostgreSQL database running
2. Kafka broker running
3. teda library installed (`cd ../../../../teda && mvn clean install`)
4. Eureka server (optional)

### Build
```bash
mvn clean package
```

### Run Locally
```bash
export DB_HOST=localhost
export DB_PASSWORD=yourpassword
export KAFKA_BROKERS=localhost:9092

mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t invoice-intake-service:latest .

docker run -p 8081:8081 \
  -e DB_HOST=postgres \
  -e DB_PASSWORD=postgres \
  -e KAFKA_BROKERS=kafka:29092 \
  invoice-intake-service:latest
```

## Integration with teda Library

The service uses the **teda library** (`com.wpanther:thai-etax-invoice`) for complete XML validation:

### Three-Layer Validation
1. **XML Well-Formedness** - Validated during JAXB unmarshaling
2. **XSD Schema Validation** - Validates against Thai e-Tax XSD schemas (TaxInvoice_2p1.xsd, etc.)
3. **Schematron Business Rules** - Validates against ETDA business rules (TaxInvoice_Schematron_2p1.sch)

### JAXB-Based Processing
- **Type-safe unmarshaling** - XML converted to strongly-typed JAXB objects
- **Document type auto-detection** - Detected from namespace URI or unmarshaled class
- **Invoice number extraction** - Extracted from ExchangedDocument/ID element
- **Error collection** - ValidationEventHandler collects all errors (not fail-fast)

### Implementation Notes
**Critical**: The teda library uses an interface/implementation pattern. JAXB contexts MUST be initialized with `.impl` packages:

```java
// Correct - Uses implementation packages
"com.wpanther.etax.generated.taxinvoice.rsm.impl:" +
"com.wpanther.etax.generated.taxinvoice.ram.impl:" +
"com.wpanther.etax.generated.common.qdt.impl:" +
"com.wpanther.etax.generated.common.udt.impl"
```

### Test Coverage
- **184 tests** covering all validation scenarios
- Integration tests use real XML samples from `src/test/resources/samples/`
- Tests verify JAXB unmarshaling, XSD validation, and Schematron rules

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Health check
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics

### Key Metrics
- `invoice_received_total` - Total invoices received
- `invoice_validation_duration_seconds` - Validation time
- `invoice_validation_failures_total` - Validation failures

## Project Structure

```
src/main/java/com/invoice/intake/
├── InvoiceIntakeServiceApplication.java
├── domain/
│   ├── model/              # IncomingInvoice aggregate, value objects
│   ├── repository/         # Repository interfaces
│   └── service/            # XmlValidationService
├── application/
│   ├── controller/         # REST controllers
│   └── service/            # InvoiceIntakeService
└── infrastructure/
    ├── persistence/        # JPA entities, repositories
    ├── config/             # Camel routes, Kafka config
    └── validation/         # XML validation implementation
```

## Recommended Enhancements

1. **Outbox Pattern** - Reliable event publishing (table exists, needs implementation)
2. **Rate Limiting** - Prevent overload
3. **API Authentication** - Secure endpoints
4. **Performance Testing** - Load test for high-volume scenarios

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
