# Hexagonal Architecture Migration — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate document-intake-service from DDD-layered to Hexagonal Architecture (Ports & Adapters) in two focused commits, keeping all tests green throughout.

**Architecture:** Two-commit hybrid strategy. Commit 1 fixes domain violations (pure `DocumentType` enum, explicit port interfaces, wire application service to ports). Commit 2 restructures packages (`adapter/in/`, `adapter/out/`) and extracts `KafkaDocumentConsumer`. No observable behaviour changes.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Mockito, JUnit 5, JaCoCo

---

## Baseline check

Run tests before touching anything to establish a green baseline.

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -q
```

Expected: BUILD SUCCESS. If not, stop and fix before proceeding.

---

## COMMIT 1 — Domain Cleanup

---

### Task 1: Pure domain `DocumentType` enum

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/domain/model/DocumentType.java`
- Create: `src/test/java/com/wpanther/document/intake/domain/model/DocumentTypeTest.java`

**Step 1: Write the failing test**

```java
// src/test/java/com/wpanther/document/intake/domain/model/DocumentTypeTest.java
package com.wpanther.document.intake.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    @Test
    void hasExactlySixValues() {
        assertThat(DocumentType.values()).hasSize(6);
    }

    @Test
    void containsAllExpectedTypes() {
        assertThat(DocumentType.values()).containsExactlyInAnyOrder(
            DocumentType.TAX_INVOICE,
            DocumentType.RECEIPT,
            DocumentType.INVOICE,
            DocumentType.DEBIT_CREDIT_NOTE,
            DocumentType.CANCELLATION_NOTE,
            DocumentType.ABBREVIATED_TAX_INVOICE
        );
    }

    @Test
    void isValueOfByName() {
        assertThat(DocumentType.valueOf("TAX_INVOICE")).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(DocumentType.valueOf("ABBREVIATED_TAX_INVOICE")).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
    }
}
```

**Step 2: Run to verify it fails**

```bash
mvn test -Dtest=DocumentTypeTest -q 2>&1 | tail -5
```

Expected: compilation error — `DocumentTypeTest` cannot find `domain.model.DocumentType`.

**Step 3: Create the enum**

```java
// src/main/java/com/wpanther/document/intake/domain/model/DocumentType.java
package com.wpanther.document.intake.domain.model;

public enum DocumentType {
    TAX_INVOICE,
    RECEIPT,
    INVOICE,
    DEBIT_CREDIT_NOTE,
    CANCELLATION_NOTE,
    ABBREVIATED_TAX_INVOICE
}
```

**Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=DocumentTypeTest -q
```

Expected: BUILD SUCCESS.

---

### Task 2: Create port interfaces

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/domain/port/in/SubmitDocumentUseCase.java`
- Create: `src/main/java/com/wpanther/document/intake/domain/port/in/GetDocumentUseCase.java`
- Create: `src/main/java/com/wpanther/document/intake/domain/port/out/DocumentRepository.java`
- Create: `src/main/java/com/wpanther/document/intake/domain/port/out/XmlValidationPort.java`
- Create: `src/main/java/com/wpanther/document/intake/domain/port/out/DocumentEventPublisher.java`

No tests needed — these are interface contracts with no behaviour.

**Step 1: Create inbound ports**

```java
// src/main/java/com/wpanther/document/intake/domain/port/in/SubmitDocumentUseCase.java
package com.wpanther.document.intake.domain.port.in;

import com.wpanther.document.intake.domain.model.IncomingDocument;

public interface SubmitDocumentUseCase {
    IncomingDocument submitDocument(String xmlContent, String source, String correlationId);
}
```

```java
// src/main/java/com/wpanther/document/intake/domain/port/in/GetDocumentUseCase.java
package com.wpanther.document.intake.domain.port.in;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import java.util.UUID;

public interface GetDocumentUseCase {
    IncomingDocument getDocument(UUID id);
}
```

**Step 2: Create outbound ports**

```java
// src/main/java/com/wpanther/document/intake/domain/port/out/DocumentRepository.java
package com.wpanther.document.intake.domain.port.out;

import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    IncomingDocument save(IncomingDocument document);
    Optional<IncomingDocument> findById(UUID id);
    Optional<IncomingDocument> findByDocumentNumber(String documentNumber);
    List<IncomingDocument> findByStatus(DocumentStatus status);
    boolean existsByDocumentNumber(String documentNumber);
}
```

```java
// src/main/java/com/wpanther/document/intake/domain/port/out/XmlValidationPort.java
package com.wpanther.document.intake.domain.port.out;

import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.domain.model.ValidationResult;

public interface XmlValidationPort {
    ValidationResult validate(String xmlContent);
    String extractDocumentNumber(String xmlContent);
    DocumentType extractDocumentType(String xmlContent);
}
```

```java
// src/main/java/com/wpanther/document/intake/domain/port/out/DocumentEventPublisher.java
package com.wpanther.document.intake.domain.port.out;

import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.StartSagaCommand;

public interface DocumentEventPublisher {
    void publishStartSagaCommand(StartSagaCommand command);
    void publishTraceEvent(DocumentReceivedTraceEvent event);
}
```

**Step 3: Delete the old interfaces (now replaced by ports)**

```bash
rm src/main/java/com/wpanther/document/intake/domain/repository/IncomingDocumentRepository.java
rm src/main/java/com/wpanther/document/intake/domain/service/XmlValidationService.java
rmdir src/main/java/com/wpanther/document/intake/domain/repository
rmdir src/main/java/com/wpanther/document/intake/domain/service
```

**Step 4: Verify compilation (tests will fail until implementations are updated — that is expected)**

```bash
mvn compile -q 2>&1 | grep "ERROR" | head -20
```

Expected: compilation errors in `IncomingDocumentRepositoryImpl`, `XmlValidationServiceImpl`, `EventPublisher`, `DocumentIntakeService`, `DocumentIntakeController`, and their tests. Proceed to the next tasks to fix each one.

---

### Task 3: Fix `IncomingDocument` — replace infra `DocumentType` import

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/domain/model/IncomingDocument.java`

**Step 1: Replace the import**

Change line 3:
```java
// REMOVE:
import com.wpanther.document.intake.infrastructure.validation.DocumentType;

// ADD:
import com.wpanther.document.intake.domain.model.DocumentType;
```

Everything else in `IncomingDocument.java` is unchanged — `DocumentType` is used only as a value holder there.

**Step 2: Verify the domain model compiles cleanly**

```bash
mvn compile -pl . -q 2>&1 | grep "IncomingDocument" | head -5
```

---

### Task 4: Update `IncomingDocumentRepositoryImpl` — implement `DocumentRepository`, add type mapping

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/persistence/IncomingDocumentRepositoryImpl.java`

The entity still uses `infrastructure.validation.DocumentType` (until Commit 2). Add a private mapping helper and update the class-level `implements`.

**Step 1: Update the implementation**

Replace the entire file content:

```java
package com.wpanther.document.intake.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.domain.exception.ValidationResultSerializationException;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.port.out.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter — bridges domain IncomingDocument and JPA IncomingDocumentEntity.
 * Implements the DocumentRepository outbound port.
 */
@Component
public class IncomingDocumentRepositoryImpl implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(IncomingDocumentRepositoryImpl.class);

    private final JpaIncomingDocumentRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public IncomingDocumentRepositoryImpl(JpaIncomingDocumentRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public IncomingDocument save(IncomingDocument document) {
        log.debug("Saving document: {}", document.getId());
        IncomingDocumentEntity entity = toEntity(document);
        IncomingDocumentEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<IncomingDocument> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<IncomingDocument> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber).map(this::toDomain);
    }

    @Override
    public List<IncomingDocument> findByStatus(DocumentStatus status) {
        return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByDocumentNumber(String documentNumber) {
        return jpaRepository.existsByDocumentNumber(documentNumber);
    }

    private IncomingDocumentEntity toEntity(IncomingDocument document) {
        ValidationResult validationResult = document.getValidationResult();
        return IncomingDocumentEntity.builder()
            .id(document.getId())
            .documentNumber(document.getDocumentNumber())
            .xmlContent(document.getXmlContent())
            .source(document.getSource())
            .correlationId(document.getCorrelationId())
            .documentType(toInfraDocumentType(document.getDocumentType()))
            .status(document.getStatus())
            .validationResult(validationResult != null ? serializeValidationResult(validationResult) : null)
            .receivedAt(document.getReceivedAt())
            .processedAt(document.getProcessedAt())
            .build();
    }

    private IncomingDocument toDomain(IncomingDocumentEntity entity) {
        IncomingDocument.Builder builder = IncomingDocument.builder()
            .id(entity.getId())
            .documentNumber(entity.getDocumentNumber())
            .xmlContent(entity.getXmlContent())
            .source(entity.getSource())
            .correlationId(entity.getCorrelationId())
            .documentType(toDomainDocumentType(entity.getDocumentType()))
            .status(entity.getStatus())
            .receivedAt(entity.getReceivedAt())
            .processedAt(entity.getProcessedAt());

        if (entity.getValidationResult() != null) {
            builder.validationResult(deserializeValidationResult(entity.getValidationResult()));
        }
        return builder.build();
    }

    // --- DocumentType mapping helpers (temporary until Commit 2 unifies to domain.DocumentType) ---

    private com.wpanther.document.intake.infrastructure.validation.DocumentType toInfraDocumentType(
            com.wpanther.document.intake.domain.model.DocumentType domainType) {
        if (domainType == null) return null;
        return com.wpanther.document.intake.infrastructure.validation.DocumentType.valueOf(domainType.name());
    }

    private com.wpanther.document.intake.domain.model.DocumentType toDomainDocumentType(
            com.wpanther.document.intake.infrastructure.validation.DocumentType infraType) {
        if (infraType == null) return null;
        return com.wpanther.document.intake.domain.model.DocumentType.valueOf(infraType.name());
    }

    // --- Validation result serialization ---

    private String serializeValidationResult(ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize ValidationResult to JSON", e);
            throw new ValidationResultSerializationException(
                "Failed to serialize ValidationResult to JSON. Error: " + e.getMessage(), e);
        }
    }

    private ValidationResult deserializeValidationResult(String json) {
        try {
            return objectMapper.readValue(json, ValidationResult.class);
        } catch (Exception e) {
            log.error("Failed to deserialize ValidationResult from JSON: {}", json, e);
            throw new ValidationResultSerializationException(
                "Failed to deserialize ValidationResult from JSON. Error: " + e.getMessage(), e);
        }
    }
}
```

**Step 2: Run persistence tests**

```bash
mvn test -Dtest=IncomingDocumentRepositoryImplTest -q
```

Expected: BUILD SUCCESS.

---

### Task 5: Update `XmlValidationServiceImpl` — implement `XmlValidationPort`, rename method, map return type

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/validation/XmlValidationServiceImpl.java`

Three changes:
1. `implements XmlValidationService` → `implements XmlValidationPort` (update import)
2. Rename `extractInvoiceNumber` → `extractDocumentNumber`
3. `extractDocumentType()` returns `domain.DocumentType` instead of `infrastructure.validation.DocumentType` (map at return point; internals stay as-is)

**Step 1: Update imports and class declaration**

Change the top of the file:
```java
// REMOVE:
import com.wpanther.document.intake.domain.service.XmlValidationService;

// ADD:
import com.wpanther.document.intake.domain.port.out.XmlValidationPort;
```

Change class declaration:
```java
// REMOVE:
public class XmlValidationServiceImpl implements XmlValidationService {

// ADD:
public class XmlValidationServiceImpl implements XmlValidationPort {
```

**Step 2: Rename `extractInvoiceNumber` → `extractDocumentNumber` and update `@Override`**

Find the method at line ~149 and rename:
```java
// REMOVE:
public String extractInvoiceNumber(String xmlContent) {

// ADD:
public String extractDocumentNumber(String xmlContent) {
```

**Step 3: Update `extractDocumentType()` return type and add mapping**

The `extractDocumentType()` method currently returns `DocumentType` (infra). Change its return type and add a mapping at each `return` statement. The internal `result.documentType` field in `UnmarshalResult` remains `infrastructure.validation.DocumentType`; we map it at the boundary.

Replace the `extractDocumentType` method:
```java
@Override
public com.wpanther.document.intake.domain.model.DocumentType extractDocumentType(String xmlContent) {
    if (xmlContent == null || xmlContent.isBlank()) {
        return null;
    }

    try {
        ValidationErrorHandler errorHandler = new ValidationErrorHandler();
        UnmarshalResult result = unmarshalXml(xmlContent, null, errorHandler);

        if (result.jaxbObject() == null || errorHandler.hasErrors()) {
            Document doc = parseXmlDom(xmlContent, new java.util.ArrayList<>());
            if (doc != null) {
                DocumentType infraType = detectDocumentTypeFromDom(doc);
                return toDomainDocumentType(infraType);
            }
            return null;
        }

        return toDomainDocumentType(result.documentType());

    } catch (Exception e) {
        log.error("Failed to extract document type", e);
        return null;
    }
}

/** Maps infrastructure DocumentType to domain DocumentType by name. */
private com.wpanther.document.intake.domain.model.DocumentType toDomainDocumentType(DocumentType infraType) {
    if (infraType == null) return null;
    return com.wpanther.document.intake.domain.model.DocumentType.valueOf(infraType.name());
}
```

**Step 4: Run validation tests**

```bash
mvn test -Dtest=XmlValidationServiceImplTest -q
```

Expected: BUILD SUCCESS (if the test calls `extractInvoiceNumber`, update that call to `extractDocumentNumber` in the test class first — see note below).

> **Note on test update:** `XmlValidationServiceImplTest` will have a call to `extractInvoiceNumber`. Change it to `extractDocumentNumber` wherever it appears in that test file. Return type assertion should expect `domain.model.DocumentType` — since both enums have identical names, `assertThat(result).isEqualTo(DocumentType.TAX_INVOICE)` continues to work after updating the import to `domain.model.DocumentType`.

---

### Task 6: Update `EventPublisher` — implement `DocumentEventPublisher`

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/messaging/EventPublisher.java`

**Step 1: Add `implements DocumentEventPublisher`**

```java
// ADD import:
import com.wpanther.document.intake.domain.port.out.DocumentEventPublisher;

// Change class declaration:
// REMOVE:
public class EventPublisher {

// ADD:
public class EventPublisher implements DocumentEventPublisher {
```

The two existing methods (`publishStartSagaCommand`, `publishTraceEvent`) already match the port signatures exactly — no other changes needed.

**Step 2: Run messaging tests**

```bash
mvn test -Dtest=EventPublisherTest -q
```

Expected: BUILD SUCCESS.

---

### Task 7: Update `DocumentIntakeService` — use port interfaces, implement use-case ports

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeService.java`

**Step 1: Replace imports and constructor**

Replace these imports:
```java
// REMOVE:
import com.wpanther.document.intake.domain.repository.IncomingDocumentRepository;
import com.wpanther.document.intake.domain.service.XmlValidationService;
import com.wpanther.document.intake.infrastructure.messaging.EventPublisher;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;

// ADD:
import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.domain.port.in.GetDocumentUseCase;
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.domain.port.out.DocumentEventPublisher;
import com.wpanther.document.intake.domain.port.out.DocumentRepository;
import com.wpanther.document.intake.domain.port.out.XmlValidationPort;
```

Update class declaration:
```java
// REMOVE:
public class DocumentIntakeService {

// ADD:
public class DocumentIntakeService implements SubmitDocumentUseCase, GetDocumentUseCase {
```

Update field types and constructor:
```java
// REMOVE:
private final IncomingDocumentRepository documentRepository;
private final XmlValidationService validationService;
private final EventPublisher eventPublisher;

public DocumentIntakeService(IncomingDocumentRepository documentRepository,
                            XmlValidationService validationService,
                            EventPublisher eventPublisher) {

// ADD:
private final DocumentRepository documentRepository;
private final XmlValidationPort validationService;
private final DocumentEventPublisher eventPublisher;

public DocumentIntakeService(DocumentRepository documentRepository,
                             XmlValidationPort validationService,
                             DocumentEventPublisher eventPublisher) {
```

**Step 2: Fix the one renamed method call**

In `submitDocument()`, change:
```java
// REMOVE:
String documentNumber = validationService.extractInvoiceNumber(xmlContent);

// ADD:
String documentNumber = validationService.extractDocumentNumber(xmlContent);
```

**Step 3: Verify `@Override` annotations compile on `submitDocument` and `getDocument`**

Add `@Override` to both methods now that the class implements the interfaces:
```java
@Override
@Transactional
public IncomingDocument submitDocument(...) { ... }

@Override
@Transactional(readOnly = true)
public IncomingDocument getDocument(...) { ... }
```

**Step 4: Update `DocumentIntakeServiceTest`**

Update these lines in `src/test/java/com/wpanther/document/intake/application/service/DocumentIntakeServiceTest.java`:

```java
// REMOVE these imports:
import com.wpanther.document.intake.domain.repository.IncomingDocumentRepository;
import com.wpanther.document.intake.domain.service.XmlValidationService;
import com.wpanther.document.intake.infrastructure.messaging.EventPublisher;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;

// ADD:
import com.wpanther.document.intake.domain.model.DocumentType;
import com.wpanther.document.intake.domain.port.out.DocumentEventPublisher;
import com.wpanther.document.intake.domain.port.out.DocumentRepository;
import com.wpanther.document.intake.domain.port.out.XmlValidationPort;
```

```java
// REMOVE:
@Mock private IncomingDocumentRepository documentRepository;
@Mock private XmlValidationService validationService;
@Mock private EventPublisher eventPublisher;

// ADD:
@Mock private DocumentRepository documentRepository;
@Mock private XmlValidationPort validationService;
@Mock private DocumentEventPublisher eventPublisher;
```

```java
// In setUp() — change the mock stub:
// REMOVE:
when(validationService.extractInvoiceNumber(any())).thenReturn("INV-2024-001");

// ADD:
when(validationService.extractDocumentNumber(any())).thenReturn("INV-2024-001");
```

```java
// In testSubmitInvoiceExtractsInvoiceNumber(), change:
// REMOVE:
verify(validationService).extractInvoiceNumber(VALID_XML);

// ADD:
verify(validationService).extractDocumentNumber(VALID_XML);
```

**Step 5: Run service tests**

```bash
mvn test -Dtest=DocumentIntakeServiceTest -q
```

Expected: BUILD SUCCESS.

---

### Task 8: Update `DocumentIntakeController` — depend on port interfaces

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/application/controller/DocumentIntakeController.java`

**Step 1: Update imports and constructor**

```java
// REMOVE:
import com.wpanther.document.intake.application.service.DocumentIntakeService;

// ADD:
import com.wpanther.document.intake.domain.port.in.GetDocumentUseCase;
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
```

Replace the two references to `DocumentIntakeService` in field declaration and constructor:
```java
// REMOVE:
private final DocumentIntakeService intakeService;

public DocumentIntakeController(
        DocumentIntakeService intakeService,
        ProducerTemplate camelProducer,
        ValidationProperties validationProperties) {
    this.intakeService = intakeService;

// ADD:
private final SubmitDocumentUseCase submitUseCase;
private final GetDocumentUseCase getUseCase;

public DocumentIntakeController(
        SubmitDocumentUseCase submitUseCase,
        GetDocumentUseCase getUseCase,
        ProducerTemplate camelProducer,
        ValidationProperties validationProperties) {
    this.submitUseCase = submitUseCase;
    this.getUseCase = getUseCase;
```

Replace usages in method bodies:
```java
// In getDocumentStatus():
// REMOVE: IncomingDocument document = intakeService.getDocument(id);
// ADD:    IncomingDocument document = getUseCase.getDocument(id);
```

The `submitDocument()` REST handler calls `camelProducer.sendBodyAndHeader("direct:document-intake", ...)` which internally calls `intakeService.submitDocument(...)` via Camel. The `CamelConfig` processor holds a reference to `DocumentIntakeService` directly (by the concrete class). Update `CamelConfig` to inject `SubmitDocumentUseCase` instead:

In `CamelConfig.java`:
```java
// REMOVE:
import com.wpanther.document.intake.application.service.DocumentIntakeService;
private final DocumentIntakeService intakeService;
public CamelConfig(DocumentIntakeService intakeService, ...)

// ADD:
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
private final SubmitDocumentUseCase submitUseCase;
public CamelConfig(SubmitDocumentUseCase submitUseCase, ...)
```

Update both `.process(exchange -> { ... intakeService.submitDocument(...) ... })` calls in `CamelConfig` to use `submitUseCase.submitDocument(...)`.

**Step 2: Update `DocumentIntakeControllerTest`**

In `src/test/java/com/wpanther/document/intake/application/controller/DocumentIntakeControllerTest.java`:

```java
// REMOVE:
import com.wpanther.document.intake.application.service.DocumentIntakeService;

// ADD:
import com.wpanther.document.intake.domain.port.in.GetDocumentUseCase;
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
```

```java
// REMOVE:
@MockBean private DocumentIntakeService intakeService;

// ADD:
@MockBean private SubmitDocumentUseCase submitUseCase;
@MockBean private GetDocumentUseCase getUseCase;
```

Update any `intakeService.getDocument(...)` stub references to `getUseCase.getDocument(...)`.

Do the same in `DocumentIntakeControllerAdditionalTest.java` if it has the same pattern.

**Step 3: Run all tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS with all tests passing.

---

### Task 9: Commit 1

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
git add src/
git commit -m "refactor: introduce hexagonal ports, pure domain DocumentType (Commit 1/2)"
```

---

## COMMIT 2 — Package Restructuring

---

### Task 10: Create `TedaDocumentType` in `adapter/out/validation/` with test

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/out/validation/TedaDocumentType.java`
- Create: `src/test/java/com/wpanther/document/intake/adapter/out/validation/TedaDocumentTypeTest.java`

`TedaDocumentType` is the old `infrastructure/validation/DocumentType` renamed. Copy its full content, change the package declaration and class name, then add a mapping method to `domain.DocumentType`.

**Step 1: Write the failing test**

```java
// src/test/java/com/wpanther/document/intake/adapter/out/validation/TedaDocumentTypeTest.java
package com.wpanther.document.intake.adapter.out.validation;

import com.wpanther.document.intake.domain.model.DocumentType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TedaDocumentTypeTest {

    @Test
    void mapsEveryValueToDomainDocumentType() {
        for (TedaDocumentType tedaType : TedaDocumentType.values()) {
            DocumentType domainType = tedaType.toDomainDocumentType();
            assertThat(domainType).isNotNull();
            assertThat(domainType.name()).isEqualTo(tedaType.name());
        }
    }

    @Test
    void fromDomainDocumentTypeRoundTrip() {
        for (DocumentType domainType : DocumentType.values()) {
            TedaDocumentType tedaType = TedaDocumentType.fromDomainDocumentType(domainType);
            assertThat(tedaType).isNotNull();
            assertThat(tedaType.name()).isEqualTo(domainType.name());
        }
    }

    @Test
    void fromNamespaceUriFindsCorrectType() {
        TedaDocumentType result = TedaDocumentType.fromNamespaceUri(
            "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2");
        assertThat(result).isEqualTo(TedaDocumentType.TAX_INVOICE);
    }
}
```

**Step 2: Run to verify it fails**

```bash
mvn test -Dtest=TedaDocumentTypeTest -q 2>&1 | tail -5
```

**Step 3: Create `TedaDocumentType`**

Copy the full body of `infrastructure/validation/DocumentType.java`, then:
1. Change `package com.wpanther.document.intake.infrastructure.validation;` → `package com.wpanther.document.intake.adapter.out.validation;`
2. Change `public enum DocumentType {` → `public enum TedaDocumentType {`
3. Add these two methods at the end of the enum (before the closing `}`):

```java
/** Maps this TedaDocumentType to the pure domain DocumentType by name. */
public com.wpanther.document.intake.domain.model.DocumentType toDomainDocumentType() {
    return com.wpanther.document.intake.domain.model.DocumentType.valueOf(this.name());
}

/** Maps a domain DocumentType to TedaDocumentType by name. */
public static TedaDocumentType fromDomainDocumentType(
        com.wpanther.document.intake.domain.model.DocumentType domainType) {
    if (domainType == null) return null;
    return TedaDocumentType.valueOf(domainType.name());
}
```

**Step 4: Run test**

```bash
mvn test -Dtest=TedaDocumentTypeTest -q
```

Expected: BUILD SUCCESS.

---

### Task 11: Create `TedaXmlValidationAdapter` in `adapter/out/validation/`

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/out/validation/TedaXmlValidationAdapter.java`
- Move: `InvoiceNumberExtractor.java` → `adapter/out/validation/`
- Move: `InvoiceNumberExtractorStrategies.java` → `adapter/out/validation/`
- Move: `ValidationErrorHandler.java` → `adapter/out/validation/`

**Step 1: Move support classes**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/adapter/out/validation
cp src/main/java/com/wpanther/document/intake/infrastructure/validation/InvoiceNumberExtractor.java \
   src/main/java/com/wpanther/document/intake/adapter/out/validation/
cp src/main/java/com/wpanther/document/intake/infrastructure/validation/InvoiceNumberExtractorStrategies.java \
   src/main/java/com/wpanther/document/intake/adapter/out/validation/
cp src/main/java/com/wpanther/document/intake/infrastructure/validation/ValidationErrorHandler.java \
   src/main/java/com/wpanther/document/intake/adapter/out/validation/
```

Update package declarations in the three new files to `com.wpanther.document.intake.adapter.out.validation`.

**Step 2: Create `TedaXmlValidationAdapter`**

Copy `infrastructure/validation/XmlValidationServiceImpl.java` to `adapter/out/validation/TedaXmlValidationAdapter.java`, then:

1. Change package: `com.wpanther.document.intake.infrastructure.validation` → `com.wpanther.document.intake.adapter.out.validation`
2. Remove: `import com.wpanther.document.intake.domain.service.XmlValidationService;`
3. Add: `import com.wpanther.document.intake.domain.port.out.XmlValidationPort;`
4. Change class declaration: `public class XmlValidationServiceImpl implements XmlValidationService` → `public class TedaXmlValidationAdapter implements XmlValidationPort`
5. Change all internal `DocumentType` references (field types, local variables, method params) to `TedaDocumentType` — these are the internal infra ones. Import `com.wpanther.document.intake.adapter.out.validation.TedaDocumentType` (same package, so no import needed).
6. Update `ValidationErrorHandler` and `InvoiceNumberExtractor` to use same-package imports (no change needed since same package).
7. The `extractDocumentType()` method already returns `domain.DocumentType` via `toDomainDocumentType()` — update the helper to use `TedaDocumentType`:

```java
private com.wpanther.document.intake.domain.model.DocumentType toDomainDocumentType(TedaDocumentType tedaType) {
    if (tedaType == null) return null;
    return tedaType.toDomainDocumentType();
}
```

8. The `UnmarshalResult` record's `documentType` field type should be `TedaDocumentType`.
9. `SchemaPathConfig.getSchemaPath(type.name())` — `type` is now `TedaDocumentType`, `.name()` still works.

**Step 3: Run validation adapter tests**

```bash
mvn test -Dtest="TedaDocumentTypeTest,XmlValidationServiceImplTest" -q
```

> **Note:** `XmlValidationServiceImplTest` still runs against the old `XmlValidationServiceImpl` which still exists. That is intentional — it proves the old path is still green. The old class will be deleted in the next step.

---

### Task 12: Delete `infrastructure/validation/` and redirect test

**Step 1: Delete old infrastructure validation classes**

```bash
rm src/main/java/com/wpanther/document/intake/infrastructure/validation/XmlValidationServiceImpl.java
rm src/main/java/com/wpanther/document/intake/infrastructure/validation/DocumentType.java
rm src/main/java/com/wpanther/document/intake/infrastructure/validation/InvoiceNumberExtractor.java
rm src/main/java/com/wpanther/document/intake/infrastructure/validation/InvoiceNumberExtractorStrategies.java
rm src/main/java/com/wpanther/document/intake/infrastructure/validation/ValidationErrorHandler.java
rmdir src/main/java/com/wpanther/document/intake/infrastructure/validation
```

**Step 2: Update `IncomingDocumentRepositoryImpl` — remove infra DocumentType mapping helpers**

Now that `infrastructure.validation.DocumentType` is gone, update the two mapping helpers in `IncomingDocumentRepositoryImpl.java` to no longer use it. The entity's `documentType` field will be updated next (Task 13). For now, keep compilation by importing `TedaDocumentType` from its new location:

```java
// In IncomingDocumentRepositoryImpl:
// REMOVE the two helpers that reference infrastructure.validation.DocumentType
// ADD helpers using TedaDocumentType:

import com.wpanther.document.intake.adapter.out.validation.TedaDocumentType;

private TedaDocumentType toInfraDocumentType(com.wpanther.document.intake.domain.model.DocumentType domainType) {
    if (domainType == null) return null;
    return TedaDocumentType.fromDomainDocumentType(domainType);
}

private com.wpanther.document.intake.domain.model.DocumentType toDomainDocumentType(TedaDocumentType tedaType) {
    if (tedaType == null) return null;
    return tedaType.toDomainDocumentType();
}
```

Also update `IncomingDocumentEntity.java` — its `documentType` field imports `infrastructure.validation.DocumentType`. Change to `TedaDocumentType`:

```java
// In IncomingDocumentEntity.java:
// REMOVE: import com.wpanther.document.intake.infrastructure.validation.DocumentType;
// ADD:    import com.wpanther.document.intake.adapter.out.validation.TedaDocumentType;

// REMOVE: private DocumentType documentType;
// ADD:    private TedaDocumentType documentType;
```

**Step 3: Update `XmlValidationServiceImplTest` → rename to `TedaXmlValidationAdapterTest`**

Move test file:
```bash
mkdir -p src/test/java/com/wpanther/document/intake/adapter/out/validation
cp src/test/java/com/wpanther/document/intake/infrastructure/validation/XmlValidationServiceImplTest.java \
   src/test/java/com/wpanther/document/intake/adapter/out/validation/TedaXmlValidationAdapterTest.java
rm src/test/java/com/wpanther/document/intake/infrastructure/validation/XmlValidationServiceImplTest.java
```

In `TedaXmlValidationAdapterTest.java`:
- Change package to `com.wpanther.document.intake.adapter.out.validation`
- Change class name to `TedaXmlValidationAdapterTest`
- Change `@Autowired XmlValidationServiceImpl` (or however it's injected) to `TedaXmlValidationAdapter`
- Change `DocumentType` imports to `com.wpanther.document.intake.domain.model.DocumentType`

**Step 4: Update `DocumentTypeTest` in infrastructure/validation → adapt or move**

```bash
cp src/test/java/com/wpanther/document/intake/infrastructure/validation/DocumentTypeTest.java \
   src/test/java/com/wpanther/document/intake/adapter/out/validation/TedaDocumentTypeAdditionalTest.java
rm src/test/java/com/wpanther/document/intake/infrastructure/validation/DocumentTypeTest.java
```

Update `TedaDocumentTypeAdditionalTest.java` to test `TedaDocumentType` instead of the old `DocumentType`.

**Step 5: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

---

### Task 13: Move persistence adapter to `adapter/out/persistence/`

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/out/persistence/JpaDocumentRepository.java`
- Move: `IncomingDocumentEntity.java` → `adapter/out/persistence/`
- Move: `JpaIncomingDocumentRepository.java` → `adapter/out/persistence/`
- Move: `outbox/` → `adapter/out/persistence/outbox/`
- Delete: `infrastructure/persistence/` (all files)

**Step 1: Create the new package and move files**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/adapter/out/persistence/outbox
mkdir -p src/test/java/com/wpanther/document/intake/adapter/out/persistence/outbox

# Copy main sources
cp src/main/java/com/wpanther/document/intake/infrastructure/persistence/IncomingDocumentEntity.java \
   src/main/java/com/wpanther/document/intake/adapter/out/persistence/
cp src/main/java/com/wpanther/document/intake/infrastructure/persistence/JpaIncomingDocumentRepository.java \
   src/main/java/com/wpanther/document/intake/adapter/out/persistence/
cp src/main/java/com/wpanther/document/intake/infrastructure/persistence/outbox/OutboxEventEntity.java \
   src/main/java/com/wpanther/document/intake/adapter/out/persistence/outbox/
cp src/main/java/com/wpanther/document/intake/infrastructure/persistence/outbox/SpringDataOutboxRepository.java \
   src/main/java/com/wpanther/document/intake/adapter/out/persistence/outbox/
cp src/main/java/com/wpanther/document/intake/infrastructure/persistence/outbox/JpaOutboxEventRepository.java \
   src/main/java/com/wpanther/document/intake/adapter/out/persistence/outbox/
```

**Step 2: Update package declarations in all copied files**

Change `com.wpanther.document.intake.infrastructure.persistence` → `com.wpanther.document.intake.adapter.out.persistence` (and same for `.outbox` subpackage) in each file.

For `IncomingDocumentEntity.java` in the new location: update the `TedaDocumentType` import to come from `adapter.out.validation` (should already be there from Task 12).

**Step 3: Create `JpaDocumentRepository`**

Copy `infrastructure/persistence/IncomingDocumentRepositoryImpl.java` to `adapter/out/persistence/JpaDocumentRepository.java`:

1. Change package to `com.wpanther.document.intake.adapter.out.persistence`
2. Rename class to `JpaDocumentRepository`
3. Update all references to `JpaIncomingDocumentRepository` to use the same-package version (no import change needed)
4. Update `IncomingDocumentEntity` import to same-package version
5. The `TedaDocumentType` import should already point to `adapter.out.validation`

**Step 4: Update `OutboxConfig.java`**

`OutboxConfig` registers the `JpaOutboxEventRepository` bean. Update its import to the new package:
```java
// REMOVE: import com.wpanther.document.intake.infrastructure.persistence.outbox.JpaOutboxEventRepository;
// ADD:    import com.wpanther.document.intake.adapter.out.persistence.outbox.JpaOutboxEventRepository;
```

**Step 5: Delete old persistence classes**

```bash
rm -rf src/main/java/com/wpanther/document/intake/infrastructure/persistence/
```

**Step 6: Move persistence tests**

```bash
mkdir -p src/test/java/com/wpanther/document/intake/adapter/out/persistence/outbox

for f in IncomingDocumentRepositoryImplTest IncomingDocumentEntityTest JpaIncomingDocumentRepositoryTest; do
  cp "src/test/java/com/wpanther/document/intake/infrastructure/persistence/${f}.java" \
     "src/test/java/com/wpanther/document/intake/adapter/out/persistence/"
  rm "src/test/java/com/wpanther/document/intake/infrastructure/persistence/${f}.java"
done

for f in OutboxEventEntityTest JpaOutboxEventRepositoryTest; do
  cp "src/test/java/com/wpanther/document/intake/infrastructure/persistence/outbox/${f}.java" \
     "src/test/java/com/wpanther/document/intake/adapter/out/persistence/outbox/"
  rm "src/test/java/com/wpanther/document/intake/infrastructure/persistence/outbox/${f}.java"
done
rmdir src/test/java/com/wpanther/document/intake/infrastructure/persistence/outbox
rmdir src/test/java/com/wpanther/document/intake/infrastructure/persistence
```

In each moved test file: update package declaration and any `infrastructure.persistence` imports to `adapter.out.persistence`.

The test for `IncomingDocumentRepositoryImpl` — rename to `JpaDocumentRepositoryTest`, update class name and references inside.

**Step 7: Run persistence tests**

```bash
mvn test -Dtest="JpaDocumentRepositoryTest,IncomingDocumentEntityTest,JpaIncomingDocumentRepositoryTest,OutboxEventEntityTest,JpaOutboxEventRepositoryTest" -q
```

Expected: BUILD SUCCESS.

---

### Task 14: Move messaging and health adapters

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/out/messaging/OutboxEventPublisher.java`
- Create: `src/main/java/com/wpanther/document/intake/adapter/out/health/OutboxHealthIndicator.java`
- Delete: `infrastructure/messaging/`, `infrastructure/health/`

**Step 1: Create `OutboxEventPublisher`**

Copy `infrastructure/messaging/EventPublisher.java` to `adapter/out/messaging/OutboxEventPublisher.java`:
1. Change package to `com.wpanther.document.intake.adapter.out.messaging`
2. Rename class to `OutboxEventPublisher`
3. `implements DocumentEventPublisher` (import already correct)

**Step 2: Create `OutboxHealthIndicator`**

Copy `infrastructure/health/OutboxHealthIndicator.java` to `adapter/out/health/OutboxHealthIndicator.java`:
1. Change package to `com.wpanther.document.intake.adapter.out.health`
2. Update any `infrastructure.persistence.outbox` imports to `adapter.out.persistence.outbox`

**Step 3: Delete old classes**

```bash
rm -rf src/main/java/com/wpanther/document/intake/infrastructure/messaging/
rm -rf src/main/java/com/wpanther/document/intake/infrastructure/health/
```

**Step 4: Move messaging and health tests**

```bash
mkdir -p src/test/java/com/wpanther/document/intake/adapter/out/messaging
mkdir -p src/test/java/com/wpanther/document/intake/adapter/out/health

cp src/test/java/com/wpanther/document/intake/infrastructure/messaging/EventPublisherTest.java \
   src/test/java/com/wpanther/document/intake/adapter/out/messaging/OutboxEventPublisherTest.java
rm src/test/java/com/wpanther/document/intake/infrastructure/messaging/EventPublisherTest.java
rmdir src/test/java/com/wpanther/document/intake/infrastructure/messaging

cp src/test/java/com/wpanther/document/intake/infrastructure/health/OutboxHealthIndicatorTest.java \
   src/test/java/com/wpanther/document/intake/adapter/out/health/
rm src/test/java/com/wpanther/document/intake/infrastructure/health/OutboxHealthIndicatorTest.java
rmdir src/test/java/com/wpanther/document/intake/infrastructure/health
```

Update package declarations and class/import names in each moved test.

**Step 5: Run tests**

```bash
mvn test -Dtest="OutboxEventPublisherTest,OutboxHealthIndicatorTest" -q
```

---

### Task 15: Move REST controller to `adapter/in/web/`

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/in/web/DocumentIntakeController.java`
- Delete: `application/controller/DocumentIntakeController.java`

**Step 1: Move the controller**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/adapter/in/web
cp src/main/java/com/wpanther/document/intake/application/controller/DocumentIntakeController.java \
   src/main/java/com/wpanther/document/intake/adapter/in/web/
rm src/main/java/com/wpanther/document/intake/application/controller/DocumentIntakeController.java
rmdir src/main/java/com/wpanther/document/intake/application/controller
```

In the new file: change package to `com.wpanther.document.intake.adapter.in.web`. All imports remain correct.

**Step 2: Move controller tests**

```bash
mkdir -p src/test/java/com/wpanther/document/intake/adapter/in/web

for f in DocumentIntakeControllerTest DocumentIntakeControllerAdditionalTest; do
  cp "src/test/java/com/wpanther/document/intake/application/controller/${f}.java" \
     "src/test/java/com/wpanther/document/intake/adapter/in/web/"
  rm "src/test/java/com/wpanther/document/intake/application/controller/${f}.java"
done
rmdir src/test/java/com/wpanther/document/intake/application/controller
```

Update package declarations.

**Step 3: Run controller tests**

```bash
mvn test -Dtest="DocumentIntakeControllerTest,DocumentIntakeControllerAdditionalTest" -q
```

---

### Task 16: Extract `KafkaDocumentConsumer` to `adapter/in/messaging/` and update `CamelConfig`

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/adapter/in/messaging/KafkaDocumentConsumer.java`
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/config/CamelConfig.java`

**Step 1: Create `KafkaDocumentConsumer`**

```java
// src/main/java/com/wpanther/document/intake/adapter/in/messaging/KafkaDocumentConsumer.java
package com.wpanther.document.intake.adapter.in.messaging;

import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.port.in.SubmitDocumentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaDocumentConsumer {

    private final SubmitDocumentUseCase submitUseCase;

    public void consume(Exchange exchange) {
        String xmlContent = exchange.getIn().getBody(String.class);
        String correlationId = exchange.getIn().getHeader("kafka.KEY", String.class);

        IncomingDocument document = submitUseCase.submitDocument(xmlContent, "KAFKA", correlationId);

        exchange.getIn().setHeader("documentId", document.getId().toString());
        exchange.getIn().setHeader("documentType", document.getDocumentType().name());
        exchange.getIn().setHeader("isValid", document.isValid());
    }
}
```

**Step 2: Update `CamelConfig` Kafka route to delegate to `KafkaDocumentConsumer`**

```java
// In CamelConfig — add constructor parameter:
private final KafkaDocumentConsumer kafkaConsumer;

// Update constructor signature (add kafkaConsumer param)

// In configure(), replace the Kafka route's .process(exchange -> { ... }) with:
.process(kafkaConsumer::consume)
```

Also remove the `submitUseCase` field from `CamelConfig` if you moved the submit logic fully into `KafkaDocumentConsumer`. The `direct:document-intake` REST route still calls `submitUseCase.submitDocument()` directly — keep that reference in `CamelConfig`.

**Step 3: Run Camel route tests**

```bash
mvn test -Dtest="CamelConfigTest,CamelConfigIntegrationTest" -q
```

---

### Task 17: Rename application service to `DocumentIntakeApplicationService`

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java`
- Delete: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeService.java`

**Step 1: Copy and rename**

```bash
cp src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeService.java \
   src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
rm src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeService.java
```

In the new file: rename class `DocumentIntakeService` → `DocumentIntakeApplicationService`.

**Step 2: Move and rename service test**

```bash
cp src/test/java/com/wpanther/document/intake/application/service/DocumentIntakeServiceTest.java \
   src/test/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationServiceTest.java
rm src/test/java/com/wpanther/document/intake/application/service/DocumentIntakeServiceTest.java
```

In the test: rename class to `DocumentIntakeApplicationServiceTest`, update `@InjectMocks DocumentIntakeService` → `@InjectMocks DocumentIntakeApplicationService`.

**Step 3: Run all tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS — all tests green.

---

### Task 18: Clean up `infrastructure/` empty packages

```bash
# Remove any remaining empty infrastructure subdirectories
find src/main/java/com/wpanther/document/intake/infrastructure -type d -empty -delete
find src/test/java/com/wpanther/document/intake/infrastructure -type d -empty -delete
```

Move remaining infrastructure config test files to stay in `infrastructure/config/` (they belong there):

```bash
# These stay where they are — config tests test Spring Boot wiring, not adapters:
# infrastructure/config/CamelConfigTest.java           ← keep
# infrastructure/config/OutboxConfigTest.java          ← keep
# infrastructure/config/SecurityConfigMethodTest.java  ← keep
# infrastructure/config/RateLimitConfigTest.java       ← keep
# infrastructure/config/RateLimitPropertiesTest.java   ← keep
# infrastructure/config/ValidationPropertiesTest.java  ← keep
# infrastructure/config/SchemaPathConfigTest.java      ← keep
# infrastructure/config/SecurityDisabledConfigTest.java← keep
```

**Run full test suite with coverage**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS, JaCoCo ≥ 90% line coverage per package.

---

### Task 19: Commit 2

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
git add src/
git commit -m "refactor: restructure to adapter/in + adapter/out packages (Commit 2/2)"
```

---

## Verification checklist

After both commits:

- [ ] `mvn verify` passes with JaCoCo ≥ 90%
- [ ] No `infrastructure.validation.DocumentType` imports remain in `domain/` or `application/`
- [ ] No `infrastructure.messaging.EventPublisher` imports remain in `application/`
- [ ] `domain/` package has zero Spring/JPA/teda imports
- [ ] `adapter/in/web/DocumentIntakeController` depends only on `SubmitDocumentUseCase`, `GetDocumentUseCase`
- [ ] `adapter/in/messaging/KafkaDocumentConsumer` depends only on `SubmitDocumentUseCase`
- [ ] `application/service/DocumentIntakeApplicationService` depends only on the three outbound ports
- [ ] All old `infrastructure/validation/`, `infrastructure/messaging/`, `infrastructure/health/`, `infrastructure/persistence/` classes are deleted
- [ ] `infrastructure/config/` remains intact (Spring Boot plumbing)

```bash
# Confirm no domain layer leaks
grep -r "infrastructure" src/main/java/com/wpanther/document/intake/domain/ && echo "LEAK FOUND" || echo "Domain is clean"
grep -r "infrastructure.messaging" src/main/java/com/wpanther/document/intake/application/ && echo "LEAK FOUND" || echo "Application is clean"
```
