# AGENTS.md

Build and test commands for agentic coding agents working in this repository.

## Migration Note

This service was renamed from **invoice-intake-service** to **document-intake-service** to support multiple Thai e-Tax document types (not just invoices). Legacy code and database naming remain from the migration:

- **Table naming**: `incoming_invoices` table (legacy name) - domain uses `IncomingDocument`, entity uses `IncomingDocumentEntity`
- **Method naming**: `submitInvoice()` method handles ALL document types (TaxInvoice, Receipt, Invoice, DebitCreditNote, CancellationNote, AbbreviatedTaxInvoice) - name is historical
- **Legacy Kafka topics** in `application.yml` are kept for reference but no longer used - all documents route via `saga.commands.orchestrator`
- **Flyway migrations**: V1 is a consolidated baseline for development; production should use incremental migrations (V1-V6)

When working with this codebase, use the domain model (`IncomingDocument`) and follow the current architecture, not the legacy naming.

## Build, Test, and Verify Commands

```bash
# Install teda library dependency (required before building)
cd ../../../../teda && mvn clean install

# Build the service
mvn clean package

# Run all tests
mvn test

# Run single test method (use fully qualified test name)
mvn test -Dtest=DocumentIntakeServiceTest#testSubmitInvoiceWithValidXml

# Run all tests in a class
mvn test -Dtest=XmlValidationServiceImplTest

# Run tests with specific log level
mvn test -Dtest=CamelConfigTest -Dlogging.level.com.wpanther.document.intake=TRACE

# Run integration tests only (tests ending with IntegrationTest)
mvn test -Dtest=*IntegrationTest

# Run CDC integration tests (requires external containers)
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
cd services/document-intake-service
mvn test -Dtest="*CdcIntegrationTest,*TableIntegrationTest" -Dspring.profiles.active=cdc-test

# Code coverage (90% line coverage required per package)
mvn verify

# Database migrations
mvn flyway:migrate
mvn flyway:info

# Run locally (requires PostgreSQL, Kafka)
mvn spring-boot:run

# Run with Docker test environment (different ports)
DB_PORT=5433 KAFKA_BROKERS=localhost:9093 mvn spring-boot:run
```

## Code Style Guidelines

### Import Organization
- Static imports at top (e.g., `import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;`)
- Third-party imports (jakarta.*, org.springframework.*, org.apache.camel.*, etc.)
- Internal imports (`com.wpanther.*`)
- Blank line between import groups

### Naming Conventions
- Classes: PascalCase (e.g., `IncomingDocument`, `DocumentIntakeService`)
- Methods: camelCase starting with verb (e.g., `submitInvoice()`, `markValidated()`)
- Constants: UPPER_SNAKE_CASE (e.g., `TEST_INVOICE_NUMBER`)
- Private fields: camelCase (e.g., `invoiceNumber`, `xmlContent`)
- Packages: lowercase with dots (e.g., `com.wpanther.document.intake.domain.model`)

### Domain Objects
- Use private Builder pattern (not Lombok @Builder)
- Encapsulate state with private final fields where appropriate
- Validate business invariants in constructor (e.g., `validateInvariant()`)
- State transition methods (e.g., `startValidation()`, `markValidated()`) throw `IllegalStateException` for invalid transitions
- Use `Objects.requireNonNull()` for null checks with descriptive messages

### JPA Entities
- Use Lombok annotations: `@Entity`, `@Table`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- Define indexes in `@Table` annotation
- Use `@PrePersist` for field initialization (UUID, defaults, timestamps)
- Use `@Enumerated(EnumType.STRING)` for enum fields
- Use `@CreationTimestamp` and `@UpdateTimestamp` for audit fields

### Event/DTO Classes
- Use `@Getter`, `@Builder`, `@Jacksonized`, `@JsonIgnoreProperties(ignoreUnknown = true)`
- Extend `IntegrationEvent` for saga events (from saga-commons)
- Provide two constructors: `@Builder` for creation, `@JsonCreator` for deserialization
- Use `@JsonProperty` on constructor parameters
- Add field-level Javadoc

### Service Layer
- Annotate with `@Service` and `@Slf4j`
- Use constructor injection (final fields)
- Annotate transactional methods with `@Transactional`
- Use `@Transactional(readOnly = true)` for read-only operations
- Log important operations at INFO level, debug at DEBUG level

### Testing
- Use JUnit 5: `@Test`, `@DisplayName`, `@BeforeEach`, `@ParameterizedTest`
- Use AssertJ assertions: `assertThat()`, `assertThatThrownBy()`
- Use `@SpringBootTest` for integration tests with H2 database
- Define constants at class level for test data (e.g., `TEST_INVOICE_NUMBER`)
- Use text blocks for XML content (JDK 15+)
- Organize tests into categories with comments (e.g., `// ==================== Builder Pattern Tests ====================`)

### Error Handling
- Use `IllegalArgumentException` for invalid input (e.g., null/blank required fields)
- Use `IllegalStateException` for invalid state transitions
- Use `NullPointerException` when null violates contract (via `Objects.requireNonNull()`)
- Log errors at ERROR level with context

### Logging
- Use SLF4J with `@Slf4j` annotation
- DEBUG: Detailed flow information
- INFO: Key operations (document created, validation result)
- WARN: Recoverable issues (fallback behavior)
- ERROR: Unexpected exceptions

### DDD Layer Structure
- `domain/`: Core business logic (no framework dependencies)
- `application/`: Use cases and orchestration
- `infrastructure/`: Framework and external concerns
- Keep dependencies directional: infrastructure → application → domain

### XML Processing (teda Library)
- JAXB contexts MUST use `.impl` packages (not interface packages)
- Use `ValidationErrorHandler` to collect errors instead of failing fast
- Handle `JAXBElement` wrapper during unmarshaling
- Auto-detect document type from unmarshaled class or namespace

### Code Quality
- No inline comments unless explicitly asked
- Use meaningful variable/method names instead of comments
- Keep methods focused and under 30 lines when possible
- Prefer immutability (final fields, private constructors)
- Use `java.time` API for dates/times (LocalDateTime, Instant)
- Use UUID for entity IDs
