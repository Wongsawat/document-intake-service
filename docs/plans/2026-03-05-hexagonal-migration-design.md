# Hexagonal Architecture Migration Design
**Service:** document-intake-service
**Date:** 2026-03-05
**Status:** Approved

---

## Context

The document-intake-service currently uses a DDD layered architecture (`domain/`, `application/`, `infrastructure/`). While repository and service interfaces exist, several architecture violations prevent the domain from being framework-free:

- `IncomingDocument` (domain aggregate) imports `DocumentType` from `infrastructure/validation/`
- `XmlValidationService` (domain interface) returns `DocumentType` from `infrastructure/`
- `DocumentIntakeService` (application) imports `EventPublisher` directly from `infrastructure/messaging/` instead of through a port
- No explicit inbound port interfaces — the controller couples to the concrete application service

**Goal:** Migrate to Hexagonal Architecture (Ports & Adapters) so the domain is pure Java with zero framework/library dependencies, and every external concern is reached through an explicit port.

---

## Decisions

| Question | Decision |
|---|---|
| Domain purity | `DocumentType` becomes a pure 6-value enum; all teda/JAXB references move to adapter layer |
| Migration strategy | **Hybrid (two-commit)**: Commit 1 = domain cleanup; Commit 2 = package restructuring |
| Package naming | `adapter/in/` (driving) and `adapter/out/` (driven) |
| Inbound ports | Explicit use-case interfaces in `domain/port/in/`; application service implements them |

---

## Target Package Structure

```
com.wpanther.document.intake/
│
├── domain/                          # Pure Java — zero Spring/teda/JPA imports
│   ├── model/
│   │   ├── IncomingDocument.java    (aggregate root — logic unchanged)
│   │   ├── DocumentStatus.java
│   │   ├── DocumentType.java        ← MOVED from infrastructure/validation/
│   │   │                              pure 6-value enum, no teda/JAXB refs
│   │   └── ValidationResult.java
│   ├── port/
│   │   ├── in/                      # Inbound (driving) ports — use case interfaces
│   │   │   ├── SubmitDocumentUseCase.java
│   │   │   └── GetDocumentUseCase.java
│   │   └── out/                     # Outbound (driven) ports
│   │       ├── DocumentRepository.java        ← MOVED from domain/repository/
│   │       ├── XmlValidationPort.java         ← RENAMED from domain/service/XmlValidationService
│   │       └── DocumentEventPublisher.java    ← NEW: replaces direct EventPublisher dependency
│   └── event/
│       ├── StartSagaCommand.java
│       ├── DocumentReceivedTraceEvent.java
│       └── EventStatus.java
│
├── application/
│   └── service/
│       └── DocumentIntakeApplicationService.java
│           implements SubmitDocumentUseCase, GetDocumentUseCase
│           depends only on: DocumentRepository, XmlValidationPort, DocumentEventPublisher
│
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── DocumentIntakeController.java
│   │   │       depends on: SubmitDocumentUseCase, GetDocumentUseCase (interfaces only)
│   │   └── messaging/
│   │       └── KafkaDocumentConsumer.java     ← extracted from CamelConfig
│   │           depends on: SubmitDocumentUseCase (interface only)
│   └── out/
│       ├── persistence/
│       │   ├── JpaDocumentRepository.java     implements DocumentRepository
│       │   ├── IncomingDocumentEntity.java
│       │   ├── SpringDataIncomingDocumentRepository.java
│       │   └── outbox/                         (unchanged internals)
│       ├── validation/
│       │   ├── TedaXmlValidationAdapter.java  implements XmlValidationPort
│       │   ├── TedaDocumentType.java           holds teda/JAXB refs, maps ↔ domain DocumentType
│       │   └── InvoiceNumberExtractor*.java
│       └── messaging/
│           └── OutboxEventPublisher.java       implements DocumentEventPublisher
│
└── infrastructure/
    └── config/                       # Spring Boot plumbing only, no business logic
        ├── CamelConfig.java
        ├── OutboxConfig.java
        ├── SecurityConfig.java
        ├── RateLimitConfig.java
        ├── RateLimitProperties.java
        ├── ValidationProperties.java
        └── SchemaPathConfig.java
```

---

## Port Contracts

### Inbound ports (`domain/port/in/`)

```java
public interface SubmitDocumentUseCase {
    IncomingDocument submitDocument(String xmlContent, String source, String correlationId);
}

public interface GetDocumentUseCase {
    IncomingDocument getDocument(UUID id);
}
```

### Outbound ports (`domain/port/out/`)

```java
public interface DocumentRepository {
    IncomingDocument save(IncomingDocument document);
    Optional<IncomingDocument> findById(UUID id);
    Optional<IncomingDocument> findByDocumentNumber(String documentNumber);
    List<IncomingDocument> findByStatus(DocumentStatus status);
    boolean existsByDocumentNumber(String documentNumber);
}

public interface XmlValidationPort {
    ValidationResult validate(String xmlContent);
    String extractDocumentNumber(String xmlContent);  // renamed from extractInvoiceNumber
    DocumentType extractDocumentType(String xmlContent);
}

public interface DocumentEventPublisher {
    void publishStartSagaCommand(StartSagaCommand command);
    void publishTraceEvent(DocumentReceivedTraceEvent event);
}
```

### Domain `DocumentType` (pure enum)

```java
public enum DocumentType {
    TAX_INVOICE, RECEIPT, INVOICE,
    DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE
}
```

All teda/JAXB references (context paths, namespace URIs, Schematron mapping, extractor strategies) move to `adapter/out/validation/TedaDocumentType.java`.

---

## Data Flow

### REST path

```
HTTP POST /api/v1/documents
  → DocumentIntakeController           (adapter/in/web)
      calls SubmitDocumentUseCase
  → DocumentIntakeApplicationService   (application/service)
      XmlValidationPort.extractDocumentNumber()        → TedaXmlValidationAdapter
      XmlValidationPort.extractDocumentType()          → TedaXmlValidationAdapter
      DocumentRepository.existsByDocumentNumber()      → JpaDocumentRepository
      DocumentRepository.save()                        → JpaDocumentRepository
      DocumentEventPublisher.publishTraceEvent()       → OutboxEventPublisher
      XmlValidationPort.validate()                     → TedaXmlValidationAdapter
      DocumentRepository.save()                        → JpaDocumentRepository
      DocumentEventPublisher.publishStartSagaCommand() → OutboxEventPublisher
      DocumentRepository.save()                        → JpaDocumentRepository
  ← IncomingDocument
  ← 202 Accepted { correlationId }
```

### Kafka path

```
Kafka: document.intake
  → CamelConfig (infrastructure/config) wires route to KafkaDocumentConsumer
  → KafkaDocumentConsumer              (adapter/in/messaging)
      calls SubmitDocumentUseCase
      (identical domain flow as REST path)
```

### CamelConfig after migration

`CamelConfig` retains ownership of DLQ config, retry policy, and throttle config. It delegates to
`KafkaDocumentConsumer` for Kafka messages. Zero business logic remains inside `CamelConfig`.

---

## Error Handling

No observable behaviour changes. Error mapping stays identical:

| Exception | HTTP response |
|---|---|
| `IllegalArgumentException` | 400 Bad Request |
| `IllegalStateException` | 409 Conflict |
| `ConstraintViolationException` | 400 Bad Request |
| `HandlerMethodValidationException` | 400 Bad Request |
| Anything else | 500 Internal Server Error |

`CamelExecutionException` unwrapping stays in `DocumentIntakeController` for the `direct:document-intake`
route. Kafka error handling (DLQ, 3 retries, exponential backoff) stays in `CamelConfig` — unchanged.

---

## Testing Strategy

All 31 existing test classes are relocated to match the new package structure. No tests deleted.

### Test relocations (Commit 2 — mechanical)

| Old path | New path |
|---|---|
| `application/controller/DocumentIntakeControllerTest` | `adapter/in/web/DocumentIntakeControllerTest` |
| `application/service/DocumentIntakeServiceTest` | `application/service/DocumentIntakeApplicationServiceTest` |
| `infrastructure/persistence/IncomingDocumentRepositoryImplTest` | `adapter/out/persistence/JpaDocumentRepositoryTest` |
| `infrastructure/messaging/EventPublisherTest` | `adapter/out/messaging/OutboxEventPublisherTest` |
| `infrastructure/validation/XmlValidationServiceImplTest` | `adapter/out/validation/TedaXmlValidationAdapterTest` |
| `infrastructure/validation/DocumentTypeTest` | split: `domain/model/DocumentTypeTest` + `adapter/out/validation/TedaDocumentTypeTest` |

### New tests (Commit 1 — domain cleanup)

- `domain/model/DocumentTypeTest` — verifies pure enum has exactly 6 values, no framework imports
- `adapter/out/validation/TedaDocumentTypeTest` — verifies `TedaDocumentType` ↔ `DocumentType` mapping round-trip

### Testing patterns per layer

| Layer | Test style | Mocks |
|---|---|---|
| `domain/` | Plain JUnit, no Spring | Nothing |
| `application/service/` | Plain JUnit + Mockito | All 3 outbound ports |
| `adapter/in/web/` | `@WebMvcTest` | `SubmitDocumentUseCase`, `GetDocumentUseCase` |
| `adapter/in/messaging/` | Plain JUnit + Mockito | `SubmitDocumentUseCase` |
| `adapter/out/persistence/` | `@DataJpaTest` + H2 | — |
| `adapter/out/validation/` | Plain JUnit | teda library (real) |
| `adapter/out/messaging/` | Plain JUnit + Mockito | `OutboxService` |

JaCoCo 90% line coverage requirement unchanged.

---

## Migration Commits

### Commit 1 — Domain cleanup

1. Add `domain/model/DocumentType.java` — pure 6-value enum
2. Create `domain/port/in/SubmitDocumentUseCase.java`
3. Create `domain/port/in/GetDocumentUseCase.java`
4. Move `domain/repository/IncomingDocumentRepository.java` → `domain/port/out/DocumentRepository.java`
5. Move `domain/service/XmlValidationService.java` → `domain/port/out/XmlValidationPort.java`
   - Rename method `extractInvoiceNumber` → `extractDocumentNumber`
   - Return type uses domain `DocumentType`
6. Create `domain/port/out/DocumentEventPublisher.java`
7. Remove `domain/repository/` and `domain/service/` packages
8. Fix `IncomingDocument.java` — replace `infrastructure/validation/DocumentType` import with `domain/model/DocumentType`
9. Add `domain/model/DocumentTypeTest` unit test

**Exit criterion:** all existing tests pass.

### Commit 2 — Package restructuring

1. `infrastructure/persistence/` → `adapter/out/persistence/`; rename `IncomingDocumentRepositoryImpl` → `JpaDocumentRepository`
2. `infrastructure/validation/` → `adapter/out/validation/`; rename `XmlValidationServiceImpl` → `TedaXmlValidationAdapter`; extract `TedaDocumentType` from old `DocumentType`
3. `infrastructure/messaging/` → `adapter/out/messaging/`; rename `EventPublisher` → `OutboxEventPublisher`; implement `DocumentEventPublisher` port
4. `infrastructure/health/` → `adapter/out/health/`
5. `application/controller/` → `adapter/in/web/`
6. Extract `KafkaDocumentConsumer` from `CamelConfig` → `adapter/in/messaging/`
7. Update `DocumentIntakeApplicationService` to implement `SubmitDocumentUseCase`, `GetDocumentUseCase`; replace `EventPublisher` dep with `DocumentEventPublisher` port; rename from `DocumentIntakeService`
8. Update `CamelConfig` to delegate to `KafkaDocumentConsumer`
9. Relocate all test classes to match new package structure; add `TedaDocumentTypeTest`

**Exit criterion:** all tests pass; JaCoCo ≥ 90%.

---

## What Does NOT Change

- `IncomingDocument` business logic and state machine
- Orchestration flow in the application service (rename only)
- Outbox pattern internals (`OutboxEventEntity`, `SpringDataOutboxRepository`, `JpaOutboxEventRepository`)
- Flyway migrations and DB schema
- Kafka topic names and event payloads
- REST API surface (`/api/v1/documents`)
- Spring Boot configuration properties
- External behaviour observable by consumers
