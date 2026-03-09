# Hexagonal Architecture Migration Design (Canonical Alignment)

**Date:** 2026-03-09
**Service:** document-intake-service (port 8081)
**Type:** Pure refactor — package rename + relocation, no logic changes
**Strategy:** Phase-by-phase incremental (one commit per logical group, tests green after each)

---

## Context

The document-intake-service already implements a mature hexagonal architecture with `infrastructure/adapter/`, `infrastructure/config/`, and `domain/repository/` in correct positions. This migration completes the final alignment with the **canonical layout** established by the other services (invoice-pdf, taxinvoice-pdf, ebms-sending, notification, orchestrator):

- `domain/` ← `application/` ← `infrastructure/` (strict dependency rule)
- `application/usecase/` for both use-case interfaces and their implementation
- `application/port/out/` for non-domain outbound ports
- `application/dto/event/` for Kafka wire DTOs (not domain events)
- `infrastructure/config/` with concern-based sub-packages

**Remaining gaps:**

| Current | Target | Change |
|---|---|---|
| `application/port/in/` (2 interfaces) | `application/usecase/` | Rename package |
| `application/service/` (1 class) | `application/usecase/` | Merge into same package |
| `domain/event/port/DocumentEventPublisher` | `application/port/out/` | Port belongs in application, not domain |
| `domain/event/StartSagaCommand` | `application/dto/event/` | Kafka wire DTO, not domain event |
| `domain/event/DocumentReceivedTraceEvent` | `application/dto/event/` | Kafka wire DTO |
| `domain/event/EventStatus` | `application/dto/event/` | Used only by above DTOs |
| `infrastructure/config/` flat (8 classes) | `infrastructure/config/<concern>/` | Sub-package split |

---

## Target Package Structure

```
com.wpanther.document.intake/
├── domain/
│   ├── model/                              # unchanged
│   │   ├── IncomingDocument.java
│   │   ├── DocumentStatus.java
│   │   ├── DocumentType.java
│   │   └── ValidationResult.java
│   ├── repository/                         # unchanged (already canonical)
│   │   └── DocumentRepository.java
│   └── exception/                          # unchanged
│       └── ValidationResultSerializationException.java
│   # domain/event/ FULLY REMOVED — no true domain events remain
│
├── application/
│   ├── usecase/                            # MERGED from application/port/in/ + application/service/
│   │   ├── SubmitDocumentUseCase.java      # MOVED from application/port/in/
│   │   ├── GetDocumentUseCase.java         # MOVED from application/port/in/
│   │   └── DocumentIntakeApplicationService.java  # MOVED from application/service/
│   ├── port/out/
│   │   ├── XmlValidationPort.java          # unchanged
│   │   └── DocumentEventPublisher.java     # MOVED from domain/event/port/
│   └── dto/
│       └── event/                          # NEW sub-package
│           ├── StartSagaCommand.java       # MOVED from domain/event/
│           ├── DocumentReceivedTraceEvent.java  # MOVED from domain/event/
│           └── EventStatus.java            # MOVED from domain/event/
│
└── infrastructure/
    ├── adapter/                            # UNCHANGED — already canonical
    │   ├── in/
    │   │   ├── web/DocumentIntakeController.java
    │   │   └── metrics/DocumentIntakeMetrics.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── IncomingDocumentEntity.java
    │       │   ├── JpaDocumentRepository.java
    │       │   ├── JpaIncomingDocumentRepository.java
    │       │   └── outbox/
    │       │       ├── OutboxEventEntity.java
    │       │       ├── SpringDataOutboxRepository.java
    │       │       └── JpaOutboxEventRepository.java
    │       ├── messaging/EventPublisher.java
    │       ├── validation/
    │       │   ├── TedaXmlValidationAdapter.java
    │       │   ├── TedaDocumentType.java
    │       │   ├── InvoiceNumberExtractor.java
    │       │   ├── InvoiceNumberExtractorStrategies.java
    │       │   └── ValidationErrorHandler.java
    │       └── health/OutboxHealthIndicator.java
    └── config/
        ├── camel/
        │   └── CamelConfig.java            # MOVED from config/
        ├── outbox/
        │   └── OutboxConfig.java           # MOVED from config/
        ├── openapi/
        │   └── OpenApiConfig.java          # MOVED from config/
        ├── ratelimit/
        │   ├── RateLimitConfig.java        # MOVED from config/
        │   └── RateLimitProperties.java    # MOVED from config/
        ├── validation/
        │   ├── SchemaPathConfig.java       # MOVED from config/
        │   └── ValidationProperties.java   # MOVED from config/
        └── security/
            └── SecurityConfig.java         # MOVED from config/
```

---

## Component Design

### `application/usecase/` Merge

`application/port/in/` is renamed to `application/usecase/` and `application/service/` is merged into it. No interface changes — only package declarations:

- `SubmitDocumentUseCase` and `GetDocumentUseCase` move as-is
- `DocumentIntakeApplicationService` moves as-is (implements both interfaces, same package)
- `DocumentIntakeController` injects the two use-case interfaces — only its `import` statements change

### `DocumentEventPublisher` Relocation

`domain/event/port/DocumentEventPublisher` is an outbound port used by the application service. Domain packages must not contain ports (ports are application-layer contracts). After move to `application/port/out/`:

- `DocumentIntakeApplicationService` imports `application.port.out.DocumentEventPublisher` ✓
- `infrastructure/adapter/out/messaging/EventPublisher` implements `application.port.out.DocumentEventPublisher` ✓

### Kafka DTO Relocation

`StartSagaCommand`, `DocumentReceivedTraceEvent`, and `EventStatus` move from `domain/event/` to `application/dto/event/`. They are Kafka wire DTOs built by `DocumentIntakeApplicationService` and serialized by `EventPublisher` — not pure domain events.

After the move, `domain/event/` is completely empty and the directory is deleted.

**Import chain after move:**
- `DocumentIntakeApplicationService` (in `application/usecase/`) → imports `application.dto.event.*` ✓
- `EventPublisher` (in `infrastructure/adapter/out/messaging/`) → imports `application.dto.event.*` ✓

### Config Sub-Package Split

| Class(es) | Sub-package | Rationale |
|---|---|---|
| `CamelConfig` | `infrastructure/config/camel/` | Camel route beans |
| `OutboxConfig` | `infrastructure/config/outbox/` | Outbox CDC wiring |
| `OpenApiConfig` | `infrastructure/config/openapi/` | Swagger/OpenAPI beans |
| `RateLimitConfig`, `RateLimitProperties` | `infrastructure/config/ratelimit/` | Rate limiting pair |
| `SchemaPathConfig`, `ValidationProperties` | `infrastructure/config/validation/` | Validation pair |
| `SecurityConfig` | `infrastructure/config/security/` | Spring Security filter chain |

---

## Dependency Rules

| Package | May import from | Must NOT import from |
|---|---|---|
| `domain/` | stdlib, Lombok, saga-commons | application/, infrastructure/ |
| `domain/repository/` | `domain/model/` | application/, infrastructure/ |
| `application/usecase/` | `domain/`, `application/port/out/`, `application/dto/` | infrastructure/ |
| `application/port/out/` | `domain/model/`, `application/dto/` | infrastructure/ |
| `application/dto/event/` | stdlib, Jackson | domain/, infrastructure/ |
| `infrastructure/adapter/in/` | `application/usecase/`, `application/dto/` | `infrastructure/adapter/out/` directly |
| `infrastructure/adapter/out/` | `application/port/out/`, `domain/`, `application/dto/` | `infrastructure/adapter/in/` |
| `infrastructure/config/` | everything (Spring wiring — allowed) | — |

---

## Data Flow

### Inbound: REST Submission
```
POST /api/v1/documents
  → infrastructure/adapter/in/web/DocumentIntakeController
  → SubmitDocumentUseCase (interface in application/usecase/)
  → DocumentIntakeApplicationService
      ├── domain/repository/DocumentRepository (save IncomingDocument)
      ├── application/port/out/XmlValidationPort (3-layer XML validation)
      └── application/port/out/DocumentEventPublisher (publish events)
  → outbox_events table (Debezium CDC)
  → saga.commands.orchestrator + trace.document.received
```

### Inbound: Kafka Submission
```
document.intake → infrastructure/config/camel/CamelConfig (Camel route)
  → SubmitDocumentUseCase → DocumentIntakeApplicationService (same path)
```

### Outbound Event Publishing
```
DocumentIntakeApplicationService
  builds: application/dto/event/StartSagaCommand
  builds: application/dto/event/DocumentReceivedTraceEvent
  calls:  application/port/out/DocumentEventPublisher
            ↓ implemented by
          infrastructure/adapter/out/messaging/EventPublisher
            ↓ writes to outbox via OutboxService (saga-commons)
          outbox_events table → Debezium CDC → Kafka topics
```

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `domain.event.StartSagaCommand` | `application.dto.event.StartSagaCommand` |
| `domain.event.DocumentReceivedTraceEvent` | `application.dto.event.DocumentReceivedTraceEvent` |
| `domain.event.EventStatus` | `application.dto.event.EventStatus` |
| `domain.event.port.DocumentEventPublisher` | `application.port.out.DocumentEventPublisher` |
| `application.port.in.SubmitDocumentUseCase` | `application.usecase.SubmitDocumentUseCase` |
| `application.port.in.GetDocumentUseCase` | `application.usecase.GetDocumentUseCase` |
| `application.service.DocumentIntakeApplicationService` | `application.usecase.DocumentIntakeApplicationService` |
| `infrastructure.config.CamelConfig` | `infrastructure.config.camel.CamelConfig` |
| `infrastructure.config.OutboxConfig` | `infrastructure.config.outbox.OutboxConfig` |
| `infrastructure.config.OpenApiConfig` | `infrastructure.config.openapi.OpenApiConfig` |
| `infrastructure.config.RateLimitConfig` | `infrastructure.config.ratelimit.RateLimitConfig` |
| `infrastructure.config.RateLimitProperties` | `infrastructure.config.ratelimit.RateLimitProperties` |
| `infrastructure.config.SchemaPathConfig` | `infrastructure.config.validation.SchemaPathConfig` |
| `infrastructure.config.ValidationProperties` | `infrastructure.config.validation.ValidationProperties` |
| `infrastructure.config.SecurityConfig` | `infrastructure.config.security.SecurityConfig` |

---

## Migration Phases

| Phase | Scope | Commit message |
|---|---|---|
| 1 | Move `domain/event/` DTOs → `application/dto/event/`, move `DocumentEventPublisher` → `application/port/out/`, delete `domain/event/` | `Move Kafka event DTOs from domain/event to application/dto/event, move DocumentEventPublisher to application/port/out` |
| 2 | Merge `application/port/in/` + `application/service/` → `application/usecase/` | `Merge application/port/in and application/service into application/usecase` |
| 3 | Move `infrastructure/config/` → concern sub-packages | `Move infrastructure/config to concern-based sub-packages` |
| 4 | Relocate test files to mirror new structure, update JaCoCo exclusions | `Relocate test classes, update JaCoCo exclusions` |
| 5 | Final verification — `mvn verify`, confirm no old package references remain | (verification only) |

---

## Testing Strategy

### Test Relocations (Phase 4)

| Old test path | New test path |
|---|---|
| `domain/event/DocumentReceivedTraceEventTest` | `application/dto/event/` |
| `domain/event/EventStatusTest` | `application/dto/event/` |
| `domain/event/StartSagaCommandTest` | `application/dto/event/` |
| `application/service/DocumentIntakeServiceTest` | `application/usecase/` |
| `infrastructure/config/CamelConfigTest` | `infrastructure/config/camel/` |
| `infrastructure/config/CamelConfigIntegrationTest` | `infrastructure/config/camel/` |
| `infrastructure/config/OutboxConfigTest` | `infrastructure/config/outbox/` |
| `infrastructure/config/RateLimitConfigTest` | `infrastructure/config/ratelimit/` |
| `infrastructure/config/RateLimitPropertiesTest` | `infrastructure/config/ratelimit/` |
| `infrastructure/config/SchemaPathConfigTest` | `infrastructure/config/validation/` |
| `infrastructure/config/SecurityConfigMethodTest` | `infrastructure/config/security/` |
| `infrastructure/config/SecurityDisabledConfigTest` | `infrastructure/config/security/` |
| `infrastructure/config/ValidationPropertiesTest` | `infrastructure/config/validation/` |

**Not moved:** all `infrastructure/adapter/` tests, all `domain/model/` and `domain/exception/` tests, `DocumentIntakeServiceApplicationTest`.

### No New Tests Required

This is a pure package rename with no logic changes. Per moved test file: update package declaration + import statements only.

### JaCoCo Exclusion Updates

Check `pom.xml` for any `<excludes>` patterns referencing:
- `application/port/in/**` → remove (use-case interfaces now in `usecase/`)
- `infrastructure/config/**` → split into per-concern patterns

### Coverage Target

≥ 83% line coverage (`mvn verify`) maintained throughout all phases.

---

## Key Decisions

| Decision | Rationale |
|---|---|
| `domain/event/` fully removed | No true domain events exist; all events are Kafka wire DTOs belonging in `application/dto/event/` |
| `DocumentEventPublisher` moved to `application/port/out/` | Ports are application contracts; domain layer must not own port interfaces |
| `application/port/in/` renamed to `application/usecase/` (not kept as `port/in/`) | Canonical: use-case interfaces and their implementations co-locate in `usecase/`; `port/in/` is an intermediate naming |
| `application/service/` merged into `application/usecase/` | Single package for use-case interfaces + implementation is cleaner and matches canonical |
| `infrastructure/adapter/` untouched | Already in canonical position — no changes needed |
| Config `openapi/` sub-package (not `api/`) | `openapi/` is specific; `api/` is ambiguous |
