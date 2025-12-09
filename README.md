# Invoice Intake Service

Gateway microservice for receiving and validating XML invoices in the Invoice Processing System.

## Overview

The Invoice Intake Service is the **first checkpoint** for XML invoices entering the system. It:

- ✅ **Receives** XML invoices via REST API and Kafka
- ✅ **Validates** XML against XSD schema (using teda library)
- ✅ **Extracts** invoice metadata
- ✅ **Publishes** validated invoices to downstream services

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

<Invoice>...</Invoice>

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
POST /api/v1/invoices → direct:invoice-intake → Validation → Kafka (invoice.received)
```

### Route 2: Kafka Intake
```
Kafka (invoice.intake) → Validation → Kafka (invoice.received)
```

### Error Handling
- 3 retry attempts with exponential backoff
- Failed messages → Dead Letter Queue (`invoice.intake.dlq`)

## Kafka Integration

### Consumed Topics
- `invoice.intake` - Invoices from external systems

### Published Topics
- `invoice.received` - Validated invoices for processing service

### Event Schema
```json
{
  "eventId": "uuid",
  "eventType": "invoice.received",
  "occurredAt": "2025-12-03T10:30:00Z",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "xmlContent": "<Invoice>...</Invoice>",
  "correlationId": "uuid"
}
```

## Database Schema

### incoming_invoices Table
- `id` (UUID) - Primary key
- `invoice_number` (VARCHAR) - Unique invoice identifier
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
3. teda library installed
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

The `XmlValidationService` implementation requires integration with teda library for:

- XSD schema validation
- JAXB parsing
- Invoice number extraction
- Database-backed code list validation

**Note**: The implementation needs to be completed in `XmlValidationServiceImpl`.

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

## Next Steps

### Required Implementation
1. **XmlValidationServiceImpl** - Complete integration with teda library
2. **Repository Mapper** - Domain ↔ Entity conversion

### Recommended Enhancements
1. **Outbox Pattern** - Reliable event publishing
2. **Rate Limiting** - Prevent overload
3. **API Authentication** - Secure endpoints
4. **Comprehensive Tests** - Unit and integration tests

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
