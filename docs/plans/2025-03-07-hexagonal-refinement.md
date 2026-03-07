# Hexagonal Architecture Refinement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor document-intake-service to textbook Hexagonal Architecture by consolidating adapters into infrastructure, reorganizing ports, and eliminating domain model leakage.

**Architecture:** Move ports to appropriate layers (domain for repositories, application for use cases), nest adapters under infrastructure, and rename duplicate DocumentType to TedaDocumentType for clarity.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Maven, JUnit 5, Git

---

## Task 1: Create New Directory Structure

**Files:**
- Create: `src/main/java/com/wpanther/document/intake/domain/repository/`
- Create: `src/main/java/com/wpanther/document/intake/domain/event/port/`
- Create: `src/main/java/com/wpanther/document/intake/application/port/in/`
- Create: `src/main/java/com/wpanther/document/intake/application/port/out/`
- Create: `src/main/java/com/wpanther/document/intake/infrastructure/adapter/`

**Step 1: Create all new directories**

```bash
mkdir -p src/main/java/com/wpanther/document/intake/domain/repository
mkdir -p src/main/java/com/wpanther/document/intake/domain/event/port
mkdir -p src/main/java/com/wpanther/document/intake/application/port/in
mkdir -p src/main/java/com/wpanther/document/intake/application/port/out
mkdir -p src/main/java/com/wpanther/document/intake/infrastructure/adapter
```

**Step 2: Verify directories created**

Run: `find src/main/java/com/wpanther/document/intake -type d | grep -E "(repository|event/port|application/port|infrastructure/adapter)" | sort`
Expected: Output shows 5 new directories

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor(hexagonal): create new directory structure for port reorganization"
```

---

## Task 2: Move DocumentRepository to domain/repository

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/domain/port/out/DocumentRepository.java` → `src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java`
- Modify: `src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java`

**Step 1: Move file with git mv**

```bash
git mv src/main/java/com/wpanther/document/intake/domain/port/out/DocumentRepository.java src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java
```

**Step 2: Update package declaration**

Run: `sed -i 's/package com.wpanther.document.intake.domain.port.out;/package com.wpanther.document.intake.domain.repository;/g' src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java`

**Step 3: Verify package change**

Run: `head -1 src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java`
Expected: `package com.wpanther.document.intake.domain.repository;`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/domain/repository/DocumentRepository.java
git commit -m "refactor(hexagonal): move DocumentRepository port to domain/repository"
```

---

## Task 3: Move Inbound Ports to application/port/in

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/domain/port/in/GetDocumentUseCase.java` → `src/main/java/com/wpanther/document/intake/application/port/in/GetDocumentUseCase.java`
- Move: `src/main/java/com/wpanther/document/intake/domain/port/in/SubmitDocumentUseCase.java` → `src/main/java/com/wpanther/document/intake/application/port/in/SubmitDocumentUseCase.java`

**Step 1: Move inbound port files**

```bash
git mv src/main/java/com/wpanther/document/intake/domain/port/in/GetDocumentUseCase.java src/main/java/com/wpanther/document/intake/application/port/in/
git mv src/main/java/com/wpanther/document/intake/domain/port/in/SubmitDocumentUseCase.java src/main/java/com/wpanther/document/intake/application/port/in/
```

**Step 2: Update package declarations**

```bash
sed -i 's/package com.wpanther.document.intake.domain.port.in;/package com.wpanther.document.intake.application.port.in;/g' src/main/java/com/wpanther/document/intake/application/port/in/*.java
```

**Step 3: Verify package changes**

Run: `head -1 src/main/java/com/wpanther/document/intake/application/port/in/*.java`
Expected: Both files show `package com.wpanther.document.intake.application.port.in;`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/application/port/in/
git commit -m "refactor(hexagonal): move inbound ports to application/port/in"
```

---

## Task 4: Move XmlValidationPort to application/port/out

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/domain/port/out/XmlValidationPort.java` → `src/main/java/com/wpanther/document/intake/application/port/out/XmlValidationPort.java`

**Step 1: Move file**

```bash
git mv src/main/java/com/wpanther/document/intake/domain/port/out/XmlValidationPort.java src/main/java/com/wpanther/document/intake/application/port/out/XmlValidationPort.java
```

**Step 2: Update package declaration**

Run: `sed -i 's/package com.wpanther.document.intake.domain.port.out;/package com.wpanther.document.intake.application.port.out;/g' src/main/java/com/wpanther/document/intake/application/port/out/XmlValidationPort.java`

**Step 3: Verify package change**

Run: `head -1 src/main/java/com/wpanther/document/intake/application/port/out/XmlValidationPort.java`
Expected: `package com.wpanther.document.intake.application.port.out;`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/application/port/out/
git commit -m "refactor(hexagonal): move XmlValidationPort to application/port/out"
```

---

## Task 5: Move DocumentEventPublisher to domain/event/port

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/domain/port/out/DocumentEventPublisher.java` → `src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java`

**Step 1: Move file**

```bash
git mv src/main/java/com/wpanther/document/intake/domain/port/out/DocumentEventPublisher.java src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java
```

**Step 2: Update package declaration**

Run: `sed -i 's/package com.wpanther.document.intake.domain.port.out;/package com.wpanther.document.intake.domain.event.port;/g' src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java`

**Step 3: Verify package change**

Run: `head -1 src/main/java/com/wpanther/document/intake/domain/event/port/DocumentEventPublisher.java`
Expected: `package com.wpanther.document.intake.domain.event.port;`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/domain/event/port/
git commit -m "refactor(hexagonal): move DocumentEventPublisher to domain/event/port"
```

---

## Task 6: Move adapter/ to infrastructure/adapter/

**Files:**
- Move: `src/main/java/com/wpanther/document/intake/adapter/in/` → `src/main/java/com/wpanther/document/intake/infrastructure/adapter/in/`
- Move: `src/main/java/com/wpanther/document/intake/adapter/out/` → `src/main/java/com/wpanther/document/intake/infrastructure/adapter/out/`

**Step 1: Move adapter directories**

```bash
git mv src/main/java/com/wpanther/document/intake/adapter/in src/main/java/com/wpanther/document/intake/infrastructure/adapter/
git mv src/main/java/com/wpanther/document/intake/adapter/out src/main/java/com/wpanther/document/intake/infrastructure/adapter/
```

**Step 2: Update all package declarations in infrastructure/adapter/**

```bash
find src/main/java/com/wpanther/document/intake/infrastructure/adapter -name "*.java" -exec sed -i 's/package com\.wpanther\.document\.intake\.adapter\./package com.wpanther.document.intake.infrastructure.adapter./g' {} \;
```

**Step 3: Verify package changes**

Run: `find src/main/java/com/wpanther/document/intake/infrastructure/adapter -name "*.java" -exec head -1 {} \; | grep package | sort | uniq`
Expected: All packages start with `com.wpanther.document.intake.infrastructure.adapter.`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/infrastructure/adapter/
git commit -m "refactor(hexagonal): move adapter/ inside infrastructure/"
```

---

## Task 7: Rename DocumentType to TedaDocumentType

**Files:**
- Rename: `src/main/java/com/wpanther/document/intake/infrastructure/validation/DocumentType.java` → `src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java`
- Modify: `src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java`

**Step 1: Rename file**

```bash
git mv src/main/java/com/wpanther/document/intake/infrastructure/validation/DocumentType.java src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java
```

**Step 2: Update class name inside file**

Run: `sed -i 's/public enum DocumentType {/public enum TedaDocumentType {/g' src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java`

**Step 3: Verify class name**

Run: `grep "^public enum" src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java`
Expected: `public enum TedaDocumentType {`

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/infrastructure/validation/TedaDocumentType.java
git commit -m "refactor(hexagonal): rename DocumentType to TedaDocumentType for clarity"
```

---

## Task 8: Update Imports in Main Code - Application Layer

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java`

**Step 1: Update imports in DocumentIntakeApplicationService**

```bash
sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.in\./import com.wpanther.document.intake.application.port.in./g' src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentRepository;/import com.wpanther.document.intake.domain.repository.DocumentRepository;/g' src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.XmlValidationPort;/import com.wpanther.document.intake.application.port.out.XmlValidationPort;/g' src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentEventPublisher;/import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;/g' src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
sed -i 's/import com\.wpanther\.document\.intake\.adapter\./import com.wpanther.document.intake.infrastructure.adapter./g' src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
```

**Step 2: Verify imports**

Run: `grep "^import" src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java | grep -E "(application\.port|domain\.repository|domain\.event\.port|infrastructure\.adapter)"`
Expected: All imports updated correctly

**Step 3: Compile check**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No compilation errors related to DocumentIntakeApplicationService

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/application/service/DocumentIntakeApplicationService.java
git commit -m "refactor(hexagonal): update imports in DocumentIntakeApplicationService"
```

---

## Task 9: Update Imports in Infrastructure Layer - Config Classes

**Files:**
- Find: All files in `src/main/java/com/wpanther/document/intake/infrastructure/config/`
- Find: All files in `src/main/java/com/wpanther/document/intake/infrastructure/security/`

**Step 1: Find files with old imports**

```bash
grep -r "import com\.wpanther\.document\.intake\.adapter\." src/main/java/com/wpanther/document/intake/infrastructure/config/ src/main/java/com/wpanther/document/intake/infrastructure/security/ 2>/dev/null || echo "No matches found"
```

**Step 2: Update adapter imports in infrastructure/config and infrastructure/security**

```bash
find src/main/java/com/wpanther/document/intake/infrastructure/config/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.adapter\./import com.wpanther.document.intake.infrastructure.adapter./g' {} \;
find src/main/java/com/wpanther/document/intake/infrastructure/security/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.adapter\./import com.wpanther.document.intake.infrastructure.adapter./g' {} \;
```

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/infrastructure/config/ src/main/java/com/wpanther/document/intake/infrastructure/security/
git commit -m "refactor(hexagonal): update adapter imports in infrastructure config"
```

---

## Task 10: Update Imports in Infrastructure Adapter Layer

**Files:**
- All files in `src/main/java/com/wpanther/document/intake/infrastructure/adapter/`

**Step 1: Update domain/port imports to new locations**

```bash
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.in\./import com.wpanther.document.intake.application.port.in./g' {} \;
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentRepository;/import com.wpanther.document.intake.domain.repository.DocumentRepository;/g' {} \;
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.XmlValidationPort;/import com.wpanther.document.intake.application.port.out.XmlValidationPort;/g' {} \;
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentEventPublisher;/import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;/g' {} \;
```

**Step 2: Update DocumentType to TedaDocumentType references**

```bash
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.infrastructure\.validation\.DocumentType;/import com.wpanther.document.intake.infrastructure.validation.TedaDocumentType;/g' {} \;
find src/main/java/com/wpanther/document/intake/infrastructure/adapter/ -name "*.java" -exec sed -i 's/\bDocumentType\b/TedaDocumentType/g' {} \;
```

**Step 3: Verify imports**

Run: `grep -r "import com.wpanther.document.intake.domain.port" src/main/java/com/wpanther/document/intake/infrastructure/adapter/ 2>/dev/null || echo "No old imports found - good"`
Expected: "No old imports found - good"

**Step 4: Compile check**

Run: `mvn compile -q 2>&1 | grep -E "(ERROR|BUILD)" | head -10`
Expected: BUILD SUCCESS or no errors

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/document/intake/infrastructure/adapter/
git commit -m "refactor(hexagonal): update imports in infrastructure adapter layer"
```

---

## Task 11: Update Imports in Domain Event Tests

**Files:**
- All test files referencing moved ports

**Step 1: Find test files with old imports**

```bash
grep -r "import com\.wpanther\.document\.intake\.domain\.port" src/test/java/ 2>/dev/null | cut -d: -f1 | sort -u
```

**Step 2: Update imports in all test files**

```bash
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.in\./import com.wpanther.document.intake.application.port.in./g' {} \;
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentRepository;/import com.wpanther.document.intake.domain.repository.DocumentRepository;/g' {} \;
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.XmlValidationPort;/import com.wpanther.document.intake.application.port.out.XmlValidationPort;/g' {} \;
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.domain\.port\.out\.DocumentEventPublisher;/import com.wpanther.document.intake.domain.event.port.DocumentEventPublisher;/g' {} \;
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.adapter\./import com.wpanther.document.intake.infrastructure.adapter./g' {} \;
```

**Step 3: Update DocumentType to TedaDocumentType in tests**

```bash
find src/test/java/ -name "*.java" -exec sed -i 's/import com\.wpanther\.document\.intake\.infrastructure\.validation\.DocumentType;/import com.wpanther.document.intake.infrastructure.validation.TedaDocumentType;/g' {} \;
```

**Note:** Do NOT replace `DocumentType` references with `TedaDocumentType` in tests that use `com.wpanther.document.intake.domain.model.DocumentType` (the pure domain enum).

**Step 4: Verify no old imports remain**

Run: `grep -r "import com.wpanther.document.intake.domain.port" src/test/java/ 2>/dev/null || echo "No old imports found"`
Expected: "No old imports found"

**Step 5: Commit**

```bash
git add src/test/java/
git commit -m "refactor(hexagonal): update imports in test files"
```

---

## Task 12: Delete Empty domain/port Directory

**Files:**
- Remove: `src/main/java/com/wpanther/document/intake/domain/port/` (now empty)

**Step 1: Verify directory is empty**

Run: `find src/main/java/com/wpanther/document/intake/domain/port/ -type f 2>/dev/null || echo "Directory empty or doesn't exist"`
Expected: No files found

**Step 2: Remove empty directory**

```bash
rm -rf src/main/java/com/wpanther/document/intake/domain/port/
```

**Step 3: Verify removal**

Run: `ls -la src/main/java/com/wpanther/document/intake/domain/ | grep port || echo "port directory removed"`
Expected: "port directory removed"

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor(hexagonal): remove empty domain/port directory"
```

---

## Task 13: Run Full Test Suite

**Files:** None (verification task)

**Step 1: Run tests**

```bash
mvn test 2>&1 | tail -50
```

**Step 2: Verify test results**

Run: `mvn test 2>&1 | grep -E "(Tests run|BUILD)" | tail -3`
Expected: `Tests run: 342, Failures: 0, Errors: 0` and `BUILD SUCCESS`

**Step 3: If tests fail, investigate**

Run: `mvn test 2>&1 | grep -E "(COMPILATION ERROR|cannot find symbol)" | head -20`
Expected: No errors

**Step 4: No commit needed for this task**

---

## Task 14: Final Verification and Summary

**Files:** None (verification task)

**Step 1: Verify final package structure**

```bash
find src/main/java/com/wpanther/document/intake -type d | sort | sed 's|src/main/java/com/wpanther/document/intake/||' | head -30
```

Expected output:
```
.
adapter/in/metrics
adapter/in/web
adapter/out/health
adapter/out/messaging
adapter/out/persistence
adapter/out/persistence/outbox
adapter/out/validation
application/port/in
application/port/out
application/service
domain/event
domain/event/port
domain/exception
domain/model
domain/repository
infrastructure/adapter
infrastructure/adapter/in
infrastructure/adapter/in/metrics
infrastructure/adapter/in/web
infrastructure/adapter/out
infrastructure/adapter/out/health
infrastructure/adapter/out/messaging
infrastructure/adapter/out/persistence
infrastructure/adapter/out/persistence/outbox
infrastructure/adapter/out/validation
infrastructure/config
infrastructure/security
infrastructure/validation
```

**Step 2: Verify dependency graph (no domain dependencies on infrastructure/application)**

Run: `grep -r "import com.wpanther.document.intake.application" src/main/java/com/wpanther/document/intake/domain/ 2>/dev/null || echo "Clean - no domain imports from application"`
Run: `grep -r "import com.wpanther.document.intake.infrastructure" src/main/java/com/wpanther/document/intake/domain/ 2>/dev/null || echo "Clean - no domain imports from infrastructure"`
Expected: Both show "Clean"

**Step 3: Summary commit (if all tests pass)**

```bash
git add -A
git commit -m "refactor(hexagonal): complete package reorganization to textbook hexagonal architecture

- Moved domain/port/out/DocumentRepository → domain/repository/
- Moved domain/port/in/* → application/port/in/
- Moved domain/port/out/XmlValidationPort → application/port/out/
- Moved domain/port/out/DocumentEventPublisher → domain/event/port/
- Moved adapter/ → infrastructure/adapter/
- Renamed infrastructure/validation/DocumentType → TedaDocumentType
- Updated all imports across main and test code
- Removed empty domain/port/ directory

Architecture now follows textbook Hexagonal pattern:
- domain/ has zero framework dependencies
- application/ orchestrates use cases
- infrastructure/ contains all adapters and external concerns

All 342 tests pass"
```

**Step 4: Push to remote**

```bash
git push
```

---

## Completion Checklist

- [ ] All 14 tasks completed
- [ ] All 342 tests pass
- [ ] No compilation errors
- [ ] Package structure verified
- [ ] Dependency graph verified (domain has no outward dependencies)
- [ ] Code pushed to remote

---

## Notes

- Each commit is atomic and can be reverted independently
- Tests should pass after Task 10 (infrastructure adapter updates)
- The domain/model/DocumentType (pure enum) remains unchanged
- Only infrastructure/validation/DocumentType was renamed to TedaDocumentType
