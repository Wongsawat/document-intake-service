package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.domain.service.XmlValidationService;
import com.wpanther.etax.generated.abbreviatedtaxinvoice.rsm.AbbreviatedTaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.debitcreditnote.rsm.DebitCreditNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.invoice.rsm.Invoice_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.receipt.rsm.Receipt_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.taxinvoice.rsm.TaxInvoice_CrossIndustryInvoiceType;
import com.wpanther.etax.validation.DocumentSchematron;
import com.wpanther.etax.validation.SchematronError;
import com.wpanther.etax.validation.SchematronValidationResult;
import com.wpanther.etax.validation.SchematronValidator;
import com.wpanther.etax.validation.SchematronValidatorImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

/**
 * Implementation of XmlValidationService that provides three-layer validation:
 * 1. XML well-formedness (JAXB unmarshaling)
 * 2. XSD schema validation via JAXB with schema
 * 3. Schematron business rules validation via teda library
 * <p>
 * This service validates Thai e-Tax documents against XSD schemas and Schematron rules
 * bundled in the teda library, using JAXB for strongly-typed document processing.
 * <p>
 * The teda library uses an interface/implementation pattern where JAXB contexts
 * must be initialized with .impl packages for proper unmarshaling.
 */
@Slf4j
@Service
public class XmlValidationServiceImpl implements XmlValidationService {

    // Thread-safe cached JAXB contexts and schemas (initialized once at startup)
    private final Map<DocumentType, JAXBContext> jaxbContexts;
    private final Map<DocumentType, Schema> schemas;
    private final SchematronValidator schematronValidator;
    private final DocumentBuilderFactory documentBuilderFactory;

    public XmlValidationServiceImpl() {
        log.info("Initializing XmlValidationService...");
        long startTime = System.currentTimeMillis();

        this.jaxbContexts = initializeJaxbContexts();
        this.schemas = initializeSchemas();
        this.schematronValidator = new SchematronValidatorImpl();
        this.documentBuilderFactory = createDocumentBuilderFactory();

        long duration = System.currentTimeMillis() - startTime;
        log.info("XmlValidationService initialized in {}ms with {} JAXB contexts and {} schemas",
            duration, jaxbContexts.size(), schemas.size());
    }

    @Override
    public ValidationResult validate(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return ValidationResult.invalid(List.of("XML content is null or empty"));
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            // Step 1: Unmarshal XML with JAXB (includes XSD validation and document type detection)
            ValidationErrorHandler errorHandler = new ValidationErrorHandler();
            UnmarshalResult unmarshalResult = unmarshalXml(xmlContent, null, errorHandler);

            // Collect JAXB unmarshaling errors
            errors.addAll(errorHandler.getErrors());
            warnings.addAll(errorHandler.getWarnings());

            if (!errors.isEmpty()) {
                return ValidationResult.invalid(errors, warnings);
            }

            DocumentType docType = unmarshalResult.documentType;
            Object jaxbObject = unmarshalResult.jaxbObject;

            if (docType == null) {
                return ValidationResult.invalid(List.of("Unable to detect document type from XML"));
            }
            log.debug("Detected document type: {}", docType);

            // Step 2: Schematron business rules validation (only if XSD passed)
            if (errors.isEmpty()) {
                applySchematronValidation(xmlContent, docType, errors, warnings);
            }

        } catch (Exception e) {
            errors.add("Unexpected validation error: " + e.getMessage());
            log.error("Unexpected validation error", e);
        }

        // Return result
        if (errors.isEmpty()) {
            return warnings.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.validWithWarnings(warnings);
        } else {
            return ValidationResult.invalid(errors, warnings);
        }
    }

    @Override
    public String extractInvoiceNumber(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return null;
        }

        try {
            ValidationErrorHandler errorHandler = new ValidationErrorHandler();
            UnmarshalResult result = unmarshalXml(xmlContent, null, errorHandler);

            if (result.jaxbObject == null || errorHandler.hasErrors()) {
                return null;
            }

            return extractInvoiceNumberFromJaxb(result.jaxbObject);

        } catch (Exception e) {
            log.error("Failed to extract invoice number", e);
            return null;
        }
    }

    @Override
    public DocumentType extractDocumentType(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return null;
        }

        try {
            ValidationErrorHandler errorHandler = new ValidationErrorHandler();
            UnmarshalResult result = unmarshalXml(xmlContent, null, errorHandler);

            if (result.jaxbObject == null || errorHandler.hasErrors()) {
                // Fallback to DOM-based detection if JAXB unmarshaling fails
                Document doc = parseXmlDom(xmlContent, new ArrayList<>());
                if (doc != null) {
                    return detectDocumentTypeFromDom(doc);
                }
                return null;
            }

            return result.documentType;

        } catch (Exception e) {
            log.error("Failed to extract document type", e);
            return null;
        }
    }

    /**
     * Result of JAXB unmarshaling containing both the object and detected document type.
     */
    private record UnmarshalResult(Object jaxbObject, DocumentType documentType) {}

    /**
     * Unmarshal XML content using JAXB.
     * Handles JAXBElement wrapper and detects document type from unmarshaled object.
     *
     * @param xmlContent the XML content to unmarshal
     * @param docType optional document type hint (null to auto-detect)
     * @param errorHandler validation event handler for collecting errors
     * @return the unmarshaled object with detected document type
     * @throws JAXBException if unmarshaling fails catastrophically
     */
    private UnmarshalResult unmarshalXml(String xmlContent, DocumentType docType,
                                          ValidationErrorHandler errorHandler) throws JAXBException {
        // Try each document type until we find one that works
        if (docType != null) {
            return unmarshalWithDocumentType(xmlContent, docType, errorHandler);
        }

        // Auto-detect document type by trying each context
        JAXBException lastException = null;

        for (DocumentType type : DocumentType.values()) {
            try {
                return unmarshalWithDocumentType(xmlContent, type, new ValidationErrorHandler());
            } catch (UnmarshalException e) {
                // Try next document type
                lastException = e;
                log.debug("Failed to unmarshal as {}: {}", type, e.getMessage());
            }
        }

        // If all failed, try TaxInvoice as default with detailed error reporting
        log.warn("Could not auto-detect document type, trying TAX_INVOICE as default");
        try {
            return unmarshalWithDocumentType(xmlContent, DocumentType.TAX_INVOICE, errorHandler);
        } catch (UnmarshalException e) {
            if (lastException != null) {
                throw lastException;
            }
            throw e;
        }
    }

    /**
     * Unmarshal XML content with a specific document type's JAXB context.
     */
    private UnmarshalResult unmarshalWithDocumentType(String xmlContent, DocumentType docType,
                                                       ValidationErrorHandler errorHandler) throws JAXBException {
        JAXBContext jaxbContext = jaxbContexts.get(docType);
        if (jaxbContext == null) {
            throw new JAXBException("No JAXB context available for " + docType);
        }

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        // Set schema for XSD validation
        Schema schema = schemas.get(docType);
        if (schema != null) {
            unmarshaller.setSchema(schema);
        }

        // Set validation event handler
        unmarshaller.setEventHandler(errorHandler);

        // Unmarshal XML
        Object result = unmarshaller.unmarshal(new StringReader(xmlContent));

        // Handle JAXBElement wrapper (common when no @XmlRootElement annotation)
        if (result instanceof JAXBElement) {
            JAXBElement<?> jaxbElement = (JAXBElement<?>) result;
            result = jaxbElement.getValue();
        }

        // Detect document type from unmarshaled object class
        DocumentType detectedType = DocumentType.fromJaxbClass(result.getClass());
        if (detectedType == null) {
            detectedType = docType; // Fallback to the type we tried
        }

        return new UnmarshalResult(result, detectedType);
    }

    /**
     * Extract invoice number from JAXB object using strategy pattern.
     * Uses DocumentType-specific extractor for type-safe invoice number extraction.
     */
    private String extractInvoiceNumberFromJaxb(Object jaxbObject) {
        try {
            if (jaxbObject == null) {
                return null;
            }

            // Get document type from JAXB object class
            DocumentType docType = DocumentType.fromJaxbClass(jaxbObject.getClass());
            if (docType == null) {
                log.debug("Could not determine document type for invoice number extraction");
                return null;
            }

            // Use strategy to extract invoice number
            return docType.getInvoiceNumberExtractor().extractInvoiceNumber(jaxbObject);

        } catch (Exception e) {
            log.error("Failed to extract invoice number from JAXB object", e);
            return null;
        }
    }

    /**
     * Initialize JAXB contexts for all document types.
     * Uses implementation packages (.impl) as required by teda library's interface/implementation pattern.
     */
    private Map<DocumentType, JAXBContext> initializeJaxbContexts() {
        Map<DocumentType, JAXBContext> contextMap = new HashMap<>();

        for (DocumentType type : DocumentType.values()) {
            try {
                String contextPath = type.getImplementationContextPath();
                JAXBContext jaxbContext = JAXBContext.newInstance(contextPath);
                contextMap.put(type, jaxbContext);
                log.info("Initialized JAXB context for {} using: {}", type, contextPath);
            } catch (JAXBException e) {
                log.error("Failed to initialize JAXB context for {}: {}", type, e.getMessage());
            }
        }

        return contextMap;
    }

    /**
     * Initialize XSD schemas for all document types.
     * Uses URL-based loading to allow schema factory to resolve relative imports.
     */
    private Map<DocumentType, Schema> initializeSchemas() {
        Map<DocumentType, Schema> schemaMap = new HashMap<>();
        SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);

        for (DocumentType type : DocumentType.values()) {
            try {
                String schemaPath = type.getSchemaPath();
                java.net.URL schemaUrl = getClass().getClassLoader().getResource(schemaPath);

                if (schemaUrl != null) {
                    Schema schema = schemaFactory.newSchema(schemaUrl);
                    schemaMap.put(type, schema);
                    log.info("Loaded XSD schema for {} from {}", type, schemaPath);
                } else {
                    log.warn("Schema file not found: {}. XSD validation will be skipped for {}", schemaPath, type);
                }
            } catch (Exception e) {
                log.error("Failed to load schema for {}: {}", type, e.getMessage());
            }
        }

        return schemaMap;
    }

    /**
     * Create document builder factory with namespace awareness (for fallback DOM parsing).
     */
    private DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    /**
     * Parse XML content into DOM Document (fallback for document type detection).
     */
    private Document parseXmlDom(String xmlContent, List<String> errors) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            return builder.parse(new org.xml.sax.InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            errors.add("Failed to parse XML: " + e.getMessage());
            return null;
        }
    }

    /**
     * Detect document type from DOM document namespace or root element (fallback).
     */
    private DocumentType detectDocumentTypeFromDom(Document doc) {
        String namespaceUri = doc.getDocumentElement().getNamespaceURI();
        String localName = doc.getDocumentElement().getLocalName();

        // Try namespace first
        DocumentType type = DocumentType.fromNamespaceUri(namespaceUri);
        if (type != null) {
            return type;
        }

        // Fallback to root element name
        type = DocumentType.fromRootElementName(localName);
        if (type != null) {
            return type;
        }

        // Default to TaxInvoice (most common)
        log.warn("Could not detect document type from DOM, defaulting to TAX_INVOICE");
        return DocumentType.TAX_INVOICE;
    }

    /**
     * Apply Schematron business rules validation using teda library.
     */
    private void applySchematronValidation(String xmlContent, DocumentType docType,
                                           List<String> errors, List<String> warnings) {
        try {
            DocumentSchematron schematronType = docType.toDocumentSchematron();

            // Skip validation for document types with empty Schematron rules
            if (schematronType.isEmptySchematron()) {
                log.debug("Skipping Schematron validation for {} (empty ruleset)", docType);
                return;
            }

            SchematronValidationResult schematronResult =
                schematronValidator.validate(xmlContent, schematronType);

            // Convert Schematron errors to ValidationResult format
            if (schematronResult.hasErrors()) {
                for (SchematronError error : schematronResult.getErrors()) {
                    errors.add(formatSchematronMessage(error));
                }
            }

            // Convert Schematron warnings to ValidationResult format
            if (schematronResult.hasWarnings()) {
                for (SchematronError warning : schematronResult.getWarnings()) {
                    warnings.add(formatSchematronMessage(warning));
                }
            }

            log.debug("Schematron validation completed for {}: {} errors, {} warnings",
                docType, schematronResult.getErrors().size(), schematronResult.getWarnings().size());

        } catch (Exception e) {
            log.error("Schematron validation failed for {}", docType, e);
            warnings.add("Schematron validation skipped due to error: " + e.getMessage());
        }
    }

    /**
     * Format a SchematronError into a human-readable message.
     */
    private String formatSchematronMessage(SchematronError error) {
        StringBuilder sb = new StringBuilder();

        // Add rule ID if present
        if (error.getRuleId() != null && !error.getRuleId().isEmpty()) {
            sb.append("[").append(error.getRuleId()).append("] ");
        }

        // Add XPath location if present
        if (error.getLocation() != null && !error.getLocation().isEmpty()) {
            sb.append("at ").append(error.getLocation()).append(": ");
        }

        // Add message
        sb.append(error.getMessage());

        return sb.toString();
    }
}
