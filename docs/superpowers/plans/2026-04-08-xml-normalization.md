# XML Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Normalize XML input to compact single-line format (strip outer whitespace, collapse inter-element whitespace) before validation, database persistence, and Kafka publishing.

**Architecture:** Add one normalization line at the top of `DocumentIntakeApplicationService.submitDocument()` — this single entry point covers both REST and Kafka paths. The normalized string flows through the entire method: extraction, validation, persistence, and outbox event publishing.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito

---

## File Map

| File | Action | What changes |
|------|--------|-------------|
| `src/main/java/com/wpanther/document/intake/application/usecase/DocumentIntakeApplicationService.java` | Modify (line 74) | Add normalization as first operation in `submitDocument()` |
| `src/test/java/com/wpanther/document/intake/application/usecase/DocumentIntakeServiceTest.java` | Modify | Add normalization test; fix two `verify` calls; fix null-XML test |

---

## Task 1: Add normalization test (failing first)

**Files:**
- Modify: `src/test/java/com/wpanther/document/intake/application/usecase/DocumentIntakeServiceTest.java`

- [ ] **Step 1: Add the failing test**

Open `DocumentIntakeServiceTest.java`. After the last test in the `// ==================== Edge Case Tests ====================` section (around line 292), add this new test:

```java
@Test
@DisplayName("Submit document normalizes XML before processing - strips inter-element whitespace")
void testSubmitDocumentNormalizesXmlContent() {
    String indentedXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:TaxInvoice_CrossIndustryInvoice
            xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
            xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2">
            <rsm:ExchangedDocument>
                <ram:ID>NORM-001</ram:ID>
            </rsm:ExchangedDocument>
        </rsm:TaxInvoice_CrossIndustryInvoice>
        """;

    ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
    when(validationService.extractDocumentNumber(xmlCaptor.capture())).thenReturn("NORM-001");

    documentIntakeService.submitDocument(indentedXml, DEFAULT_SOURCE, "corr-norm");

    String captured = xmlCaptor.getValue();
    // Must not contain whitespace between tags
    assertThat(captured).doesNotContainPattern(">\\s+<");
    // Must be a single line (no newlines)
    assertThat(captured).doesNotContain("\n");
    // Content inside tags must be preserved
    assertThat(captured).contains("<ram:ID>NORM-001</ram:ID>");
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -Dtest=DocumentIntakeServiceTest#testSubmitDocumentNormalizesXmlContent -pl .
```

Expected: **FAIL** — the captured XML will still contain `>\n` patterns because normalization hasn't been added yet.

---

## Task 2: Implement the normalization

**Files:**
- Modify: `src/main/java/com/wpanther/document/intake/application/usecase/DocumentIntakeApplicationService.java`

- [ ] **Step 1: Add normalization at the top of `submitDocument()`**

In `DocumentIntakeApplicationService.java`, the method signature is at line 74:
```java
public IncomingDocument submitDocument(String xmlContent, String source, String correlationId) {
```

Add the normalization as the **very first line** inside the method body (before `long startTime`):

```java
public IncomingDocument submitDocument(String xmlContent, String source, String correlationId) {
    // Normalize XML: strip outer whitespace and collapse inter-element whitespace
    xmlContent = xmlContent.strip().replaceAll(">\\s+<", "><");
    long startTime = System.currentTimeMillis();
    // ... rest of method unchanged
```

The full method opening after the edit:
```java
@Transactional
@Override
public IncomingDocument submitDocument(String xmlContent, String source, String correlationId) {
    // Normalize XML: strip outer whitespace and collapse inter-element whitespace
    xmlContent = xmlContent.strip().replaceAll(">\\s+<", "><");
    long startTime = System.currentTimeMillis();
    log.info("Submitting document from source: {} with correlationId: {}", source, correlationId);
```

- [ ] **Step 2: Run the new test to verify it now passes**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -Dtest=DocumentIntakeServiceTest#testSubmitDocumentNormalizesXmlContent -pl .
```

Expected: **PASS**

---

## Task 3: Fix broken existing tests

**Files:**
- Modify: `src/test/java/com/wpanther/document/intake/application/usecase/DocumentIntakeServiceTest.java`

The normalization changes what string is passed to `validationService` methods and what happens with null input. Three existing tests need updating.

**Why they break:**

1. `testSubmitInvoiceExtractsDocumentNumber` (line ~201): Verifies `extractDocumentNumber(VALID_XML)` but after normalization, the normalized form of `VALID_XML` is passed — the exact string no longer matches.
2. `testSubmitInvoiceExtractsDocumentType` (line ~238): Same reason for `extractDocumentType(VALID_XML)`.
3. `testSubmitInvoiceWithNullXml` (line ~259): Previously `extractDocumentNumber(null)` was called first (then NPE in the mock). Now `null.strip()` throws NPE before `extractDocumentNumber` is ever called — the `verify` is no longer valid.

- [ ] **Step 1: Fix `testSubmitInvoiceExtractsDocumentNumber`**

Find:
```java
@Test
@DisplayName("Submit document extracts document number")
void testSubmitInvoiceExtractsDocumentNumber() {
    documentIntakeService.submitDocument(VALID_XML, DEFAULT_SOURCE, "corr-123");

    verify(validationService).extractDocumentNumber(VALID_XML);
}
```

Replace with:
```java
@Test
@DisplayName("Submit document extracts document number")
void testSubmitInvoiceExtractsDocumentNumber() {
    documentIntakeService.submitDocument(VALID_XML, DEFAULT_SOURCE, "corr-123");

    verify(validationService).extractDocumentNumber(any());
}
```

- [ ] **Step 2: Fix `testSubmitInvoiceExtractsDocumentType`**

Find:
```java
@Test
@DisplayName("Submit document extracts document type")
void testSubmitInvoiceExtractsDocumentType() {
    documentIntakeService.submitDocument(VALID_XML, DEFAULT_SOURCE, "corr-123");

    verify(validationService).extractDocumentType(VALID_XML);
}
```

Replace with:
```java
@Test
@DisplayName("Submit document extracts document type")
void testSubmitInvoiceExtractsDocumentType() {
    documentIntakeService.submitDocument(VALID_XML, DEFAULT_SOURCE, "corr-123");

    verify(validationService).extractDocumentType(any());
}
```

- [ ] **Step 3: Fix `testSubmitInvoiceWithNullXml`**

Find:
```java
@Test
@DisplayName("Submit document with null XML throws exception")
void testSubmitInvoiceWithNullXml() {
    assertThatThrownBy(() -> documentIntakeService.submitDocument(null, DEFAULT_SOURCE, "corr-123"))
        .isInstanceOf(NullPointerException.class);

    verify(validationService).extractDocumentNumber(null);
    verify(documentRepository, never()).save(any());
}
```

Replace with:
```java
@Test
@DisplayName("Submit document with null XML throws NullPointerException before validation")
void testSubmitInvoiceWithNullXml() {
    assertThatThrownBy(() -> documentIntakeService.submitDocument(null, DEFAULT_SOURCE, "corr-123"))
        .isInstanceOf(NullPointerException.class);

    // Normalization (strip) throws NPE before extractDocumentNumber is called
    verify(validationService, never()).extractDocumentNumber(any());
    verify(documentRepository, never()).save(any());
}
```

- [ ] **Step 4: Run the full test class to verify all tests pass**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -Dtest=DocumentIntakeServiceTest -pl .
```

Expected: **All tests PASS** with output like:
```
Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

---

## Task 4: Run full test suite and commit

- [ ] **Step 1: Run all tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
mvn test -pl .
```

Expected: All tests pass. If any test fails because it asserts exact `xmlContent` containing indentation or newlines, apply the same pattern: change exact-string assertions/verifies to use `any()` or compute the normalized expected value.

- [ ] **Step 2: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-intake-service
git add src/main/java/com/wpanther/document/intake/application/usecase/DocumentIntakeApplicationService.java \
        src/test/java/com/wpanther/document/intake/application/usecase/DocumentIntakeServiceTest.java
git commit -m "feat: normalize XML input before validation and persistence"
```
