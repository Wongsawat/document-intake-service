# Document-Intake-Service Hexagonal Architecture Canonical Alignment

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align document-intake-service with the canonical hexagonal layout used by all other services — move Kafka wire DTOs out of `domain/event/`, merge `application/port/in/` into `application/usecase/`, and split `infrastructure/config/` into concern-based sub-packages.

**Architecture:** Pure package rename + relocation, no logic changes. `domain/` ← `application/` ← `infrastructure/` strict dependency rule. Kafka wire DTOs move to `application/dto/event/`. `DocumentEventPublisher` moves to `application/port/out/`. Use-case interfaces and implementation co-locate in `application/usecase/`. Config splits by concern under `infrastructure/config/`.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Maven, JUnit 5, JaCoCo 0.8.11

**Design doc:** `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`

---

## Pre-flight

Confirm all unit tests pass:

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -q
```

Expected: BUILD SUCCESS. If not, stop and fix first.

---

## Phase 1 — Move `domain/event/` out of domain

### Task 1: Move Kafka event DTOs to `application/dto/event/` and `DocumentEventPublisher` to `application/port/out/`

The domain layer must not contain Kafka wire DTOs or port interfaces. `StartSagaCommand`, `DocumentReceivedTraceEvent`, and `EventStatus` are Kafka serialization concerns — they move to `application/dto/event/`. `DocumentEventPublisher` is an outbound port — it moves to `application/port/out/`.

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/domain/event/StartSagaCommand.java` → `src/main/java/com/wpanther/document/intake/application/dto/event/StartSagaCommand.java`
- Move: `src/main/java/com/wpanther/document/intake/domain/event/DocumentReceivedTraceEvent.java` → `src/main/java/com/wpanther/document/intake/application/dto/event/DocumentReceivedTraceEvent.java`
- Move: `src/main/java/com/wpanther/document/intake/domain/event/EventStatus.java` → `src/main/java/com/wpanther/document/intake/application/dto/event/EventStatus.java`
- Move: `src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java` → `src/main/java/com/wpanther/document/intake/application/port/out/DocumentEventPublisher.java`
- Modify: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java`
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/adapter/out/messaging/EventPublisher.java`

**Step 1: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/application/dto/event
```

(The `application/port/out/` directory already exists — `XmlValidationPort.java` is there.)

**Step 2: Move `StartSagaCommand.java` — update package declaration**

Open `src/main/java/com/wpanther/document/intake/domain/event/StartSagaCommand.java`.

Change the first line:
```java
// Before:
package com.wpanther.document.intake.domain.event;

// After:
package com.wpanther.document.intake.application.dto.event;
```

Move the file:
```bash
mv src/main/java/com/wpanther/document/intake/domain/event/StartSagaCommand.java \
   src/main/java/com/wpanther/document/intake/application/dto/event/StartSagaCommand.java
```

**Step 3: Move `DocumentReceivedTraceEvent.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.domain.event;

// After:
package com.wpanther.document.intake.application.dto.event;
```

```bash
mv src/main/java/com/wpanther/document/intake/domain/event/DocumentReceivedTraceEvent.java \
   src/main/java/com/wpanther/document/intake/application/dto/event/DocumentReceivedTraceEvent.java
```

**Step 4: Move `EventStatus.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.domain.event;

// After:
package com.wpanther.document.intake.application.dto.event;
```

```bash
mv src/main/java/com/wpanther/document/intake/domain/event/EventStatus.java \
   src/main/java/com/wpanther/document/intake/application/dto/event/EventStatus.java
```

**Step 5: Move `DocumentEventPublisher.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.domain.event.port;

// After:
package com.wpanther.document.intake.application.port.out;
```

```bash
mv src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java \
   src/main/java/com/wpanther/document/intake/application/port/out/DocumentEventPublisher.java
rmdir src/main/java/com/wpanther/document/intake/domain/event/port
rmdir src/main/java/com/wpanther/document/intake/domain/event
```

**Step 6: Update imports in `DocumentIntakeApplicationService.java`**

Find and replace all old-path imports:
```bash
grep -n "domain.event" src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
```

Replace each found import:
```java
// Before:
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.EventStatus;
import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;

// After:
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.dto.event.EventStatus;
import com.wpanther.document.intake.application.port.out.DocumentEventPublisher;
```

**Step 7: Update imports in `EventPublisher.java`**

```bash
grep -n "domain.event" src/main/java/com/wpanther/document/intake/infrastructure/adapter/out/messaging/EventPublisher.java
```

Replace each found import:
```java
// Before:
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;

// After:
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.port.out.DocumentEventPublisher;
```

**Step 8: Check for any other importers**

```bash
grep -rl "document.intake.domain.event" src/main/
```

Update any additional files found using the same mapping above.

**Step 9: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" before continuing.

**Step 10: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 11: Verify `domain/event/` is gone**

```bash
find src/main/java/com/wpanther/document/intake/domain/event -type f 2>/dev/null
```

Expected: no output (directory deleted in Step 5).

**Step 12: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/application/dto/ \
        src/main/java/com/wpanther/document/intake/application/port/out/DocumentEventPublisher.java \
        src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java \
        src/main/java/com/wpanther/document/intake/infrastructure/adapter/out/messaging/EventPublisher.java
git rm src/main/java/com/wpanther/document/intake/domain/event/StartSagaCommand.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/domain/event/DocumentReceivedTraceEvent.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/domain/event/EventStatus.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java 2>/dev/null || true
git add -u
git commit -m "Move Kafka event DTOs from domain/event to application/dto/event, move DocumentEventPublisher to application/port/out"
```

---

## Phase 2 — Merge `application/port/in/` and `application/service/` into `application/usecase/`

### Task 2: Move use-case interfaces and implementation into `application/usecase/`

`application/port/in/` holds the use-case interfaces. `application/service/` holds the implementation. Both move into the same `application/usecase/` package. No interface changes — only package declarations and import statements.

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/application/port/in/SubmitDocumentUseCase.java` → `src/main/java/com/wpanther/document/intake/application/usecase/SubmitDocumentUseCase.java`
- Move: `src/main/java/com/wpanther/document/intake/application/port/in/GetDocumentUseCase.java` → `src/main/java/com/wpanther/document/intake/application/usecase/GetDocumentUseCase.java`
- Move: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java` → `src/main/java/com/wpanther/document/intake/application/usecase/DocumentIntakeApplicationService.java`
- Modify (importers): `DocumentIntakeController.java`, `infrastructure/config/CamelConfig.java` (and any other file importing from `application.port.in` or `application.service`)

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/application/usecase
```

**Step 2: Move `SubmitDocumentUseCase.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.application.port.in;

// After:
package com.wpanther.document.intake.application.usecase;
```

```bash
mv src/main/java/com/wpanther/document/intake/application/port/in/SubmitDocumentUseCase.java \
   src/main/java/com/wpanther/document/intake/application/usecase/SubmitDocumentUseCase.java
```

**Step 3: Move `GetDocumentUseCase.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.application.port.in;

// After:
package com.wpanther.document.intake.application.usecase;
```

```bash
mv src/main/java/com/wpanther/document/intake/application/port/in/GetDocumentUseCase.java \
   src/main/java/com/wpanther/document/intake/application/usecase/GetDocumentUseCase.java
rmdir src/main/java/com/wpanther/document/intake/application/port/in
rmdir src/main/java/com/wpanther/document/intake/application/port 2>/dev/null || true
```

> **Note:** Only remove `application/port` if it is now empty (it may still contain `application/port/out/`). Do NOT delete `application/port/out/` — it contains `XmlValidationPort.java` and `DocumentEventPublisher.java`.

Check before removing:
```bash
ls src/main/java/com/wpanther/document/intake/application/port/
```

If only `out/` remains, leave `application/port/` in place (do not run the last `rmdir`).

**Step 4: Move `DocumentIntakeApplicationService.java` — update package declaration and `implements` imports**

```java
// Before:
package com.wpanther.document.intake.application.service;
// ...
import com.wpanther.document.intake.application.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.port.in.GetDocumentUseCase;

// After:
package com.wpanther.document.intake.application.usecase;
// ...
import com.wpanther.document.intake.application.usecase.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.usecase.GetDocumentUseCase;
```

Since `DocumentIntakeApplicationService` is moving into the same package as the interfaces it implements, the `import` statements for `SubmitDocumentUseCase` and `GetDocumentUseCase` become same-package — you can remove those two import lines entirely (Java does not require explicit imports for classes in the same package). Either approach (remove imports or update paths) is correct; removing them is cleaner.

```bash
mv src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java \
   src/main/java/com/wpanther/document/intake/application/usecase/DocumentIntakeApplicationService.java
rmdir src/main/java/com/wpanther/document/intake/application/service
```

**Step 5: Find all importers of old paths**

```bash
grep -rl "application\.port\.in\.\|application\.service\.DocumentIntake" src/main/
```

Update each found file. Common importers:

For files importing use-case interfaces:
```java
// Before:
import com.wpanther.document.intake.application.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.port.in.GetDocumentUseCase;

// After:
import com.wpanther.document.intake.application.usecase.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.usecase.GetDocumentUseCase;
```

For files importing the application service:
```java
// Before:
import com.wpanther.document.intake.application.service.DocumentIntakeApplicationService;

// After:
import com.wpanther.document.intake.application.usecase.DocumentIntakeApplicationService;
```

**Step 6: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" before continuing.

**Step 7: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 8: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/application/usecase/
git rm src/main/java/com/wpanther/document/intake/application/port/in/SubmitDocumentUseCase.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/application/port/in/GetDocumentUseCase.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java 2>/dev/null || true
git add -u
git commit -m "Merge application/port/in and application/service into application/usecase"
```

---

## Phase 3 — Move `infrastructure/config/` to concern sub-packages

### Task 3: Split config classes into concern-based sub-packages

8 config classes move from flat `infrastructure/config/` into sub-packages by concern. Spring's `@ComponentScan` covers `com.wpanther.document.intake` and all sub-packages — no scan configuration changes needed.

**Files:**
- Move: `CamelConfig.java` → `infrastructure/config/camel/`
- Move: `OutboxConfig.java` → `infrastructure/config/outbox/`
- Move: `OpenApiConfig.java` → `infrastructure/config/openapi/`
- Move: `RateLimitConfig.java` + `RateLimitProperties.java` → `infrastructure/config/ratelimit/`
- Move: `SchemaPathConfig.java` + `ValidationProperties.java` → `infrastructure/config/validation/`
- Move: `SecurityConfig.java` → `infrastructure/config/security/`

**Step 1: Create all sub-package directories**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/camel
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/outbox
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/openapi
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/ratelimit
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/validation
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/config/security
```

**Step 2: Move `CamelConfig.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.camel;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/CamelConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/camel/CamelConfig.java
```

**Step 3: Move `OutboxConfig.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.outbox;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/OutboxConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/outbox/OutboxConfig.java
```

**Step 4: Move `OpenApiConfig.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.openapi;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/OpenApiConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/openapi/OpenApiConfig.java
```

**Step 5: Move `RateLimitConfig.java` and `RateLimitProperties.java` — update package declarations**

For both files:
```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.ratelimit;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/RateLimitConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/ratelimit/RateLimitConfig.java
mv src/main/java/com/wpanther/document/intake/infrastructure/config/RateLimitProperties.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/ratelimit/RateLimitProperties.java
```

Since `RateLimitConfig` likely imports `RateLimitProperties`, check if the import is explicit or same-package. After this move both are in `ratelimit/`, so the import becomes same-package — remove it or update it:
```java
// If RateLimitConfig.java has:
import com.wpanther.document.intake.infrastructure.config.RateLimitProperties;
// Remove this line (they are now in the same package)
```

**Step 6: Move `SchemaPathConfig.java` and `ValidationProperties.java` — update package declarations**

For both files:
```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.validation;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/SchemaPathConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/validation/SchemaPathConfig.java
mv src/main/java/com/wpanther/document/intake/infrastructure/config/ValidationProperties.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/validation/ValidationProperties.java
```

**Step 7: Move `SecurityConfig.java` — update package declaration**

```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After:
package com.wpanther.document.intake.infrastructure.config.security;
```

```bash
mv src/main/java/com/wpanther/document/intake/infrastructure/config/SecurityConfig.java \
   src/main/java/com/wpanther/document/intake/infrastructure/config/security/SecurityConfig.java
```

**Step 8: Find all cross-package importers**

```bash
grep -rl "infrastructure\.config\." src/main/
```

For each found file, update the import path. Common cases:
```java
// Before:
import com.wpanther.document.intake.infrastructure.config.CamelConfig;
import com.wpanther.document.intake.infrastructure.config.RateLimitProperties;
import com.wpanther.document.intake.infrastructure.config.ValidationProperties;
import com.wpanther.document.intake.infrastructure.config.SchemaPathConfig;
// etc.

// After:
import com.wpanther.document.intake.infrastructure.config.camel.CamelConfig;
import com.wpanther.document.intake.infrastructure.config.ratelimit.RateLimitProperties;
import com.wpanther.document.intake.infrastructure.config.validation.ValidationProperties;
import com.wpanther.document.intake.infrastructure.config.validation.SchemaPathConfig;
// etc.
```

Config classes rarely import each other (they import framework types), so most cross-package consumers will be test files (handled in Phase 4) or other config classes that reference `@ConfigurationProperties` types.

**Step 9: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" before continuing.

**Step 10: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 11: Verify flat `infrastructure/config/` is empty**

```bash
find src/main/java/com/wpanther/document/intake/infrastructure/config -maxdepth 1 -name "*.java"
```

Expected: no output (all `.java` files have moved to sub-packages).

**Step 12: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/infrastructure/config/camel/ \
        src/main/java/com/wpanther/document/intake/infrastructure/config/outbox/ \
        src/main/java/com/wpanther/document/intake/infrastructure/config/openapi/ \
        src/main/java/com/wpanther/document/intake/infrastructure/config/ratelimit/ \
        src/main/java/com/wpanther/document/intake/infrastructure/config/validation/ \
        src/main/java/com/wpanther/document/intake/infrastructure/config/security/
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/CamelConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/OutboxConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/OpenApiConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/RateLimitConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/RateLimitProperties.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/SchemaPathConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/ValidationProperties.java 2>/dev/null || true
git rm src/main/java/com/wpanther/document/intake/infrastructure/config/SecurityConfig.java 2>/dev/null || true
git add -u
git commit -m "Move infrastructure/config to concern-based sub-packages (camel, outbox, openapi, ratelimit, validation, security)"
```

---

## Phase 4 — Relocate Test Files and Update JaCoCo

### Task 4: Move test files to mirror new production structure

**Files to move:**

| Old test path | New test path |
|---|---|
| `domain/event/DocumentReceivedTraceEventTest.java` | `application/dto/event/` |
| `domain/event/EventStatusTest.java` | `application/dto/event/` |
| `domain/event/StartSagaCommandTest.java` | `application/dto/event/` |
| `application/service/DocumentIntakeServiceTest.java` | `application/usecase/` |
| `infrastructure/config/CamelConfigTest.java` | `infrastructure/config/camel/` |
| `infrastructure/config/CamelConfigIntegrationTest.java` | `infrastructure/config/camel/` |
| `infrastructure/config/OutboxConfigTest.java` | `infrastructure/config/outbox/` |
| `infrastructure/config/RateLimitConfigTest.java` | `infrastructure/config/ratelimit/` |
| `infrastructure/config/RateLimitPropertiesTest.java` | `infrastructure/config/ratelimit/` |
| `infrastructure/config/SchemaPathConfigTest.java` | `infrastructure/config/validation/` |
| `infrastructure/config/SecurityConfigMethodTest.java` | `infrastructure/config/security/` |
| `infrastructure/config/SecurityDisabledConfigTest.java` | `infrastructure/config/security/` |
| `infrastructure/config/ValidationPropertiesTest.java` | `infrastructure/config/validation/` |

**NOT moved:** all `infrastructure/adapter/` tests, all `domain/model/` and `domain/exception/` tests, `DocumentIntakeServiceApplicationTest.java`.

**Step 1: Create target test directories**

```bash
mkdir -p src/test/java/com/wpanther/document/intake/application/dto/event
mkdir -p src/test/java/com/wpanther/document/intake/application/usecase
mkdir -p src/test/java/com/wpanther/document/intake/infrastructure/config/camel
mkdir -p src/test/java/com/wpanther/document/intake/infrastructure/config/outbox
mkdir -p src/test/java/com/wpanther/document/intake/infrastructure/config/ratelimit
mkdir -p src/test/java/com/wpanther/document/intake/infrastructure/config/validation
mkdir -p src/test/java/com/wpanther/document/intake/infrastructure/config/security
```

**Step 2: Move domain/event test files**

```bash
for f in DocumentReceivedTraceEventTest EventStatusTest StartSagaCommandTest; do
  mv "src/test/java/com/wpanther/document/intake/domain/event/${f}.java" \
     "src/test/java/com/wpanther/document/intake/application/dto/event/${f}.java"
done
rmdir src/test/java/com/wpanther/document/intake/domain/event 2>/dev/null || true
```

Update package declaration in each moved file:
```java
// Before:
package com.wpanther.document.intake.domain.event;

// After:
package com.wpanther.document.intake.application.dto.event;
```

Also update any imports referencing old `domain.event.*` paths in these test files:
```java
// Before:
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.EventStatus;

// After (same package — remove explicit imports, or update to):
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
// etc.
```

**Step 3: Move application service test**

```bash
mv src/test/java/com/wpanther/document/intake/application/service/DocumentIntakeServiceTest.java \
   src/test/java/com/wpanther/document/intake/application/usecase/DocumentIntakeServiceTest.java
rmdir src/test/java/com/wpanther/document/intake/application/service 2>/dev/null || true
```

Update package declaration:
```java
// Before:
package com.wpanther.document.intake.application.service;

// After:
package com.wpanther.document.intake.application.usecase;
```

Update any imports of old `application.service.*` or `application.port.in.*` paths:
```java
// Before:
import com.wpanther.document.intake.application.service.DocumentIntakeApplicationService;
import com.wpanther.document.intake.application.port.in.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.port.in.GetDocumentUseCase;

// After:
import com.wpanther.document.intake.application.usecase.DocumentIntakeApplicationService;
import com.wpanther.document.intake.application.usecase.SubmitDocumentUseCase;
import com.wpanther.document.intake.application.usecase.GetDocumentUseCase;
```

Also update any imports of `domain.event.*` in this test file:
```java
// Before:
import com.wpanther.document.intake.domain.event.StartSagaCommand;
import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.EventStatus;
import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;

// After:
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;
import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.dto.event.EventStatus;
import com.wpanther.document.intake.application.port.out.DocumentEventPublisher;
```

**Step 4: Move config test files**

```bash
# camel/
for f in CamelConfigTest CamelConfigIntegrationTest; do
  mv "src/test/java/com/wpanther/document/intake/infrastructure/config/${f}.java" \
     "src/test/java/com/wpanther/document/intake/infrastructure/config/camel/${f}.java"
done

# outbox/
mv src/test/java/com/wpanther/document/intake/infrastructure/config/OutboxConfigTest.java \
   src/test/java/com/wpanther/document/intake/infrastructure/config/outbox/OutboxConfigTest.java

# ratelimit/
for f in RateLimitConfigTest RateLimitPropertiesTest; do
  mv "src/test/java/com/wpanther/document/intake/infrastructure/config/${f}.java" \
     "src/test/java/com/wpanther/document/intake/infrastructure/config/ratelimit/${f}.java"
done

# validation/
for f in SchemaPathConfigTest ValidationPropertiesTest; do
  mv "src/test/java/com/wpanther/document/intake/infrastructure/config/${f}.java" \
     "src/test/java/com/wpanther/document/intake/infrastructure/config/validation/${f}.java"
done

# security/
for f in SecurityConfigMethodTest SecurityDisabledConfigTest; do
  mv "src/test/java/com/wpanther/document/intake/infrastructure/config/${f}.java" \
     "src/test/java/com/wpanther/document/intake/infrastructure/config/security/${f}.java"
done
```

Update package declaration in each moved config test file. Pattern:
```java
// Before:
package com.wpanther.document.intake.infrastructure.config;

// After (example for camel):
package com.wpanther.document.intake.infrastructure.config.camel;
```

Apply the correct sub-package name for each file (`camel`, `outbox`, `ratelimit`, `validation`, `security`).

**Step 5: Update imports in moved config test files**

Find any remaining old-path imports in config test files:
```bash
grep -rl "infrastructure\.config\." src/test/java/com/wpanther/document/intake/infrastructure/config/
```

Update each occurrence using the mapping from Task 3 Step 8.

**Step 6: Check for any remaining old-path imports across all test files**

```bash
grep -rl "domain\.event\.\|application\.port\.in\.\|application\.service\.\|infrastructure\.config\." src/test/
```

Update all found files. The full mapping:

| Old import pattern | New import pattern |
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
| `infrastructure.config.RateLimitConfig` | `infrastructure.config.ratelimit.RateLimitConfig` |
| `infrastructure.config.RateLimitProperties` | `infrastructure.config.ratelimit.RateLimitProperties` |
| `infrastructure.config.SchemaPathConfig` | `infrastructure.config.validation.SchemaPathConfig` |
| `infrastructure.config.ValidationProperties` | `infrastructure.config.validation.ValidationProperties` |
| `infrastructure.config.SecurityConfig` | `infrastructure.config.security.SecurityConfig` |

**Step 7: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, same test count as before.

### Task 5: Update JaCoCo exclusion patterns in `pom.xml`

**File:** `pom.xml`

**Step 1: Open `pom.xml` and find the `<excludes>` block under `jacoco-maven-plugin`**

```bash
grep -n "exclude\|jacoco" pom.xml | head -40
```

**Step 2: Apply exclusion pattern updates**

Find and update any patterns that reference old paths:

| Old pattern (if present) | New pattern |
|---|---|
| `**/application/port/in/**` | remove (interfaces now in `usecase/`, no longer excluded) |
| `**/infrastructure/config/**` | split into: `**/infrastructure/config/camel/**`, `**/infrastructure/config/outbox/**`, `**/infrastructure/config/openapi/**`, `**/infrastructure/config/ratelimit/**`, `**/infrastructure/config/validation/**`, `**/infrastructure/config/security/**` |

> **Note:** The current JaCoCo config may not have exclusions for `application/port/in/` or `infrastructure/config/` — check first before adding. Only update patterns that actually exist. The key goal is that coverage still hits 83% after the moves.

**Step 3: Run full coverage verification**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS with line coverage ≥ 83%.

If coverage drops below 83%, diagnose:
```bash
mvn verify 2>&1 | grep -A5 "Coverage checks"
```

Common causes: a moved test file still references an old import path and fails to compile (check `mvn test-compile -q`).

**Step 4: Commit**

```bash
git add src/test/java/com/wpanther/document/intake/application/dto/ \
        src/test/java/com/wpanther/document/intake/application/usecase/ \
        src/test/java/com/wpanther/document/intake/infrastructure/config/ \
        pom.xml
git rm -r src/test/java/com/wpanther/document/intake/domain/event/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/document/intake/application/service/ 2>/dev/null || true
git add -u
git commit -m "Relocate test classes to mirror new package structure, update JaCoCo exclusions"
```

---

## Phase 5 — Final Verification

### Task 6: Confirm clean state

**Step 1: No old package references in main source**

```bash
grep -r "document.intake.domain.event"           src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "document.intake.application.port.in"    src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "document.intake.application.service\."  src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "document.intake.infrastructure.config\.[A-Z]" src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
```

Expected: all `CLEAN`.

**Step 2: No old directories remain**

```bash
find src/main/java/com/wpanther/document/intake/domain/event -type f 2>/dev/null
find src/main/java/com/wpanther/document/intake/application/port/in -type f 2>/dev/null
find src/main/java/com/wpanther/document/intake/application/service -type f 2>/dev/null
find src/main/java/com/wpanther/document/intake/infrastructure/config -maxdepth 1 -name "*.java" 2>/dev/null
```

Expected: no output from any command.

**Step 3: Confirm new directories contain files**

```bash
find src/main/java/com/wpanther/document/intake/application/dto/event -name "*.java"
find src/main/java/com/wpanther/document/intake/application/usecase -name "*.java"
find src/main/java/com/wpanther/document/intake/application/port/out -name "*.java"
find src/main/java/com/wpanther/document/intake/infrastructure/config -name "*.java" | sort
```

Expected:
- `dto/event/`: 3 files (`StartSagaCommand`, `DocumentReceivedTraceEvent`, `EventStatus`)
- `usecase/`: 3 files (`SubmitDocumentUseCase`, `GetDocumentUseCase`, `DocumentIntakeApplicationService`)
- `port/out/`: 2 files (`XmlValidationPort`, `DocumentEventPublisher`)
- `infrastructure/config/`: 8 files spread across 6 sub-packages

**Step 4: Run full test suite with coverage**

```bash
mvn verify
```

Expected: BUILD SUCCESS, line coverage ≥ 83%.

**Step 5: Done**

The document-intake-service now matches the canonical hexagonal layout. `domain/` contains only pure domain types (model, repository, exception). `application/usecase/` holds both use-case interfaces and their implementation. `application/dto/event/` holds Kafka wire DTOs. `application/port/out/` holds both outbound ports. `infrastructure/config/` is organized by concern.
