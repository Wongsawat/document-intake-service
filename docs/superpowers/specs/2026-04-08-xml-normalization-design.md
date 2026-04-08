# XML Normalization Design

**Date:** 2026-04-08
**Service:** document-intake-service
**Status:** Approved

## Problem

XML documents submitted via REST or Kafka may contain indentation, line breaks, and inter-element whitespace. This extra whitespace increases:
- Database storage size (`incoming_documents.xml_content` TEXT column)
- Kafka message size (`StartSagaCommand.xmlContent` in outbox → `saga.commands.orchestrator`)
- Unnecessary variation in stored content

## Goal

Normalize XML input to compact single-line format before any validation, persistence, or event publishing occurs.

## Decision

**Approach:** Regex-based inter-element whitespace collapse at the application service entry point.

**Location:** First operation in `DocumentIntakeApplicationService.submitDocument()`, before any other processing.

**Implementation:**
```java
xmlContent = xmlContent.strip().replaceAll(">\\s+<", "><");
```

- `strip()` removes leading/trailing whitespace (including BOM-adjacent spaces)
- `replaceAll(">\\s+<", "><")` collapses all whitespace (spaces, tabs, newlines) between XML tags

## Why This Approach

- **Simple:** One line, no extra dependencies
- **Fast:** Single regex pass before validation
- **Safe:** Thai e-Tax XML has no mixed content (text nodes alongside child elements), so inter-element whitespace is always non-significant
- **Single entry point:** Covers both REST and Kafka paths since both call `submitDocument()`
- **Validation unaffected:** XSD and Schematron validators operate on XML logical structure, not formatting

## Alternatives Considered

| Approach | Reason Rejected |
|----------|----------------|
| JAXB round-trip (unmarshal → marshal) | Requires document type detection before normalization; adds latency |
| `javax.xml.transform` with `INDENT=no` | More robust for edge cases but adds overhead; overkill for this use case |
| Normalize at Camel route level | Two places to maintain; misses future entry points |

## Scope of Change

| File | Change |
|------|--------|
| `application/usecase/DocumentIntakeApplicationService.java` | Add 1-line normalization at top of `submitDocument()` |

No other files change.

## Test Impact

Tests asserting that stored or published `xmlContent` contains indentation/newlines must be updated to expect compact XML. Tests covering business logic (validation, state transitions, saga command content) are unaffected.
